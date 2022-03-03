package perfTests

import cats.effect._
import cats.syntax.all._
import cats.effect.unsafe.implicits.global
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.ExecutionContext

object Http4sTapir {
  def route(n: Int) = Http4sServerInterpreter[IO]().toRoutes(
      endpoint
        .get
        .in("path" + n.toString)
        .in(path[Int]("id"))
        .errorOut(stringBody)
        .out(stringBody)
        .serverLogic(id =>
          IO(((id + n).toString).asRight[String])
        )
    )

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  def runServer(nRoutes: Int): IO[ExitCode] = {
    val apis = Router(
      Range(0, nRoutes, 1)
        .map((n) => "/" -> route(n)):_*
    ).orNotFound

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(apis)
      .resource
      .use(_ => {
             Common.blockServer()
             println("Server terminated")
             IO.pure(ExitCode.Success)
           })
  }
}

object Http4sTapirMultiServer extends App {
  Http4sVanilla.runServer(128).unsafeRunSync()
}

object Http4sTapirServer extends App {
  Http4sVanilla.runServer(1).unsafeRunSync()
}
