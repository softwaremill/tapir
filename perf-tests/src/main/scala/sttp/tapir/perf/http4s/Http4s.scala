package sttp.tapir.perf.http4s

import cats.effect._
import fs2.io.file.{Files, Path => Fs2Path}
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl._
import org.http4s.implicits._
import org.http4s.server.Router
import sttp.monad.MonadError
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis._
import sttp.tapir.server.http4s.Http4sServerInterpreter

object Vanilla {
  val router: Int => HttpRoutes[IO] = (nRoutes: Int) =>
    Router(
      (0 to nRoutes).map((n: Int) =>
        ("/") -> {
          val dsl = Http4sDsl[IO]
          import dsl._
          HttpRoutes.of[IO] {
            case GET -> Root / s"path$n" / IntVar(id) =>
              Ok((id + n).toString)
            case req @ POST -> Root / s"path$n" / IntVar(id) =>
              req.as[String].flatMap { _ =>
                Ok((id + n).toString)
              }
            case req @ POST -> Root / s"pathBytes$n" / IntVar(id) =>
              req.as[Array[Byte]].flatMap { bytes =>
                Ok(s"Received ${bytes.length} bytes")
              }
            case req @ POST -> Root / s"pathFile$n" / IntVar(id) =>
              val filePath = tempFilePath()
              val sink = Files[IO].writeAll(Fs2Path.fromNioPath(filePath))
              req.body
                .through(sink)
                .compile
                .drain
                .flatMap(_ => Ok(s"File saved to ${filePath.toAbsolutePath.toString}"))
          }
        }
      ): _*
    )
}

object Tapir extends Endpoints {

  implicit val mErr: MonadError[IO] = new CatsMonadError[IO]

  val serverEndpointGens = replyingWithDummyStr(allEndpoints, IO.pure)

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
      .bindHttp(Port, "localhost")
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
