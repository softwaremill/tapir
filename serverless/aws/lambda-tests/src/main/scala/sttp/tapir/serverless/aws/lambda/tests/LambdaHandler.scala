package sttp.tapir.serverless.aws.lambda.tests

import cats.effect.IO
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import sttp.model.StatusCode
import sttp.tapir.serverless.aws.lambda._

import java.io.{BufferedWriter, InputStream, OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

object LambdaHandler extends RequestStreamHandler {
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {

    implicit val options: AwsServerOptions[IO] = AwsServerOptions.customInterceptors(encodeResponseBody = false)

    allEndpoints.foreach(e => println(e.endpoint.showDetail))

    val route: Route[IO] = AwsServerInterpreter.toRoute(allEndpoints.toList)
    val json = new String(input.readAllBytes(), StandardCharsets.UTF_8)

    (decode[AwsRequest](json) match {
      case Right(awsRequest) => route(awsRequest)
      case Left(_)           => IO.pure(AwsResponse(Nil, isBase64Encoded = false, StatusCode.BadRequest.code, Map.empty, ""))
    }).map { awsRes =>
      val writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))
      writer.write(Printer.noSpaces.print(awsRes.asJson))
      writer.flush()
    }.unsafeRunSync()
  }
}
