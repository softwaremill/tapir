package sttp.tapir.perf.pekko

import cats.effect.IO
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import sttp.tapir.perf.apis._
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import org.apache.pekko.stream.ActorMaterializer

object Vanilla {
  val router: Int => ActorSystem => Route = (nRoutes: Int) => (_: ActorSystem) =>
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
  val serverEndpointGens = replyingWithDummyStr(allEndpoints, Future.successful)

  def genEndpoints(i: Int) = genServerEndpoints(serverEndpointGens)(i).toList

  def router: Int => ActorSystem => Route = (nRoutes: Int) => (actorSystem: ActorSystem) =>
    PekkoHttpServerInterpreter()(actorSystem.dispatcher).toRoute(
      genEndpoints(nRoutes)
    )
}

object PekkoHttp {
  def runServer(router: ActorSystem => Route): IO[ServerRunner.KillSwitch] = {
    // We need to create a new actor system each time server is run
    implicit val actorSystem: ActorSystem = ActorSystem("tapir-pekko-http")
    implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
    IO.fromFuture(
      IO(
        Http()
          .newServerAt("127.0.0.1", 8080)
          .bind(router(actorSystem))
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
