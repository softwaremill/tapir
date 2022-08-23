package sttp.tapir.serverless.aws.cdk

import cats.effect.{IO, IOApp}
import sttp.tapir.serverless.aws.cdk.core.{Parser, StackFile}

object ExampleApp extends IOApp.Simple {
  override def run: IO[Unit] = {
    val path = "/app-template/lib/stack-template.ts"
    val values = new StackFile(
      "API",
      "TapirHandler",
      "lambda.Runtime.JAVA_11",
      "/Users/ayeo/www/tapir/serverless/aws/cdk/target/jvm-2.13/tapir-aws-cdk.jar", //fixme
      "sttp.tapir.serverless.aws.cdk.IOLambdaHandlerV1::handleRequest",
      20,
      2048
    )

    val parser = new Parser
    val content: IO[String] = parser.parse(path, values, TestEndpoints.all[IO])

    val mover = new FileMover("/app-template", "cdk")
    val files = Map(
      "bin/tapir-cdk-stack.ts" -> "bin/tapir-cdk-stack.ts",
      "gitignore" -> ".gitignore",
      "cdk.json" -> "cdk.json",
      "jest.config.js" -> "jest.config.js",
      "package.json" -> "package.json",
      "readme.md" -> "readme.md",
      "tsconfig.json" -> "tsconfig.json"
    )

    mover.clear >> mover.move(files) >> mover.put(content, "cdk/lib/tapir-cdk-stack.ts") >> IO.unit
  }
}
