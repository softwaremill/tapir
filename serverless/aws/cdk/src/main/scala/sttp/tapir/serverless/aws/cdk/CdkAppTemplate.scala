package sttp.tapir.serverless.aws.cdk

import cats.effect._
import cats.syntax.all._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.cdk.core.{ParseStackTemplate, Request, StackFile}

class CdkAppTemplate[F[_]: Sync](es: Set[ServerEndpoint[Any, F]], options: AwsCdkOptions) {
  import CdkAppTemplate._

  def generate(): F[Either[Throwable, Unit]] = {
    serverEndpointsToRequests(es) match {
      case Nil => Sync[F].delay(Left(new RuntimeException("No single valid endpoint to generate stack")))
      case rs =>
        ParseStackTemplate.apply[F](options.templateFilePath, StackFile.fromAwsCdkOptions(options), rs).as(().asRight[Throwable])
    }
  }

  private def serverEndpointsToRequests(es: Set[ServerEndpoint[Any, F]]): List[Request] =
    es.flatMap(e => Request.fromEndpoint(e.endpoint)).toList
}

object CdkAppTemplate {
  private[cdk] val files = Map(
    "bin/tapir-cdk-stack.ts" -> "bin/tapir-cdk-stack.ts",
    "gitignore" -> ".gitignore",
    "cdk.json" -> "cdk.json",
    "jest.config.js" -> "jest.config.js",
    "package.json" -> "package.json",
    "readme.md" -> "readme.md",
    "tsconfig.json" -> "tsconfig.json"
  )
}
