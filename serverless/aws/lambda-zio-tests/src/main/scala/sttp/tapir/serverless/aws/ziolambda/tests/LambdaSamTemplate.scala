package sttp.tapir.serverless.aws.ziolambda.tests

import sttp.tapir.serverless.aws.sam.{AwsSamInterpreter, AwsSamOptions, CodeSource}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

object LambdaSamTemplate extends App {

  val jarPath = Paths.get("serverless/aws/lambda-zio-tests/target/jvm-2.13/tapir-aws-lambda-zio-tests.jar").toAbsolutePath.toString

  val samOptions: AwsSamOptions = AwsSamOptions(
    "Tests",
    source = CodeSource(
      "java11",
      jarPath,
      "sttp.tapir.serverless.aws.ziolambda.tests.ZioLambdaHandlerImpl::handleRequest"
    ),
    memorySize = 1024
  )
  val yaml = AwsSamInterpreter(samOptions).toSamTemplate(allEndpoints.map(_.endpoint).toList).toYaml
  Files.write(Paths.get("aws-lambda-zio-template.yaml"), yaml.getBytes(UTF_8))
}
