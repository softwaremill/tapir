package tapir.server.play

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import play.api.Mode
import play.api.mvc.{Handler, RequestHeader}
import play.api.routing.Router
import play.api.routing.Router.Routes
import play.core.server.{DefaultAkkaHttpServerComponents, ServerConfig}
import tapir.Endpoint
import tapir.server.tests.ServerTests
import tapir.server.{DecodeFailureHandler, ServerDefaults}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

class PlayServerTests extends ServerTests[Future, Nothing, Router.Routes] with TapirPlayServer {
  override def multipleValueHeaderSupport: Boolean = false
  override def streamingSupport: Boolean = false

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
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
  ): Routes = {
    e.toRouteRecoverErrors(fn)
  }

  override def server(routes: NonEmptyList[Routes], port: Port): Resource[IO, Unit] = {
    val components = new DefaultAkkaHttpServerComponents {
      override lazy val serverConfig: ServerConfig = ServerConfig(port = Some(port), address = "127.0.0.1", mode = Mode.Test)
      override def router: Router =
        Router.from(
          routes.reduce(
            (a: Routes, b: Routes) => {
              val handler: PartialFunction[RequestHeader, Handler] = {
                case request => a.applyOrElse(request, b)
              }

              handler
            }
          )
        )
    }
    val bind = IO {
      components.server
    }
    Resource.make(bind)(s => IO(s.stop())).map(_ => ())
  }

  override def initialPort: Port = 38000
}
