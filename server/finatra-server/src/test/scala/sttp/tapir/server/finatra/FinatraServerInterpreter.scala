package sttp.tapir.server.finatra

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO, Resource, Timer}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.finatra.http.{Controller, EmbeddedHttpServer, HttpServer}
import com.twitter.util.Future
import sttp.tapir.Endpoint
import sttp.tapir.server.tests.ServerInterpreter
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}
import sttp.tapir.tests.Port

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

class FinatraServerInterpreter extends ServerInterpreter[Future, Any, FinatraRoute] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  ): FinatraRoute = {
    implicit val serverOptions: FinatraServerOptions =
      FinatraServerOptions.default.copy(decodeFailureHandler = decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler))
    e.toRoute
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): FinatraRoute = {
    e.toRouteRecoverErrors(fn)
  }

  override def server(routes: NonEmptyList[FinatraRoute]): Resource[IO, Port] = FinatraServerInterpreter.server(routes)
}

object FinatraServerInterpreter {
  def server(routes: NonEmptyList[FinatraRoute])(implicit ioTimer: Timer[IO]): Resource[IO, Port] = {
    def waitUntilHealthy(s: EmbeddedHttpServer, count: Int): IO[EmbeddedHttpServer] =
      if (s.isHealthy) IO.pure(s)
      else if (count > 1000) IO.raiseError(new IllegalStateException("Server unhealthy"))
      else IO.sleep(10.milliseconds).flatMap(_ => waitUntilHealthy(s, count + 1))

    val bind = IO {
      class TestController extends Controller with TapirController {
        routes.toList.foreach(addTapirRoute)
      }

      class TestServer extends HttpServer {
        override protected def configureHttp(router: HttpRouter): Unit = {
          router.add(new TestController)
        }
      }

      val server = new EmbeddedHttpServer(
        new TestServer,
        Map(
          "http.port" -> s":0"
        ),
        // in the default implementation waitForWarmup suspends the thread for 1 second between healthy checks
        // we improve on that by checking every 10ms
        waitForWarmup = false,
        globalFlags = Map(
          com.twitter.finagle.netty4.numWorkers -> "1",
          com.twitter.jvm.numProcs -> "1"
        )
      )
      server.start()
      server
    }.flatMap(waitUntilHealthy(_, 0))

    Resource
      .make(bind)(httpServer => IO(httpServer.close()))
      .map(_.httpExternalPort())
  }
}
