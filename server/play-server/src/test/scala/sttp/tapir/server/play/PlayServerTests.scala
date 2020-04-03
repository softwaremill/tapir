package sttp.tapir.server.play

import akka.actor.ActorSystem
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import play.api.Mode
import play.api.mvc.{Handler, RequestHeader}
import play.api.routing.Router
import play.api.routing.Router.Routes
import play.core.server.{DefaultAkkaHttpServerComponents, ServerConfig}
import sttp.tapir.Endpoint
import sttp.tapir.server.tests.ServerTests
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults}
import sttp.tapir.tests.{Port, PortCounter}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

class PlayServerTests extends ServerTests[Future, Nothing, Router.Routes] {
  override def multipleValueHeaderSupport: Boolean = false
  override def multipartInlineHeaderSupport: Boolean = false
  override def streamingSupport: Boolean = false

  private implicit val actorSystem: ActorSystem = ActorSystem()

  override protected def afterAll(): Unit = {
    Await.result(actorSystem.terminate(), 5.seconds)
    super.afterAll()
  }

  override def pureResult[T](t: T): Future[T] = Future.successful(t)

  override def suspendResult[T](t: => T): Future[T] = Future(t)

  override def route[I, E, O](
      e: Endpoint[I, E, O, Nothing],
      fn: I => Future[Either[E, O]],
      decodeFailureHandler: Option[DecodeFailureHandler]
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
          routes.reduce((a: Routes, b: Routes) => {
            val handler: PartialFunction[RequestHeader, Handler] = {
              case request => a.applyOrElse(request, b)
            }

            handler
          })
        )
    }
    val bind = IO {
      components.server
    }
    Resource.make(bind)(s => IO(s.stop())).map(_ => ())
  }

  override val portCounter: PortCounter = new PortCounter(38000)
}
