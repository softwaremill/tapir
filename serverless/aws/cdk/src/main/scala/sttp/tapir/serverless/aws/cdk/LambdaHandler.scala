package sttp.tapir.serverless.aws.cdk

import cats.effect.{Resource, Sync}
import cats.implicits.toFlatMapOps
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import io.circe._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import sttp.tapir.serverless.aws.lambda._
import sttp.tapir.serverless.aws.cdk._

import java.io.{BufferedWriter, InputStream, OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

//fixme: replace lambda test module handler (but have to move this class upward to introduce some kind of hierarchy)
//fixme add docs: F is an effect and R is request type (v1 or v2)
abstract class LambdaHandler[F[_]: Sync, R: Decoder] extends RequestStreamHandler {

  // fixme this implicit is hacky here and input/output are not pure
  protected def process(input: InputStream, output: OutputStream)(implicit upcaster: Upcaster[R]): F[Unit] = {
    val server = AwsCatsEffectServerInterpreter(AwsCatsEffectServerOptions.noEncoding[F])
    val route: Route[F] = server.toRoute(allEndpoints[F].toList)

    (decode[R](new String(input.readAllBytes(), StandardCharsets.UTF_8)) match {
      case Right(awsRequest) => route(upcaster.toV2(awsRequest))
      case Left(_)           => Sync[F].pure(AwsResponse.badRequest())
    }).flatMap { awsRes =>
      writerResource(output).use { writer => Sync[F].delay(writer.write(Printer.noSpaces.print(awsRes.asJson))) }
    }
  }

  private val writerResource: OutputStream => Resource[F, BufferedWriter] = output =>
    Resource.make(Sync[F].delay(new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8)))) { writer =>
      Sync[F].delay {
        writer.flush()
        writer.close()
      }
    }
}
