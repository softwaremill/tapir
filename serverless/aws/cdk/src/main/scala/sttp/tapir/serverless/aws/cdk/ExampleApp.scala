package sttp.tapir.serverless.aws.cdk

import cats.effect.{IO, IOApp}
import sttp.tapir.serverless.aws.cdk.core.{FileReader, Parser, StackFile}

import java.nio.file.Paths

object ExampleApp extends IOApp.Simple {
  override def run: IO[Unit] = {
    val templateFilePath = "/app-template/lib/stack-template.ts"
    val jarPath = Paths.get("serverless/aws/cdk/target/jvm-2.13/tapir-aws-cdk.jar").toAbsolutePath.toString
    val values = new StackFile(
      "API",
      "TapirHandler",
      "lambda.Runtime.JAVA_11",
      jarPath,
      "sttp.tapir.serverless.aws.cdk.IOLambdaHandlerV1::handleRequest",
      20,
      2048
    )

    implicit val reader: FileReader[IO] = new FileReader[IO]

    val parser = new Parser[IO]
    parser.parse(templateFilePath, values, TestEndpoints.all[IO]) match {
      case Right(content) => {
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
      case Left(ex) => IO.println(ex).void
    }
  }
}
