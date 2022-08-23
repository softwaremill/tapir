package sttp.tapir.serverless.aws.lambda

import cats.Applicative
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

//fixme add docs: F is an effect and R is request type (v1 or v2)
abstract class LambdaHandler[F[_]: Sync: Applicative, R: Decoder: Upcaster] extends RequestStreamHandler {

  protected def getAllEndpoints: List[ServerEndpoint[Any, F]]

  protected def process(input: InputStream, output: OutputStream): F[Unit] = {
    val server: AwsCatsEffectServerInterpreter[F] =
      AwsCatsEffectServerInterpreter(AwsCatsEffectServerOptions.noEncoding[F])

    Sync[F].blocking(input.readAllBytes()).flatMap { allBytes =>
      (decode[R](new String(allBytes, StandardCharsets.UTF_8)) match {
        case Right(awsRequest) => server.toRoute(getAllEndpoints)(implicitly[Upcaster[R]].toV2(awsRequest))
        case Left(_)           => Sync[F].pure(AwsResponse.badRequest())
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
