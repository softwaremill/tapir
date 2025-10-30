package sttp.tapir.serverless.aws.ziolambda

import io.circe._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import sttp.tapir.serverless.aws.lambda.{AwsRequest, AwsRequestV1, AwsResponse, AwsServerOptions}
import sttp.tapir.ztapir._
import zio.{RIO, Task, ZIO}

import java.io.{BufferedWriter, InputStream, OutputStream, OutputStreamWriter}
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

  def process[R: Decoder](input: InputStream, output: OutputStream): RIO[Env, Unit] = {

    val server: AwsZioServerInterpreter[Env] =
      AwsZioServerInterpreter[Env](options)

    for {
      allBytes <- ZIO.attempt(input.readAllBytes())
      str = new String(allBytes, StandardCharsets.UTF_8)
      decoded = decode[R](str)
      response <- decoded match {
        case Left(e)                => ZIO.succeed(AwsResponse.badRequest(s"Invalid AWS request: ${e.getMessage}"))
        case Right(r: AwsRequestV1) => server.toRoute(getAllEndpoints)(r.toV2)
        case Right(r: AwsRequest)   => server.toRoute(getAllEndpoints)(r)
        case Right(r)               =>
          val message = s"Request of type ${r.getClass.getCanonicalName} is not supported"
          ZIO.fail(new IllegalArgumentException(message))
      }
      _ <- writerResource(response, output)
    } yield ()
  }

  private def writerResource(response: AwsResponse, output: OutputStream): Task[Unit] = {
    val acquire = ZIO.attempt(new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8)))
    val release = (writer: BufferedWriter) =>
      ZIO.attempt {
        writer.flush()
        writer.close()
      }.orDie
    val use = (writer: BufferedWriter) => ZIO.attempt(writer.write(Printer.noSpaces.print(response.asJson)))
    ZIO.acquireReleaseWith(acquire)(release)(use)
  }
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
