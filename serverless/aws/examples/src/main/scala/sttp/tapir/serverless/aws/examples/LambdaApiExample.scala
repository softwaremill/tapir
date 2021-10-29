package sttp.tapir.serverless.aws.examples

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._

import java.io.{BufferedWriter, InputStream, OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets.UTF_8

object LambdaApiExample extends RequestStreamHandler {

  val helloEndpoint: ServerEndpoint[Unit, Unit, Unit, Unit, String, Any, IO] = endpoint.get
    .in("api" / "hello")
    .out(stringBody)
    .serverLogic { _ => IO.pure(s"Hello!".asRight[Unit]) }

  val options: AwsServerOptions[IO] = AwsServerOptions.default[IO].copy(encodeResponseBody = false)

  val route: Route[IO] = AwsCatsEffectServerInterpreter(options).toRoute(helloEndpoint)

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {

    /** Read input as string */
    val json = new String(input.readAllBytes(), UTF_8)

    /** Decode input to `AwsRequest` which is send by API Gateway */
    (decode[AwsRequest](json) match {
      /** Process request using interpreted route */
      case Right(awsRequest) => route(awsRequest)
      case Left(ex)          => IO.pure(AwsResponse(Nil, isBase64Encoded = false, StatusCode.BadRequest.code, Map.empty, ex.getMessage))
    }).map { awsRes =>
      /** Write response to output */
      val writer = new BufferedWriter(new OutputStreamWriter(output, UTF_8))
      writer.write(Printer.noSpaces.print(awsRes.asJson))
      writer.flush()
    }.unsafeRunSync()
  }
}
