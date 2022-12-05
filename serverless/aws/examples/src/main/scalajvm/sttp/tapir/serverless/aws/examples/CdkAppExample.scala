package sttp.tapir.serverless.aws.examples

import cats.effect._
import sttp.tapir.serverless.aws.cdk.{AwsCdkInterpreter, AwsCdkOptions}

import scala.concurrent.duration._
import java.nio.file.Paths

object CdkAppExample extends IOApp.Simple {
  val jarPath: String = Paths.get("serverless/aws/examples/target/jvm-2.13/tapir-aws-examples.jar").toAbsolutePath.toString

  val awsCdkOptions: AwsCdkOptions = AwsCdkOptions(
    codeUri = jarPath,
    handler = "sttp.tapir.serverless.aws.examples.LambdaApiV1Example::handleRequest",
    apiName = "HelloApi",
    lambdaName = "CdkAppExample",
    timeout = 20.seconds, // default value
    memorySizeInMB = 2048 // default value
  )

  override def run: IO[Unit] = {
    AwsCdkInterpreter(awsCdkOptions)
      .toCdkAppTemplate[IO](LambdaApiV1Example.helloEndpoint)
      .generate()
      .rethrow
  }
}
