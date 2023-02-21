package sttp.tapir.serverless.aws.lambda.tests

import sttp.tapir.serverless.aws.sam._

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

object LambdaSamTemplate extends App {

  val jarPath = Paths.get("serverless/aws/lambda-tests/target/jvm-2.13/tapir-aws-lambda-tests.jar").toAbsolutePath.toString

  val samOptions: AwsSamOptions = AwsSamOptions(
    "Tests",
    source = CodeSource(
      "java11",
      jarPath,
      "sttp.tapir.serverless.aws.lambda.tests.IOLambdaHandlerV2::handleRequest"
    ),
    memorySize = 1024
  )
  val yaml = AwsSamInterpreter(samOptions).toSamTemplate(allEndpoints.map(_.endpoint).toList).toYaml
  Files.write(Paths.get("template.yaml"), yaml.getBytes(UTF_8))
}
