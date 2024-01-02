package sttp.tapir.perf.http4s

import cats.effect._
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl._
import org.http4s.implicits._
import org.http4s.server.Router
import sttp.tapir.perf.apis._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.monad.MonadError

object Vanilla {
  val router: Int => HttpRoutes[IO] = (nRoutes: Int) =>
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

object Tapir extends Endpoints {

  implicit val mErr: MonadError[IO] = new CatsMonadError[IO]

  val serverEndpointGens = replyingWithDummyStr[IO](allEndpoints)

  val router: Int => HttpRoutes[IO] = (nRoutes: Int) =>
    Router("/" -> {
      Http4sServerInterpreter[IO]().toRoutes(
        genServerEndpoints(serverEndpointGens)(nRoutes).toList
      )
    })
}

object server {
  def runServer(router: HttpRoutes[IO]): IO[ServerRunner.KillSwitch] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(router.orNotFound)
      .resource
      .allocated
      .map(_._2)
      .map(_.flatTap { _ =>
        IO.println("Http4s server closed.")
      })
}

object TapirServer extends ServerRunner { override def start = server.runServer(Tapir.router(1)) }
object TapirMultiServer extends ServerRunner { override def start = server.runServer(Tapir.router(128)) }
object VanillaServer extends ServerRunner { override def start = server.runServer(Vanilla.router(1)) }
object VanillaMultiServer extends ServerRunner { override def start = server.runServer(Vanilla.router(128)) }
