package sttp.tapir.serverless.aws.cdk

import cats.effect.{IO, IOApp}
import cats.implicits.toTraverseOps
import java.nio.file.Paths
import scala.reflect.io.Directory

object ExampleApp extends IOApp.Simple {
  override def run: IO[Unit] = {
//    val path = "/app-template/lib/stack-template.ts"
//    val values = new StackFile(
//      "API",
//      "TapirHandler",
//      "lambda.Runtime.JAVA_11",
//      "/Users/ayeo/www/tapir/serverless/aws/cdk/target/jvm-2.13/tapir-aws-cdk.jar",
//      "sttp.tapir.serverless.aws.cdk.IOLambdaHandlerV1::handleRequest",
//      20,
//      2048
//    )
//
//    val parser = new Parser
//    val actual = parser.parse(path, values, TestEndpoints.all[IO])
//    val d = actual.unsafeRunSync()


    val mover = new FileMover("/app-template", "cdk")
    val files = Map(
      "bin/tapir-cdk-stack.ts" -> "bin/tapir-cdk-stack.ts",
      "lib/stack-template.ts" -> "lib/stack-template.ts",
      "gitignore" -> ".gitignore",
      "cdk.json" -> "cdk.json",
      "jest.config.js" -> "jest.config.js",
      "package.json" -> "package.json",
      "readme.md" -> "readme.md",
      "tsconfig.json" -> "tsconfig.json"
    )

    mover.clear >> files.toList.map { case (file, file2) => mover.move(file, file2) }.sequence.void
  }
}
