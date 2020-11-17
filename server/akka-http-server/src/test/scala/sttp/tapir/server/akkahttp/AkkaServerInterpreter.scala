package sttp.tapir.server.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}
import sttp.tapir.server.tests.ServerInterpreter
import sttp.tapir.tests.Port
import cats.syntax.all._

import scala.concurrent.Future
import scala.reflect.ClassTag

class AkkaServerInterpreter(implicit actorSystem: ActorSystem) extends ServerInterpreter[Future, AkkaStreams with WebSockets, Route] {
  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, AkkaStreams with WebSockets, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  ): Route = {
    implicit val serverOptions: AkkaHttpServerOptions = AkkaHttpServerOptions.default.copy(
      decodeFailureHandler = decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)
    )
    e.toRoute
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, AkkaStreams with WebSockets], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): Route = {
    e.toRouteRecoverErrors(fn)
  }

  override def server(routes: NonEmptyList[Route]): Resource[IO, Port] = {
    val bind = IO.fromFuture(IO(Http().newServerAt("localhost", 0).bind(concat(routes.toList: _*))))
    Resource.make(bind)(binding => IO.fromFuture(IO(binding.unbind())).void).map(_.localAddress.getPort)
  }
}
