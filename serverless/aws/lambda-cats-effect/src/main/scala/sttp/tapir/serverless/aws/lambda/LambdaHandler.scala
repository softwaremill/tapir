package sttp.tapir.serverless.aws.lambda

import cats.effect.Sync
import cats.implicits._
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import io.circe._
import sttp.tapir.server.ServerEndpoint
import java.io.{InputStream, OutputStream}
import java.nio.charset.StandardCharsets

/** [[LambdaHandler]] is an entry point for handling requests sent to AWS Lambda application which exposes Tapir endpoints.
  *
  * @tparam F
  *   The effect type constructor used in the endpoint.
  * @tparam R
  *   AWS API Gateway request type [[AwsRequestV1]] or [[AwsRequest]]. At the moment mapping is required as there is no support for
  *   generating API Gateway V2 definitions with AWS CDK v2.
  * @param options
  *   Server options of type AwsServerOptions.
  */
abstract class LambdaHandler[F[_]: Sync, R: Decoder](options: AwsServerOptions[F]) extends RequestStreamHandler {

  protected def getAllEndpoints: List[ServerEndpoint[Any, F]]

  protected def process(input: InputStream, output: OutputStream): F[Unit] = {
    val server: AwsCatsEffectServerInterpreter[F] = AwsCatsEffectServerInterpreter(options)

    for {
      allBytes <- Sync[F].blocking(input.readAllBytes())
      decoded <- Sync[F].delay(AwsLambdaCodec.decodeRequest[R](new String(allBytes, StandardCharsets.UTF_8)))
      response <- decoded.fold(Sync[F].pure(_), server.toRoute(getAllEndpoints))
      _ <- Sync[F].blocking(AwsLambdaCodec.writeResponse(response, output))
    } yield ()
  }
}
