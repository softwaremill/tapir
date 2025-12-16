package sttp.tapir.server.pekkohttp

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import cats.effect.{IO, Resource}
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._

import scala.concurrent.duration._
import scala.concurrent.Future

class PekkoHttpTestServerInterpreter(implicit actorSystem: ActorSystem)
    extends TestServerInterpreter[Future, PekkoStreams with WebSockets, PekkoHttpServerOptions, Route] {
  override def route(es: List[ServerEndpoint[PekkoStreams with WebSockets, Future]], interceptors: Interceptors): Route = {
    import actorSystem.dispatcher
    val serverOptions: PekkoHttpServerOptions = interceptors(PekkoHttpServerOptions.customiseInterceptors).options
    PekkoHttpServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(
      route: Route,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, Port] = {
    val bind = IO.fromFuture(
      IO(
        Http()
          .newServerAt("localhost", 0)
          .adaptSettings(setts => setts.withRemoteAddressAttribute(true))
          .bind(route)
      )
    )

    Resource
      .make(bind)(server => IO.fromFuture(IO(server.terminate(gracefulShutdownTimeout.getOrElse(50.millis)))).void)
      .map(_.localAddress.getPort)
  }
}
