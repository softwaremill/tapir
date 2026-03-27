package sttp.tapir.serverless.aws.lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

import java.io.{BufferedWriter, InputStream, OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

/** [[SyncLambdaHandler]] is a direct-style entry point for handling requests sent to AWS Lambda application which exposes Tapir endpoints.
  *
  * @tparam R
  *   AWS API Gateway request type [[AwsRequestV1]] or [[AwsRequest]].
  * @param options
  *   Server options of type AwsServerOptions.
  */
abstract class SyncLambdaHandler[R: Decoder](options: AwsServerOptions[Identity]) extends RequestStreamHandler {

  protected def getAllEndpoints: List[ServerEndpoint[Any, Identity]]

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val server: AwsSyncServerInterpreter = AwsSyncServerInterpreter(options)

    val allBytes = input.readAllBytes()
    val decoded = decode[R](new String(allBytes, StandardCharsets.UTF_8))
    val response = decoded match {
      case Left(e) => AwsResponse.badRequest(s"Invalid AWS request: ${e.getMessage}")
      case Right(awsRequest) =>
        awsRequest match {
          case r: AwsRequestV1 => server.toRoute(getAllEndpoints)(r.toV2)
          case r: AwsRequest   => server.toRoute(getAllEndpoints)(r)
          case r =>
            throw new IllegalArgumentException(s"Request of type ${r.getClass.getCanonicalName} is not supported")
        }
    }

    val writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))
    try {
      writer.write(Printer.noSpaces.print(response.asJson))
    } finally {
      writer.flush()
      writer.close()
    }
  }
}

object SyncLambdaHandler {

  def apply[R: Decoder](
      endpoints: List[ServerEndpoint[Any, Identity]],
      serverOptions: AwsServerOptions[Identity]
  ): SyncLambdaHandler[R] =
    new SyncLambdaHandler[R](serverOptions) {
      override protected def getAllEndpoints: List[ServerEndpoint[Any, Identity]] = endpoints
    }

  def default[R: Decoder](endpoints: List[ServerEndpoint[Any, Identity]]): SyncLambdaHandler[R] =
    apply(endpoints, AwsSyncServerOptions.noEncoding)
}
