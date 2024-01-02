package sttp.tapir.perf.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import sttp.tapir.perf
import sttp.tapir.perf.apis._
import sttp.tapir.perf.Common
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.{ExecutionContextExecutor, Future}
import sttp.monad.MonadError
import sttp.monad.FutureMonad
import scala.concurrent.ExecutionContext
import cats.effect.IO

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
    AkkaHttpServerInterpreter()(AkkaHttp.executionContext).toRoute(
      genEndpoints(nRoutes)
    )
}

object AkkaHttp {
  implicit val actorSystem: ActorSystem = ActorSystem("akka-http")
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

object TapirServer extends ServerRunner { override def start = AkkaHttp.runServer(Tapir.router(1)) }
object TapirMultiServer extends ServerRunner { override def start = AkkaHttp.runServer(Tapir.router(128)) }
object VanillaServer extends ServerRunner { override def start = AkkaHttp.runServer(Vanilla.router(1)) }
object VanillaMultiServer extends ServerRunner { override def start = AkkaHttp.runServer(Vanilla.router(128)) }
