package sttp.tapir.server.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._

import scala.concurrent.Future
import scala.concurrent.duration._

class AkkaHttpTestServerInterpreter(implicit actorSystem: ActorSystem)
    extends TestServerInterpreter[Future, AkkaStreams with WebSockets, AkkaHttpServerOptions, Route] {
  override def route(es: List[ServerEndpoint[AkkaStreams with WebSockets, Future]], interceptors: Interceptors): Route = {
    import actorSystem.dispatcher
    val serverOptions: AkkaHttpServerOptions = interceptors(AkkaHttpServerOptions.customiseInterceptors).options
    AkkaHttpServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(
      routes: NonEmptyList[Route],
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, Port] = {
    val bind = IO.fromFuture(IO(Http().newServerAt("localhost", 0).bind(concat(routes.toList: _*))))

    Resource
      .make(bind)(server => IO.fromFuture(IO(server.terminate(gracefulShutdownTimeout.getOrElse(50.millis)))).void)
      .map(_.localAddress.getPort)
  }
}
