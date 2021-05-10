package sttp.tapir.serverless.aws.lambda

import io.circe.syntax._
import sttp.tapir.Endpoint
import sttp.tapir.serverless.aws.sam._
import sttp.tapir.tests._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object AwsLambdaHttpTestSamTemplate extends App {
  val eps: Set[Endpoint[_, _, _, _]] = allTestEndpoints
  implicit val samOptions: AwsSamOptions = AwsSamOptions(
    "hello",
    source = CodeSource("java11", "target/jvm-2.13/examples.jar", "sttp.tapir.serverless.aws.examples.HelloHandler::handleRequest")
  )
  val samTemplate = new AwsSamInterpreter().apply(eps.toList)

  val yaml = Printer(dropNullKeys = true, preserveOrder = true, stringStyle = Printer.StringStyle.Plain)
    .pretty(samTemplate.asJson(AwsSamTemplateEncoders.encoderSamTemplate))

  val targetFile = "/Users/kubinio/Desktop/workspace/tapir/serverless/aws/examples/template.yaml"

  Files.write(Paths.get(targetFile), yaml.getBytes(StandardCharsets.UTF_8))
}
