package sttp.tapir.serverless.aws.cdk.tests

import cats.effect._
import cats.syntax.all._
import sttp.tapir.serverless.aws.cdk.{AwsCdkInterpreter, AwsCdkOptions}

import java.nio.file.Paths

object AwsCdkAppTemplate extends IOApp.Simple {

  val awsCdkOptions: AwsCdkOptions = AwsCdkOptions(
    codeUri = Paths.get("serverless/aws/cdk-tests/target/jvm-2.13/tapir-aws-cdk-tests.jar").toAbsolutePath.toString,
    handler = "sttp.tapir.serverless.aws.cdk.tests.CdkTestLambdaHandler::handleRequest",
    memorySizeInMB = 1024,
    outputDir = "aws-cdk-tests"
  )

  override def run: IO[Unit] = {
    AwsCdkInterpreter(awsCdkOptions)
      .toCdkAppTemplate[IO](allEndpoints)
      .generate()
      .rethrow
  }
}
