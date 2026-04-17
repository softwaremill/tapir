package sttp.tapir.server.finatra.cats

import cats.effect.Async
import cats.effect.std.Dispatcher
import cats.~>
import com.twitter.util.Future
import sttp.monad.MonadError
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.finatra.FinatraServerInterpreter.FutureMonadError
import sttp.tapir.server.finatra.cats.FinatraCatsServerInterpreter._
import sttp.tapir.server.finatra.{FinatraRoute, FinatraServerInterpreter, FinatraServerOptions}
import sttp.tapir.server.interceptor.{
  DecodeFailureContext,
  DecodeSuccessContext,
  EndpointHandler,
  EndpointInterceptor,
  Interceptor,
  RequestHandler,
  RequestInterceptor,
  RequestResult,
  Responder,
  SecurityFailureContext
}
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.{Endpoint, TapirFile}

import scala.util.Try
import sttp.tapir.server.finatra.cats.conversions._
import sttp.tapir.server.model.{ServerResponse, ValuedEndpointOutput}

trait FinatraCatsServerInterpreter[F[_]] {

  implicit def fa: Async[F]

  private implicit val fme: MonadError[F] = new CatsMonadError[F]

  def finatraCatsServerOptions: FinatraCatsServerOptions[F]

  def toRoute(e: ServerEndpoint[Any, F]): FinatraRoute = {
    implicit val dispatcher: Dispatcher[F] = finatraCatsServerOptions.dispatcher
    val finatraCreateFile: Array[Byte] => Future[TapirFile] = bytes => finatraCatsServerOptions.createFile(bytes).asTwitterFuture
    val finatraDeleteFile: TapirFile => Future[Unit] = file => finatraCatsServerOptions.deleteFile(file).asTwitterFuture
    val interceptors = finatraCatsServerOptions.interceptors.map(convertInterceptor(_))

    FinatraServerInterpreter(
      FinatraServerOptions(finatraCreateFile, finatraDeleteFile, interceptors)
    ).toRoute(
      e.endpoint
        .serverSecurityLogic(e.securityLogic(new CatsMonadError[F])(_).asTwitterFuture)
        .serverLogic { u => i =>
          e.logic(new CatsMonadError[F])(u)(i).asTwitterFuture
        }
    )
  }
}

object FinatraCatsServerInterpreter {
  private type RequestHandlerLogic[F[_], R, B] = EndpointInterceptor[F] => RequestHandler[F, R, B]

