package sttp.tapir.serverless.aws.ziolambda

import io.circe._
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import sttp.tapir.serverless.aws.lambda.{AwsLambdaCodec, AwsRequest, AwsResponse, AwsServerOptions}
import sttp.tapir.ztapir._
import zio.{RIO, ZIO}

import java.io.{InputStream, OutputStream}
import java.nio.charset.StandardCharsets

/** [[ZioLambdaHandler]] is an entry point for handling requests sent to AWS Lambda application which exposes Tapir endpoints.
  *
  * @tparam Env
  *   The Environment type of the handler .
  * @param options
  *   Server options of type AwsServerOptions.
  */
abstract class ZioLambdaHandler[Env: RIOMonadError](options: AwsServerOptions[RIO[Env, *]]) {

  protected def getAllEndpoints: List[ZServerEndpoint[Env, Any]]

  private lazy val route: AwsRequest => RIO[Env, AwsResponse] = AwsZioServerInterpreter[Env](options).toRoute(getAllEndpoints)

  def process[R: Decoder](input: InputStream, output: OutputStream): RIO[Env, Unit] =
    for {
      allBytes <- ZIO.attempt(input.readAllBytes())
      decoded <- ZIO.attempt(AwsLambdaCodec.decodeRequest[R](new String(allBytes, StandardCharsets.UTF_8)))
      response <- decoded.fold(ZIO.succeed(_), route)
      _ <- ZIO.attempt(AwsLambdaCodec.writeResponse(response, output))
    } yield ()
}

object ZioLambdaHandler {

  def apply[Env: RIOMonadError](endpoints: List[ZServerEndpoint[Env, Any]], options: AwsServerOptions[RIO[Env, *]]): ZioLambdaHandler[Env] =
    new ZioLambdaHandler[Env](options) {
      override protected def getAllEndpoints: List[ZServerEndpoint[Env, Any]] = endpoints
    }

  def default[Env: RIOMonadError](endpoints: List[ZServerEndpoint[Env, Any]]): ZioLambdaHandler[Env] = {
    val serverLogger =
      ZioHttpServerOptions.defaultServerLog[Env]

    val options =
      AwsZioServerOptions.noEncoding[Env](
        AwsZioServerOptions
          .customiseInterceptors[Env]
          .serverLog(serverLogger)
          .options
      )

    ZioLambdaHandler(endpoints, options)
  }
}
