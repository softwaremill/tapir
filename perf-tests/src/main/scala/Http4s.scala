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
  val router = (nRoutes: Int) =>
    Router(
      (0 to nRoutes).map((n: Int) =>
        ("/path" + n.toString) -> {
          val dsl = Http4sDsl[IO]
          import dsl._
          HttpRoutes.of[IO] { case GET -> Root / IntVar(id) =>
            Ok((id + n).toString)
          }
        }
      ): _*
    )
}

object Tapir {
  val router = (nRoutes: Int) =>
    Router("/" -> {
      Http4sServerInterpreter[IO]().toRoutes(
        (0 to nRoutes)
          .map((n: Int) => perfTests.Common.genTapirEndpoint(n).serverLogic(id => IO(((id + n).toString).asRight[String])))
          .toList
      )
    })
}

object Http4s {
  def runServer(router: HttpRoutes[IO]): IO[ExitCode] = {
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(router.orNotFound)
      .resource
      .use(_ => { perfTests.Common.blockServer(); IO.pure(ExitCode.Success) })
  }
}

object TapirServer extends App { Http4s.runServer(Tapir.router(1)).unsafeRunSync() }
object TapirMultiServer extends App { Http4s.runServer(Tapir.router(128)).unsafeRunSync() }
object VanillaServer extends App { Http4s.runServer(Vanilla.router(1)).unsafeRunSync() }
object VanillaMultiServer extends App { Http4s.runServer(Vanilla.router(128)).unsafeRunSync() }
