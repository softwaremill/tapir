package sttp.tapir.serverless.aws.lambda

import cats.effect.Sync
import sttp.model.StatusCode
import sttp.monad.syntax._
import sttp.tapir.Endpoint
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}

import scala.reflect.ClassTag

trait AwsCatsEffectServerInterpreter[F[_]] {

  implicit def fa: Sync[F]

  def awsServerOptions: AwsServerOptions[F] = AwsServerOptions.default[F]

  def toRoute[I, E, O](e: Endpoint[I, E, O, Any])(
      logic: I => F[Either[E, O]]
  )(implicit sync: Sync[F]): Route[F] = toRoute(e.serverLogic(logic))

  def toRoute[I, E, O](se: ServerEndpoint[I, E, O, Any, F]): Route[F] =
    toRoute(List(se))

  def toRouteRecoverErrors[I, E, O](e: Endpoint[I, E, O, Any])(
      logic: I => F[O]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Route[F] =
    toRoute(e.serverLogicRecoverErrors(logic))

  def toRoute(ses: List[ServerEndpoint[_, _, _, Any, F]]): Route[F] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F]
    implicit val bodyListener: BodyListener[F, String] = new AwsBodyListener[F]

    { request: AwsRequest =>
      val serverRequest = new AwsServerRequest(request)
      val interpreter = new ServerInterpreter[Any, F, String, NoStreams](
        new AwsRequestBody[F](request),
        new AwsToResponseBody(awsServerOptions),
        awsServerOptions.interceptors,
        deleteFile = _ => ().unit // no file support
      )

      interpreter.apply(serverRequest, ses).map {
        case RequestResult.Failure(_) =>
          AwsResponse(Nil, isBase64Encoded = awsServerOptions.encodeResponseBody, StatusCode.NotFound.code, Map.empty, "")
        case RequestResult.Response(res) =>
          val cookies = res.cookies.collect { case Right(cookie) => cookie.value }.toList
          val headers = res.headers.groupBy(_.name).map { case (n, v) => n -> v.map(_.value).mkString(",") }
          AwsResponse(cookies, isBase64Encoded = awsServerOptions.encodeResponseBody, res.code.code, headers, res.body.getOrElse(""))
      }
    }
  }
}

object AwsCatsEffectServerInterpreter {

  def apply[F[_]](serverOptions: AwsServerOptions[F])(implicit _fa: Sync[F]): AwsCatsEffectServerInterpreter[F] = {
    new AwsCatsEffectServerInterpreter[F] {
      override implicit def fa: Sync[F] = _fa
      override def awsServerOptions: AwsServerOptions[F] = serverOptions
    }
  }

  def apply[F[_]]()(implicit _fa: Sync[F]): AwsCatsEffectServerInterpreter[F] = {
    new AwsCatsEffectServerInterpreter[F] {
      override implicit def fa: Sync[F] = _fa
      override def awsServerOptions: AwsServerOptions[F] = AwsServerOptions.default[F](fa)
    }
  }
}
