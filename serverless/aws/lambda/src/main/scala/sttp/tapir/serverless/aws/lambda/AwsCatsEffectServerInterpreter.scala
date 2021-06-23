package sttp.tapir.serverless.aws.lambda

import cats.effect.Sync
import sttp.model.StatusCode
import sttp.monad.syntax._
import sttp.tapir.Endpoint
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}

import scala.reflect.ClassTag

trait AwsCatsEffectServerInterpreter {
  def toRoute[I, E, O, F[_]](e: Endpoint[I, E, O, Any])(
      logic: I => F[Either[E, O]]
  )(implicit serverOptions: AwsServerOptions[F], sync: Sync[F]): Route[F] = toRoute(e.serverLogic(logic))

  def toRoute[I, E, O, F[_]](se: ServerEndpoint[I, E, O, Any, F])(implicit serverOptions: AwsServerOptions[F], sync: Sync[F]): Route[F] =
    toRoute(List(se))

  def toRouteRecoverErrors[I, E, O, F[_]](e: Endpoint[I, E, O, Any])(
      logic: I => F[O]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E], serverOptions: AwsServerOptions[F], sync: Sync[F]): Route[F] =
    toRoute(e.serverLogicRecoverErrors(logic))

  def toRoute[F[_]](ses: List[ServerEndpoint[_, _, _, Any, F]])(implicit serverOptions: AwsServerOptions[F], sync: Sync[F]): Route[F] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F]
    implicit val bodyListener: BodyListener[F, String] = new AwsBodyListener[F]

    { request: AwsRequest =>
      val serverRequest = new AwsServerRequest(request)
      val interpreter = new ServerInterpreter[Any, F, String, NoStreams](
        new AwsRequestBody[F](request),
        new AwsToResponseBody,
        serverOptions.interceptors,
        deleteFile = _ => ().unit // no file support
      )

      interpreter.apply(serverRequest, ses).map {
        case None => AwsResponse(Nil, isBase64Encoded = serverOptions.encodeResponseBody, StatusCode.NotFound.code, Map.empty, "")
        case Some(res) =>
          val cookies = res.cookies.collect { case Right(cookie) => cookie.value }.toList
          val headers = res.headers.groupBy(_.name).map { case (n, v) => n -> v.map(_.value).mkString(",") }
          AwsResponse(cookies, isBase64Encoded = serverOptions.encodeResponseBody, res.code.code, headers, res.body.getOrElse(""))
      }
    }
  }
}

object AwsCatsEffectServerInterpreter extends AwsCatsEffectServerInterpreter
