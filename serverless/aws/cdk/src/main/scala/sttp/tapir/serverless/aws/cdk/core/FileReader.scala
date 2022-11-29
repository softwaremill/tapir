package sttp.tapir.serverless.aws.cdk.core

import cats.effect._
import scala.io.Source

class FileReader[F[_]: Sync] {

  private val file: String => kernel.Resource[F, Source] = o =>
    Resource.fromAutoCloseable[F, Source](Sync[F].blocking(Source.fromInputStream(getClass.getResourceAsStream(o))))

  def getContent(path: String): F[String] = {
    file(path).use(content => Sync[F].delay(content.getLines().mkString("\n"))) // fixme
  }
}
