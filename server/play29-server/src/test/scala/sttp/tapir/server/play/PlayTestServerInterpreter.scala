package sttp.tapir.server.play

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import play.api.Configuration
import play.api.Mode
import play.api.http.ParserConfiguration
import play.api.mvc.{Handler, PlayBodyParsers, RequestHeader}
import play.api.routing.Router
import play.api.routing.Router.Routes
import play.core.server.{DefaultAkkaHttpServerComponents, ServerConfig}
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._

import scala.concurrent.Future
import scala.concurrent.duration._

class PlayTestServerInterpreter(implicit actorSystem: ActorSystem)
    extends TestServerInterpreter[Future, AkkaStreams with WebSockets, PlayServerOptions, Routes] {
  import actorSystem.dispatcher

  override def route(es: List[ServerEndpoint[AkkaStreams with WebSockets, Future]], interceptors: Interceptors): Routes = {
    val serverOptions: PlayServerOptions = interceptors(PlayServerOptions.customiseInterceptors()).options
      // increase the default maxMemoryBuffer to 10M so that tests pass
      .copy(playBodyParsers = PlayBodyParsers(conf = ParserConfiguration(maxMemoryBuffer = 1024000)))
    PlayServerInterpreter(serverOptions).toRoutes(es)
  }

  override def serverWithStop(
      routes: NonEmptyList[Routes],
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, (Port, KillSwitch)] = {
    val components = new DefaultAkkaHttpServerComponents {
      val initialServerConfig = ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test)

      val customConf =
        Configuration(
          ConfigFactory.parseString(s"play { server.terminationTimeout=${gracefulShutdownTimeout.getOrElse(50.millis).toString} }")
        )
      override lazy val serverConfig: ServerConfig =
        initialServerConfig.copy(configuration = customConf.withFallback(initialServerConfig.configuration))
      override lazy val actorSystem: ActorSystem =
        ActorSystem("tapir", defaultExecutionContext = Some(PlayTestServerInterpreter.this.actorSystem.dispatcher))
      override lazy val router: Router =
        Router.from(
          routes.reduce((a: Routes, b: Routes) => {
            val handler: PartialFunction[RequestHeader, Handler] = { case request =>
              a.applyOrElse(request, b)
            }

            handler
          })
        )
    }
    val bind = IO {
      components.server
    }
    Resource.make(bind.map(s => (s.mainAddress.getPort, IO(s.stop())))) { case (_, release) => release }
  }
}
