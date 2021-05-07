package sttp.tapir.serverless.aws.examples

import cats.effect.IO
import cats.syntax.all._
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe.Printer
import io.circe.generic.auto._
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

    val route: Route[IO] = AwsServerInterpreter.toRoute(helloEndpoint)

    val ctx = LambdaRuntimeContext(input, output, context)

    val result: IO[Unit] = route(ctx)
      .map { awsRes =>
        val writer = new BufferedWriter(new OutputStreamWriter(ctx.output, StandardCharsets.UTF_8))
        writer.write(Printer.noSpaces.print(awsRes.asJson))
        writer.flush()
      }

    result.unsafeRunSync()
  }
}

object HelloHandler {
  val helloEndpoint: ServerEndpoint[Unit, Unit, String, Any, IO] = endpoint.get
    .in("hello")
    .out(stringBody)
    .serverLogic(_ => IO.pure("Hello!".asRight[Unit]))
}
