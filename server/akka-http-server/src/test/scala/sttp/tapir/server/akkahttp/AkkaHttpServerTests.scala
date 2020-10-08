package sttp.tapir.server.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.typesafe.scalalogging.StrictLogging
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.tests.ServerTests
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}
import sttp.tapir.tests.Port

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

abstract class AkkaHttpServerTests[R >: AkkaStreams with WebSockets] extends ServerTests[Future, R, Route] with StrictLogging {
  private implicit var actorSystem: ActorSystem = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    actorSystem = ActorSystem()
  }

  override protected def afterAll(): Unit = {
    Await.result(actorSystem.terminate(), 5.seconds)
    super.afterAll()
  }

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, R, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  ): Route = {
    implicit val serverOptions: AkkaHttpServerOptions = AkkaHttpServerOptions.default.copy(
      decodeFailureHandler = decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)
    )
    e.toRoute
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, R], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): Route = {
    e.toRouteRecoverErrors(fn)
  }

  override def server(routes: NonEmptyList[Route], port: Port): Resource[IO, Unit] = {
    val bind = IO.fromFuture(IO(Http().newServerAt("localhost", port).bind(concat(routes.toList: _*))))
    Resource.make(bind)(binding => IO.fromFuture(IO(binding.unbind())).void).void
  }

  override def pureResult[T](t: T): Future[T] = Future.successful(t)
  override def suspendResult[T](t: => T): Future[T] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future { t }
  }
}
