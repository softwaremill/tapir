package tapir.server.akkahttp

import java.io.File

import akka.http.scaladsl.server.RequestContext
import tapir.Defaults

import scala.concurrent.Future

case class AkkaHttpServerOptions(createFile: RequestContext => Future[File])

object AkkaHttpServerOptions {
  val DefaultCreateFile: RequestContext => Future[File] = { _ =>
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Defaults.createTempFile())
  }

  implicit val Default: AkkaHttpServerOptions = AkkaHttpServerOptions(DefaultCreateFile)
}
