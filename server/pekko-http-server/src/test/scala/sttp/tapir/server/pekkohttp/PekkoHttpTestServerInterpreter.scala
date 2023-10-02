package sttp.tapir.server.pekkohttp

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives.concat
import org.apache.pekko.http.scaladsl.server.Route
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.concurrent.Future
import scala.reflect.ClassTag

class PekkoHttpTestServerInterpreter(implicit actorSystem: ActorSystem)
    extends TestServerInterpreter[Future, PekkoStreams with WebSockets, Route] {
  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, PekkoStreams with WebSockets, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  ): Route = {
    implicit val serverOptions: PekkoHttpServerOptions = PekkoHttpServerOptions.default.copy(
      decodeFailureHandler = decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)
    )
    PekkoHttpServerInterpreter.toRoute(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, PekkoStreams with WebSockets], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): Route = {
    PekkoHttpServerInterpreter.toRouteRecoverErrors(e)(fn)
  }

  override def server(routes: NonEmptyList[Route]): Resource[IO, Port] = {
    val bind = IO.fromFuture(IO(Http().newServerAt("localhost", 0).bind(concat(routes.toList: _*))))
    Resource.make(bind)(binding => IO.fromFuture(IO(binding.unbind())).void).map(_.localAddress.getPort)
  }
}
