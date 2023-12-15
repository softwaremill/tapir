package sttp.tapir.perf.http4s

import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl._
import org.http4s.implicits._
import org.http4s.server.Router
import sttp.tapir.perf
import sttp.tapir.perf.Common
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint

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

object Tapir extends perf.apis.SimpleGetEndpoints {

  implicit val mErr: MonadError[IO] = new CatsMonadError[IO]

  val serverEndpointGens = replyingWithDummyStr[IO](
    List(gen_get_in_string_out_string, gen_post_in_string_out_string)
  )

  val router: Int => HttpRoutes[IO] = (nRoutes: Int) =>
    Router("/" -> {
      Http4sServerInterpreter[IO]().toRoutes(
        genServerEndpoints(serverEndpointGens)(nRoutes).toList
      )
    })
}

object Http4s {
  def runServer(router: HttpRoutes[IO]): IO[ExitCode] = {
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(router.orNotFound)
      .resource
      .use(_ => { perf.Common.blockServer(); IO.pure(ExitCode.Success) })
  }
}

object TapirServer extends App { Http4s.runServer(Tapir.router(1)).unsafeRunSync() }
object TapirMultiServer extends App { Http4s.runServer(Tapir.router(128)).unsafeRunSync() }
object VanillaServer extends App { Http4s.runServer(Vanilla.router(1)).unsafeRunSync() }
object PostStringServer extends App { Http4s.runServer(Vanilla.router(1)).unsafeRunSync() }
object VanillaMultiServer extends App { Http4s.runServer(Vanilla.router(128)).unsafeRunSync() }
