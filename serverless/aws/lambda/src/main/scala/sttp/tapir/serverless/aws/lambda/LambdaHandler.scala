package sttp.tapir.serverless.aws.lambda

import cats.effect.{Resource, Sync}
import cats.implicits._
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import io.circe._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import sttp.tapir.server.ServerEndpoint
import java.io.{BufferedWriter, InputStream, OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

/**
 * [[LambdaHandler]] is an entry point for handling requests sent to AWS Lambda application which exposes Tapir endpoints.
 *
 * @tparam F
 *   The effect type constructor used in by the endpoint.
 * @tparam R
 *   AWS API Gateway request type [[AwsRequestV1]] or [[AwsRequest]].
 *   At the moment mapping is required as there is no support for generating API Gateway V2 definitions with AWS CDK v2.
 */
abstract class LambdaHandler[F[_]: Sync, R: Decoder: Mapper] extends RequestStreamHandler {

  protected def getAllEndpoints: List[ServerEndpoint[Any, F]]

  protected def process(input: InputStream, output: OutputStream): F[Unit] = {
    val server: AwsCatsEffectServerInterpreter[F] =
      AwsCatsEffectServerInterpreter(AwsCatsEffectServerOptions.noEncoding[F])

    Sync[F].blocking(input.readAllBytes()).flatMap { allBytes =>
      (decode[R](new String(allBytes, StandardCharsets.UTF_8)) match {
        case Right(awsRequest) => server.toRoute(getAllEndpoints)(implicitly[Mapper[R]].toV2(awsRequest))
        case Left(e)           => Sync[F].pure(AwsResponse.badRequest(s"Invalid AWS request: ${e.getMessage}"))
      }).flatMap { awsRes =>
        writerResource(Sync[F].delay(output)).use { writer =>
          Sync[F].blocking(writer.write(Printer.noSpaces.print(awsRes.asJson)))
        }
      }
    }
  }

  private val writerResource: F[OutputStream] => Resource[F, BufferedWriter] = output => {
    Resource.make {
      output.map(i => new BufferedWriter(new OutputStreamWriter(i, StandardCharsets.UTF_8)))
    } { writer =>
      Sync[F].delay {
        writer.flush()
        writer.close()
      }
    }
  }
}
