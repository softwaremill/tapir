package sttp.tapir.serverless.aws.lambda

import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}

private[lambda] abstract class AwsServerInterpreter[F[_]: MonadError] {

  def awsServerOptions: AwsServerOptions[F]

  def toRoute[I, E, O](se: ServerEndpoint[Any, F]): Route[F] =
    toRoute(List(se))

  def toRoute(ses: List[ServerEndpoint[Any, F]]): Route[F] = {
    implicit val bodyListener: BodyListener[F, String] = new AwsBodyListener[F]

    val interpreter = new ServerInterpreter[Any, F, String, NoStreams](
      ses,
      new AwsToResponseBody(awsServerOptions),
      awsServerOptions.interceptors,
      deleteFile = _ => ().unit // no file support
    )

    { (request: AwsRequest) =>
      val serverRequest = new AwsServerRequest(request)

      interpreter.apply(serverRequest, new AwsRequestBody[F](request)).map {
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
