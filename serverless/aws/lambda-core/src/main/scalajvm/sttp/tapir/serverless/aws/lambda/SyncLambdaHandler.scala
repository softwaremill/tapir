package sttp.tapir.serverless.aws.lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe._
import sttp.tapir.server.ServerEndpoint

import java.io.{InputStream, OutputStream}
import java.nio.charset.StandardCharsets

/** [[SyncLambdaHandler]] is a direct-style entry point for handling requests sent to AWS Lambda application which exposes Tapir endpoints.
  *
  * @tparam R
  *   AWS API Gateway request type [[AwsRequestV1]] or [[AwsRequest]].
  * @param options
  *   Server options of type AwsServerOptions.
  */
abstract class SyncLambdaHandler[R: Decoder](options: AwsServerOptions[sttp.shared.Identity]) extends RequestStreamHandler {

  protected def getAllEndpoints: List[ServerEndpoint[Any, sttp.shared.Identity]]

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val server: AwsSyncServerInterpreter = AwsSyncServerInterpreter(options)
    val json = new String(input.readAllBytes(), StandardCharsets.UTF_8)
    val response = AwsLambdaCodec.decodeRequest[R](json).fold(identity, server.toRoute(getAllEndpoints))
    AwsLambdaCodec.writeResponse(response, output)
  }
}

object SyncLambdaHandler {

  def apply[R: Decoder](
      endpoints: List[ServerEndpoint[Any, sttp.shared.Identity]],
      serverOptions: AwsServerOptions[sttp.shared.Identity]
  ): SyncLambdaHandler[R] =
    new SyncLambdaHandler[R](serverOptions) {
      override protected def getAllEndpoints: List[ServerEndpoint[Any, sttp.shared.Identity]] = endpoints
    }

  def default[R: Decoder](endpoints: List[ServerEndpoint[Any, sttp.shared.Identity]]): SyncLambdaHandler[R] =
    apply(endpoints, AwsSyncServerOptions.default)
}
