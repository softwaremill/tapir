package sttp.tapir.perf.play

import cats.effect.IO
import org.apache.pekko.actor.ActorSystem
import play.api.Mode
import play.api.mvc.{Handler, PlayBodyParsers, RequestHeader}
import play.api.routing.Router
import play.api.routing.Router.Routes
import play.core.server.{DefaultPekkoHttpServerComponents, ServerConfig}
import sttp.tapir.server.play.PlayServerInterpreter
import sttp.tapir.perf.apis._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object Vanilla {
  // val router: Int => Route = (nRoutes: Int) =>
  //   concat(
  //     (0 to nRoutes).map((n: Int) =>
  //       get {
  //         path(("path" + n.toString) / IntNumber) { id =>
  //           complete((n + id).toString)
  //         }
  //       }
  //     ): _*
  //   )
}

object Tapir extends Endpoints {
  val serverEndpointGens = replyingWithDummyStr(allEndpoints, Future.successful)

  def genEndpoints(i: Int) = genServerEndpoints(serverEndpointGens)(i).toList
  import Play._

  val router: Int => Routes = (nRoutes: Int) =>
    PlayServerInterpreter().toRoutes(
      genEndpoints(nRoutes)
    )
}

object Play {
  implicit lazy val perfActorSystem: ActorSystem = ActorSystem("tapir-play")
  implicit lazy val executionContext: ExecutionContextExecutor = perfActorSystem.dispatcher

  def runServer(routes: Routes): IO[ServerRunner.KillSwitch] = {
    val components = new DefaultPekkoHttpServerComponents {
      val initialServerConfig = ServerConfig(port = Some(8080), address = "127.0.0.1", mode = Mode.Test)
      override lazy val actorSystem: ActorSystem = perfActorSystem
      override def router: Router =
        Router.from(
          List(routes).reduce((a: Routes, b: Routes) => {
            val handler: PartialFunction[RequestHeader, Handler] = { case request =>
              a.applyOrElse(request, b)
            }

            handler
          })
        )
    }
    IO(components.server).map(server => IO(server.stop()))
  }
}

object TapirServer extends ServerRunner { override def start = Play.runServer(Tapir.router(1)) }
object TapirMultiServer extends ServerRunner { override def start = Play.runServer(Tapir.router(128)) }
// object VanillaServer extends ServerRunner { override def start = Play.runServer(Vanilla.router(1)) }
// object VanillaMultiServer extends ServerRunner { override def start = Play.runServer(Vanilla.router(128)) }
