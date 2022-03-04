package perfTests.Http4s

import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.server.http4s.Http4sServerInterpreter

object Vanilla {
  def route(n: Int) = ("/path" + n.toString) -> {
    val dsl = Http4sDsl[IO]
    import dsl._
    HttpRoutes.of[IO] {
      case GET -> Root / IntVar(id) =>
        Ok((id + n).toString)
    }
  }
}

object Tapir {
  def route(n: Int) = "/" -> {
    Http4sServerInterpreter[IO]().toRoutes(
      perfTests.Common.genTapirEndpoint(n).serverLogic(
        id => IO(((id + n).toString).asRight[String])
      )
    )
  }
}

object Http4s {
  def runServer(nRoutes: Int, route: Int => (String, HttpRoutes[IO])): IO[ExitCode] = {
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router((0 to nRoutes).map(route):_*).orNotFound)
      .resource
      .use(_ => {
             perfTests.Common.blockServer()
             IO.pure(ExitCode.Success)
           })
  }
}

object TapirMultiServer   extends App {Http4s.runServer( 128,  Tapir.route  ).unsafeRunSync()}
object TapirServer        extends App {Http4s.runServer( 1,    Tapir.route  ).unsafeRunSync()}
object VanillaMultiServer extends App {Http4s.runServer( 128,  Vanilla.route).unsafeRunSync()}
object VanillaServer      extends App {Http4s.runServer( 1,    Vanilla.route).unsafeRunSync()}
