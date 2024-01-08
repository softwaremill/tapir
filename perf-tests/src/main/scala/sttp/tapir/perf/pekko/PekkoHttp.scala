package sttp.tapir.perf.pekko

import cats.effect.IO
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.perf.apis._
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object Vanilla {
  val router: Int => Route = (nRoutes: Int) =>
    concat(
      (0 to nRoutes).map((n: Int) =>
        get {
          path(("path" + n.toString) / IntNumber) { id =>
            complete((n + id).toString)
          }
        }
      ): _*
    )
}

object Tapir extends Endpoints {
  implicit val mErr: MonadError[Future] = new FutureMonad()(ExecutionContext.Implicits.global)

  val serverEndpointGens = replyingWithDummyStr[Future](allEndpoints)

  def genEndpoints(i: Int) = genServerEndpoints(serverEndpointGens)(i).toList

  val router: Int => Route = (nRoutes: Int) =>
    PekkoHttpServerInterpreter()(PekkoHttp.executionContext).toRoute(
      genEndpoints(nRoutes)
    )
}

object PekkoHttp {
  implicit val actorSystem: ActorSystem = ActorSystem("tapir-pekko-http")
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

  def runServer(router: Route): IO[ServerRunner.KillSwitch] = {
    IO.fromFuture(
      IO(
        Http()
          .newServerAt("127.0.0.1", 8080)
          .bind(router)
          .map { binding =>
            IO.fromFuture(IO(binding.unbind().flatMap(_ => actorSystem.terminate()))).void
          }
      )
    )
  }
}

object TapirServer extends ServerRunner { override def start = PekkoHttp.runServer(Tapir.router(1)) }
object TapirMultiServer extends ServerRunner { override def start = PekkoHttp.runServer(Tapir.router(128)) }
object VanillaServer extends ServerRunner { override def start = PekkoHttp.runServer(Vanilla.router(1)) }
object VanillaMultiServer extends ServerRunner { override def start = PekkoHttp.runServer(Vanilla.router(128)) }
