package sttp.tapir.serverless.aws.cdk

import cats.effect._
import cats.syntax.all._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.cdk.core.{FileReader, Parser, Request, StackFile}

class CdkAppTemplate[F[_]: Sync](es: Set[ServerEndpoint[Any, F]], options: AwsCdkOptions) {
  def generate(): F[Either[Throwable, Unit]] = {
    serverEndpointsToRequests(es) match {
      case Nil => Sync[F].delay(Left(new RuntimeException("No single valid endpoint to generate stack")))
      case rs  =>

    }

//    val values = StackFile(
//      apiName = options.apiName,
//      lambdaName = options.lambdaName,
//      runtime = "lambda.Runtime.JAVA_11",
//      jarPath = options.codeUri,
//      handler = options.handler,
//      timeout = options.timeout.toSeconds,
//      memorySize = options.memorySizeInMB
//    )
//
//    implicit val reader: FileReader[F] = new FileReader[F]
//
//    val parser = new Parser[F]
//    parser.parse(options.templateFilePath, values, es) match {
//      case Right(content) =>
//        val mover = new FileMover[F]("/app-template", "cdk")
//        val files = Map(
//          "bin/tapir-cdk-stack.ts" -> "bin/tapir-cdk-stack.ts",
//          "gitignore" -> ".gitignore",
//          "cdk.json" -> "cdk.json",
//          "jest.config.js" -> "jest.config.js",
//          "package.json" -> "package.json",
//          "readme.md" -> "readme.md",
//          "tsconfig.json" -> "tsconfig.json"
//        )
//        mover.clear >> mover.move(files) >> mover.put(content, "cdk/lib/tapir-cdk-stack.ts")
//      case Left(ex) => Sync[F].delay(println(ex)).void
//    }
    Sync[F].delay(Right())
  }

  private def serverEndpointsToRequests(es: Set[ServerEndpoint[Any, F]]): List[Request] =
    es.flatMap(e => Request.fromEndpoint(e.endpoint)).toList
}
