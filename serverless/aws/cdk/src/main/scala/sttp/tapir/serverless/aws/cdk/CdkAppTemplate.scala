package sttp.tapir.serverless.aws.cdk

import cats.effect._
import cats.syntax.all._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.cdk.internal.{AppTemplateFiles, ParseStackTemplate, Request, StackFile}

class CdkAppTemplate[F[_]: Sync](es: Set[ServerEndpoint[Any, F]], options: AwsCdkOptions) {
  def generate(): F[Either[Throwable, Unit]] =
    serverEndpointsToRequests(es) match {
      case Nil => Sync[F].delay(Left(new RuntimeException("No single valid endpoint to generate stack")))
      case rs  =>
        for {
          files <- new AppTemplateFiles[F](sourceDir = "/app-template", outputDir = options.outputDir).pure[F]
          _ <- files.clearOutputDir()
          _ <- files.copyStaticFiles()
          _ <- files.renderStackTemplate(
            options.templateFilePath,
            s => ParseStackTemplate.apply[F](content = s, StackFile.fromAwsCdkOptions(options), rs)
          )
        } yield ().asRight[Throwable]
    }

  private def serverEndpointsToRequests(es: Set[ServerEndpoint[Any, F]]): List[Request] =
    es.flatMap(e => Request.fromEndpoint(e.endpoint)).toList
}

object CdkAppTemplate {
  def apply[F[_]: Sync](es: Set[ServerEndpoint[Any, F]], options: AwsCdkOptions): CdkAppTemplate[F] = new CdkAppTemplate(es, options)
}
