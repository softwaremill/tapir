package perfTests

import cats.effect._
import cats.effect.unsafe.implicits.global
import org.http4s._
import org.http4s.dsl._
import org.http4s.implicits._
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder

object Http4sVanilla {
  def route(n: Int): HttpRoutes[IO] = {
    val dsl = Http4sDsl[IO]
    import dsl._

    HttpRoutes.of[IO] {
      case GET -> Root / IntVar(id) =>
        Ok((id + n).toString)
    }
  }

  def runServer(nRoutes: Int): IO[ExitCode] = {
    val apis = Router(
      Range(0, nRoutes, 1)
        .map((n) => ("/path" + n.toString) -> route(n)):_*
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

object Http4sVanillaMultiServer extends App {
  Http4sVanilla.runServer(128).unsafeRunSync()
}

object Http4sVanillaServer extends App {
  Http4sVanilla.runServer(1).unsafeRunSync()
}
