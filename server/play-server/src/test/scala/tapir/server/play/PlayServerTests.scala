package tapir.server.play

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import tapir.server.tests.ServerTests

import scala.concurrent.Future
import play.api.routing.Router
import play.api.routing.Router.Routes
import tapir.Endpoint
import tapir.server.{DecodeFailureHandler, ServerDefaults}
import cats.implicits._
import play.api.Mode
import play.core.server.{DefaultAkkaHttpServerComponents, ServerConfig}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

class PlayServerTests extends ServerTests[Future, Nothing, Router.Routes] with TapirPlayServer {
  private implicit val actorSystem: ActorSystem = ActorSystem()
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val pc: PlayComponents2 = PlayComponents2.apply
  override def pureResult[T](t: T): Future[T] = Future.successful(t)

  override def suspendResult[T](t: => T): Future[T] = Future(t)

  override def route[I, E, O](
      e: Endpoint[I, E, O, Nothing],
      fn: I => Future[Either[E, O]],
      decodeFailureHandler: Option[DecodeFailureHandler[Any]]
  ): Routes = {
    implicit val serverOptions: PlayServerOptions =
      PlayServerOptions.default.copy(decodeFailureHandler = decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler))
    e.toRoute(fn)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Nothing], fn: I => Future[O])(
      implicit eClassTag: ClassTag[E]
  ): Routes = ???

  override def server(routes: NonEmptyList[Routes], port: Port): Resource[IO, Unit] = {
    val components = new DefaultAkkaHttpServerComponents {
      override lazy val serverConfig: ServerConfig = ServerConfig(port = Some(port), address = "127.0.0.1", mode = Mode.Test)
      override def router: Router = Router.from(routes.head)
    }
    val bind = IO {
      components.server
    }
    Resource.make(bind)(s => IO(s.stop())).void
  }

  override def initialPort: Port = 38000
}
