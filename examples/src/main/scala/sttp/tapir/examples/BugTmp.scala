package sttp.tapir.examples

import cats.effect._
import cats.syntax.all._
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import sttp.model.Part
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.generic.auto._
import java.io.File
import scala.concurrent.ExecutionContext


object BugTmp extends App {

  val fileEndpoint: Endpoint[FileRequest, Unit, String, Any] = endpoint.post
    .in("upload")
    .in(multipartBody[FileRequest])
    .out(stringBody)

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  val fileRoutes: HttpRoutes[IO] = Http4sServerInterpreter.toRoutes(fileEndpoint)(_ => IO(s"Hello file".asRight[Unit]))


  BlazeServerBuilder[IO](ec)
    .bindHttp(8080, "localhost")
    .withHttpApp(Router("/" -> fileRoutes).orNotFound)
    .serve
    .compile
    .drain
    .unsafeRunSync()


  case class FileRequest(file: Part[File], name: String)
}
