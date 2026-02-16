package sttp.tapir.server.play

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import play.api.Mode
import play.api.http.ParserConfiguration
import play.api.mvc.PlayBodyParsers
import play.api.routing.Router
import play.api.routing.Router.Routes
import play.core.server.{DefaultPekkoHttpServerComponents, ServerConfig}
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._

import scala.concurrent.Future
import scala.concurrent.duration._

class PlayTestServerInterpreter(implicit actorSystem: ActorSystem)
    extends TestServerInterpreter[Future, PekkoStreams with WebSockets, PlayServerOptions, Routes] {
  import actorSystem.dispatcher

  override def route(es: List[ServerEndpoint[PekkoStreams with WebSockets, Future]], interceptors: Interceptors): Routes = {
    val serverOptions: PlayServerOptions = interceptors(PlayServerOptions.customiseInterceptors()).options
      // increase the default maxMemoryBuffer to 10M so that tests pass
      .copy(playBodyParsers = PlayBodyParsers(conf = ParserConfiguration(maxMemoryBuffer = 1024000)))
    PlayServerInterpreter(serverOptions).toRoutes(es)
  }

  override def server(
      route: Routes,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, Port] = {
    lazy val components = new DefaultPekkoHttpServerComponents {
      val initialServerConfig = ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test)

      val customConf =
        Configuration(
          ConfigFactory.parseString(s"play { server.terminationTimeout=${gracefulShutdownTimeout.getOrElse(50.millis).toString} }")
        )
      override lazy val serverConfig: ServerConfig =
        initialServerConfig.copy(configuration = customConf.withFallback(initialServerConfig.configuration))
      override lazy val actorSystem: ActorSystem =
        ActorSystem("tapir", defaultExecutionContext = Some(PlayTestServerInterpreter.this.actorSystem.dispatcher))
      override lazy val router: Router = Router.from(route)
    }
    val bind = IO.blocking {
      components.server
    }
    Resource.make(bind)(s => IO.blocking(s.stop())).map(s => (s.mainAddress.getPort))
  }
}