  def apply[F[_]](
      dispatcher: Dispatcher[F]
  )(implicit _fa: Async[F]): FinatraCatsServerInterpreter[F] = {
    new FinatraCatsServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
      override def finatraCatsServerOptions: FinatraCatsServerOptions[F] = FinatraCatsServerOptions.default(dispatcher)(_fa)
    }
  }

  def apply[F[_]](serverOptions: FinatraCatsServerOptions[F])(implicit _fa: Async[F]): FinatraCatsServerInterpreter[F] = {
    new FinatraCatsServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
      override def finatraCatsServerOptions: FinatraCatsServerOptions[F] = serverOptions
    }
  }

  private def convertEndpoint[F[_]: MonadError, G[_], R](
      original: ServerEndpoint[R, F],
      fToG: F ~> G
  ): ServerEndpoint[R, G] = {
    new ServerEndpoint[R, G] {
      override type SECURITY_INPUT = original.SECURITY_INPUT
      override type PRINCIPAL = original.PRINCIPAL
      override type INPUT = original.INPUT
      override type ERROR_OUTPUT = original.ERROR_OUTPUT
      override type OUTPUT = original.OUTPUT

      override def logic: MonadError[G] => PRINCIPAL => INPUT => G[Either[ERROR_OUTPUT, OUTPUT]] = _ =>
        p => i => fToG(original.logic(MonadError[F])(p)(i))

      override def endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R] = original.endpoint

      override def securityLogic: MonadError[G] => SECURITY_INPUT => G[Either[ERROR_OUTPUT, PRINCIPAL]] = _ =>
        si => fToG(original.securityLogic(MonadError[F])(si))
    }
  }

  private def convertHandler[F[_]: MonadError, G[_], B](
      original: EndpointHandler[F, B],
      fToG: F ~> G,
      gToF: G ~> F
  ): EndpointHandler[G, B] = {
    new EndpointHandler[G, B] {
      private implicit def bodyListenerF(implicit bodyListener: BodyListener[G, B]): BodyListener[F, B] =
        new BodyListener[F, B] {
          override def onComplete(body: B)(cb: Try[Unit] => F[Unit]): F[B] =
            gToF(bodyListener.onComplete(body)(x => fToG(cb(x))))
        }

      override def onDecodeSuccess[A, U, I](
          ctx: DecodeSuccessContext[G, A, U, I]
      )(implicit monad: MonadError[G], bodyListener: BodyListener[G, B]): G[ServerResponse[B]] = {
        fToG(
          original.onDecodeSuccess(
            ctx.copy(serverEndpoint =
              convertEndpoint[G, F, Any](ctx.serverEndpoint.asInstanceOf[ServerEndpoint[Any, G]], gToF)(monad)
                .asInstanceOf[ServerEndpoint.Full[A, U, I, ?, ?, ?, F]]
            )
          )
        )
      }

      override def onSecurityFailure[A](
          ctx: SecurityFailureContext[G, A]
      )(implicit monad: MonadError[G], bodyListener: BodyListener[G, B]): G[ServerResponse[B]] =
        fToG(
          original.onSecurityFailure(
            ctx.copy(serverEndpoint =
              convertEndpoint[G, F, Any](ctx.serverEndpoint.asInstanceOf[ServerEndpoint[Any, G]], gToF)(monad)
                .asInstanceOf[ServerEndpoint.Full[A, ?, ?, ?, ?, ?, F]]
            )
          )
        )

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[G], bodyListener: BodyListener[G, B]): G[Option[ServerResponse[B]]] =
        fToG(original.onDecodeFailure(ctx))
    }
  }

  private def convertResponder[F[_]: Async, B](original: Responder[Future, B]): Responder[F, B] =
    new Responder[F, B] {
      override def apply[O](request: ServerRequest, output: ValuedEndpointOutput[O]): F[ServerResponse[B]] =
        fromFuture(original(request, output))
    }

  private def convertInterceptor[F[_]: Async: Dispatcher: MonadError](original: Interceptor[F]): Interceptor[Future] = {
    val fToFuture = new (F ~> Future) {
      override def apply[A](f: F[A]): Future[A] = f.asTwitterFuture
    }
    val futureToF = new (Future ~> F) {
      override def apply[A](future: Future[A]): F[A] = fromFuture(future)
    }

    def convertRequestInterceptor(interceptor: RequestInterceptor[F]): RequestInterceptor[Future] = new RequestInterceptor[Future] {
      override def apply[R, B](
          responder: Responder[Future, B],
          requestHandler: RequestHandlerLogic[Future, R, B]
      ): RequestHandler[Future, R, B] = {
        def convertRequestHandler: RequestHandlerLogic[Future, R, B] => RequestHandlerLogic[F, R, B] =
          original =>
            interceptorF => {
              new RequestHandler[F, R, B] {
                override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
                    monad: MonadError[F]
                ): F[RequestResult[B]] =
                  fromFuture(
                    original(convertEndpointInterceptor(interceptorF))(
                      request,
                      endpoints.map(convertEndpoint[F, Future, R](_, fToFuture)(monad))
                    )(
                      FutureMonadError
                    )
                  )
              }
            }

        val handler = interceptor(convertResponder(responder), convertRequestHandler(requestHandler))

        new RequestHandler[Future, R, B] {
          override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, Future]])(implicit
              monad: MonadError[Future]
          ): Future[RequestResult[B]] =
            handler(request, endpoints.map(convertEndpoint[Future, F, R](_, futureToF))).asTwitterFuture
        }
      }
    }

    def convertEndpointInterceptor(interceptor: EndpointInterceptor[F]): EndpointInterceptor[Future] =
      new EndpointInterceptor[Future] {
        override def apply[B](
            responder: Responder[Future, B],
            endpointHandler: EndpointHandler[Future, B]
        ): EndpointHandler[Future, B] = {
          val handler: EndpointHandler[F, B] =
            interceptor(convertResponder(responder), convertHandler(endpointHandler, futureToF, fToFuture))

          convertHandler(handler, fToFuture, futureToF)
        }
      }

    original match {
      case interceptor: RequestInterceptor[F]  => convertRequestInterceptor(interceptor)
      case interceptor: EndpointInterceptor[F] => convertEndpointInterceptor(interceptor)
    }
  }
}
