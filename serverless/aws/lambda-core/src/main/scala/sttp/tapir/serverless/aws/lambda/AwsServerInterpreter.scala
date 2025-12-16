package sttp.tapir.serverless.aws.lambda

import sttp.model.{HeaderNames, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}

private[aws] abstract class AwsServerInterpreter[F[_]: MonadError] {

  def awsServerOptions: AwsServerOptions[F]

  def toRoute(se: ServerEndpoint[Any, F]): Route[F] =
    toRoute(List(se))

  def toRoute(ses: List[ServerEndpoint[Any, F]]): Route[F] = {
    implicit val bodyListener: BodyListener[F, LambdaResponseBody] = new AwsBodyListener[F]

    val interpreter = new ServerInterpreter[Any, F, LambdaResponseBody, NoStreams](
      FilterServerEndpoints(ses),
      new AwsRequestBody[F](),
      new AwsToResponseBody(awsServerOptions),
      awsServerOptions.interceptors,
      deleteFile = _ => ().unit // no file support
    )

    return { (request: AwsRequest) =>
      val serverRequest = AwsServerRequest(request)

      interpreter.apply(serverRequest).map {
        case RequestResult.Failure(_) =>
          AwsResponse(isBase64Encoded = awsServerOptions.encodeResponseBody, StatusCode.NotFound.code, Map.empty, "")
        case RequestResult.Response(res, _) =>
          val baseHeaders = res.headers.groupBy(_.name).map { case (n, v) => n -> v.map(_.value).mkString(",") }
          val allHeaders = res.body match {
            case Some((_, Some(contentLength))) if res.contentLength.isEmpty =>
              baseHeaders + (HeaderNames.ContentLength -> contentLength.toString)
            case _ => baseHeaders
          }
          AwsResponse(
            isBase64Encoded = awsServerOptions.encodeResponseBody,
            res.code.code,
            allHeaders,
            res.body.map(_._1).getOrElse("")
          )
      }
    }
  }
}
