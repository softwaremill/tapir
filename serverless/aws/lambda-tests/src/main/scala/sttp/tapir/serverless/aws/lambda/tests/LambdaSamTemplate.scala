package sttp.tapir.serverless.aws.lambda.tests

import sttp.tapir.serverless.aws.sam._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object LambdaSamTemplate extends App {
  implicit val samOptions: AwsSamOptions = AwsSamOptions(
    "Tests",
    source = CodeSource(
      "java11",
      "/Users/kubinio/Desktop/workspace/tapir/serverless/aws/lambda-tests/target/jvm-2.13/tapir-aws-lambda-tests.jar",
      "sttp.tapir.serverless.aws.lambda.tests.LambdaHandler::handleRequest"
    ),
    memorySize = 1024
  )
  val yaml = AwsSamInterpreter.toSamTemplate(allEndpoints.map(_.endpoint).toList).toYaml
  val targetFile = "/Users/kubinio/Desktop/workspace/tapir/template.yaml"
  Files.write(Paths.get(targetFile), yaml.getBytes(StandardCharsets.UTF_8))
}
