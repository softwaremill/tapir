package sttp.tapir.server.finatra

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.finatra.http.{Controller, EmbeddedHttpServer, HttpServer}
import com.twitter.util.Future
import sttp.tapir.Endpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.tests.Port

import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

class FinatraTestServerInterpreter extends TestServerInterpreter[Future, Any, FinatraRoute] {
  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[Future]] = None
  ): FinatraRoute = {
    implicit val serverOptions: FinatraServerOptions =
      FinatraServerOptions.customInterceptors
        .metricsInterceptor(metricsInterceptor)
        .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
        .options
    FinatraServerInterpreter(serverOptions).toRoute(e)
  }

  override def route[I, E, O](es: List[ServerEndpoint[I, E, O, Any, Future]]): FinatraRoute = ???

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): FinatraRoute = {
    FinatraServerInterpreter().toRouteRecoverErrors(e)(fn)
  }

  override def server(routes: NonEmptyList[FinatraRoute]): Resource[IO, Port] = FinatraTestServerInterpreter.server(routes)
}

object FinatraTestServerInterpreter {
  def server(routes: NonEmptyList[FinatraRoute]): Resource[IO, Port] = {
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
