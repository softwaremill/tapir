package tapir.server.akkahttp

import akka.http.scaladsl.server.{Directive, Route}
import tapir.Endpoint
import tapir.server.ServerEndpoint
import tapir.typelevel.{ParamsToTuple, ReplaceFirstInTuple}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait TapirAkkaHttpServer {
  implicit class RichAkkaHttpEndpoint[I, E, O](e: Endpoint[I, E, O, AkkaStream]) {
    def toDirective[T](implicit paramsToTuple: ParamsToTuple.Aux[I, T], akkaHttpOptions: AkkaHttpServerOptions): Directive[T] =
      new EndpointToAkkaServer(akkaHttpOptions).toDirective(e)

    def toRoute(logic: I => Future[Either[E, O]])(implicit serverOptions: AkkaHttpServerOptions): Route =
      new EndpointToAkkaServer(serverOptions).toRoute(e)(logic)

    def toRouteRecoverErrors(logic: I => Future[O])(implicit serverOptions: AkkaHttpServerOptions,
                                                    eIsThrowable: E <:< Throwable,
                                                    eClassTag: ClassTag[E]): Route = {

      def reifyFailedFuture(f: Future[O]): Future[Either[E, O]] = {
        import ExecutionContext.Implicits.global
        f.map(Right(_): Either[E, O]).recover {
          case e: Throwable if implicitly[ClassTag[E]].runtimeClass.isInstance(e) => Left(e.asInstanceOf[E]): Either[E, O]
        }
      }

      new EndpointToAkkaServer(serverOptions)
        .toRoute(e)(logic.andThen(reifyFailedFuture))
    }
  }

  implicit class RichAkkaHttpServerEndpoint[I, E, O](se: ServerEndpoint[I, E, O, AkkaStream, Future]) {
    def toDirective[T](implicit paramsToTuple: ParamsToTuple.Aux[I, T], akkaHttpOptions: AkkaHttpServerOptions): Directive[T] =
      new EndpointToAkkaServer(akkaHttpOptions).toDirective(se.endpoint)

    def toRoute(implicit serverOptions: AkkaHttpServerOptions): Route =
      new EndpointToAkkaServer(serverOptions).toRoute(se.endpoint)(se.logic)
  }

  implicit class RichToFutureFunction[T, U](a: T => Future[U])(implicit ec: ExecutionContext) {
    def andThenFirst[U_TUPLE, T_TUPLE, O](l: U_TUPLE => Future[O])(
        implicit replaceFirst: ReplaceFirstInTuple[T, U, T_TUPLE, U_TUPLE]): T_TUPLE => Future[O] = { tTuple =>
      val t = replaceFirst.first(tTuple)
      a(t).flatMap { u =>
        val uTuple = replaceFirst.replace(tTuple, u)
        l(uTuple)
      }
    }
  }

  implicit class RichToFutureOfEitherFunction[T, U, E](a: T => Future[Either[E, U]])(implicit ec: ExecutionContext) {
    def andThenFirstE[U_TUPLE, T_TUPLE, O](l: U_TUPLE => Future[Either[E, O]])(
        implicit replaceFirst: ReplaceFirstInTuple[T, U, T_TUPLE, U_TUPLE]): T_TUPLE => Future[Either[E, O]] = { tTuple =>
      val t = replaceFirst.first(tTuple)
      a(t).flatMap {
        case Left(e) => Future.successful(Left(e))
        case Right(u) =>
          val uTuple = replaceFirst.replace(tTuple, u)
          l(uTuple)
      }
    }
  }
}
