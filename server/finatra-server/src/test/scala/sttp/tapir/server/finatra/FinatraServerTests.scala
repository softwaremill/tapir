package sttp.tapir.server.finatra

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO, Resource, Timer}
import com.github.ghik.silencer.silent
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.filters.{AccessLoggingFilter, ExceptionMappingFilter}
import com.twitter.finatra.http.{Controller, EmbeddedHttpServer, HttpServer}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.util.{Future, FuturePool}
import sttp.tapir.Endpoint
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults}
import sttp.tapir.server.tests.ServerTests
import sttp.tapir.tests.{Port, PortCounter}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.concurrent.duration._

class FinatraServerTests extends ServerTests[Future, Nothing, FinatraRoute] {
  private val futurePool = FuturePool.unboundedPool

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  override def pureResult[T](t: T): Future[T] = Future.value(t)

  override def suspendResult[T](t: => T): Future[T] = futurePool {
    t
  }

  override def route[I, E, O](
      e: Endpoint[I, E, O, Nothing],
      fn: I => Future[Either[E, O]],
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  ): FinatraRoute = {
    implicit val serverOptions: FinatraServerOptions =
      FinatraServerOptions.default.copy(decodeFailureHandler = decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler))
    e.toRoute(fn)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Nothing], fn: I => Future[O])(
      implicit eClassTag: ClassTag[E]
  ): FinatraRoute = {
    e.toRouteRecoverErrors(fn)
  }

  override def server(routes: NonEmptyList[FinatraRoute], port: Port): Resource[IO, Unit] = FinatraServerTests.server(routes, port)

  override lazy val portCounter: PortCounter = new PortCounter(58000)
}

object FinatraServerTests {
  def server(routes: NonEmptyList[FinatraRoute], port: Port)(implicit ioTimer: Timer[IO]): Resource[IO, Unit] = {
    def waitUntilHealthy(s: EmbeddedHttpServer, count: Int): IO[EmbeddedHttpServer] =
      if (s.isHealthy) IO.pure(s)
      else if (count > 1000) IO.raiseError(new IllegalStateException("Server unhealthy"))
      else IO.sleep(10.milliseconds).flatMap(_ => waitUntilHealthy(s, count + 1))

    val bind = IO {
      class TestController extends Controller with TapirController {
        routes.toList.foreach(addTapirRoute)
      }

      class TestServer extends HttpServer {
        @silent("discarded")
        override protected def configureHttp(router: HttpRouter): Unit = {
          router
            .filter[AccessLoggingFilter[Request]]
            .filter[ExceptionMappingFilter[Request]]
            .add(new TestController)
        }
      }

      val server = new EmbeddedHttpServer(
        new TestServer,
        Map(
          "http.port" -> s":$port"
        ),
        // in the default implementation waitForWarmup suspends the thread for 1 second between healthy checks
        // we improve on that by checking every 10ms
        waitForWarmup = false
      )
      server.start()
      server
    }.flatMap(waitUntilHealthy(_, 0))

    Resource
      .make(bind)(httpServer => IO(httpServer.close()))
      .map(_ => ())
  }
}
