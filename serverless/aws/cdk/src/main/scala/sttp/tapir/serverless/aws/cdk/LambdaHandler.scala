package sttp.tapir.serverless.aws.cdk

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import sttp.model.StatusCode
import sttp.tapir.serverless.aws.lambda._

import java.io.{BufferedWriter, InputStream, OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

class LambdaHandler extends RequestStreamHandler {

  //fixme: lot of duplication with sttp.tapir.serverless.aws.lambda.tests.LambdaHandler::handleRequest
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {

    val options: AwsServerOptions[IO] = AwsCatsEffectServerOptions.default[IO].copy(encodeResponseBody = false)
    val bytes = input.readAllBytes()

    context.getLogger.log("Input:")
    context.getLogger.log(bytes)

    val route: Route[IO] = AwsCatsEffectServerInterpreter(options).toRoute(allEndpoints.toList)
    val json = new String(bytes, StandardCharsets.UTF_8)

    (decode[AwsRequestV1](json).map(_.toV2) match {
      case Right(awsRequest) => route(awsRequest)
      case Left(_) => IO.pure(AwsResponse(isBase64Encoded = false, StatusCode.BadRequest.code, Map.empty, ""))
    }).map { awsRes =>
      val writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))
      writer.write(Printer.noSpaces.print(awsRes.asJson))
      writer.flush()
      writer.close() //fixme: use bracket?
    }.unsafeRunSync()
  }
}
