package sttp.tapir.serverless.aws.examples

import cats.effect.IO
import cats.syntax.all._
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.examples.HelloHandler.helloEndpoint
import sttp.tapir.serverless.aws.lambda._

import java.io.{BufferedWriter, InputStream, OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

class HelloHandler extends RequestStreamHandler {

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    implicit val options: AwsServerOptions[IO] = AwsServerOptions.customInterceptors[IO]()

    val route: Route[IO] = AwsServerInterpreter.toRoute(allTestE)

    val json = new String(input.readAllBytes(), StandardCharsets.UTF_8)

    val awsRequest = decode[AwsRequest](json).getOrElse(throw new Exception)

    val result: IO[Unit] = route(awsRequest)
      .map { awsRes =>
        val writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))
        writer.write(Printer.noSpaces.print(awsRes.asJson))
        writer.flush()
      }

    result.unsafeRunSync()
  }
}

object HelloHandler {
  import io.circe.generic.auto._
  import sttp.tapir.generic.auto._
  import sttp.tapir.json.circe.jsonBody

  case class HelloResponse(msg: String)

  val helloEndpoint: ServerEndpoint[Unit, Unit, HelloResponse, Any, IO] = endpoint.get
    .in("hello")
    .out(jsonBody[HelloResponse])
    .serverLogic(_ => IO.pure(HelloResponse("Hello!").asRight[Unit]))
}
