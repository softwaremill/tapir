package sttp.tapir.serverless.aws.lambda.tests

import io.circe.syntax._
import sttp.tapir.serverless.aws.sam.AwsSamTemplateEncoders._
import sttp.tapir.serverless.aws.sam._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object LambdaSamTemplate extends App {
  implicit val samOptions: AwsSamOptions = AwsSamOptions(
    "Tests",
    source = CodeSource(
      "java11",
      "target/jvm-2.13/tapir-aws-lambda-tests.jar",
      "sttp.tapir.serverless.aws.lambda.tests.LambdaHandler::handleRequest"
    ),
    memorySize = 1024
  )
  val samTemplate = new AwsSamInterpreter().apply(allEndpoints.map(_.endpoint).toList)
  val yaml = Printer(dropNullKeys = true, preserveOrder = true, stringStyle = Printer.StringStyle.Plain).pretty(samTemplate.asJson)
  val targetFile = "/Users/kubinio/Desktop/workspace/tapir/serverless/aws/lambda-tests/template.yaml"
  Files.write(Paths.get(targetFile), yaml.getBytes(StandardCharsets.UTF_8))
}
