package sttp.tapir.server.finatra

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.finatra.http.{Controller, EmbeddedHttpServer, HttpServer}
import com.twitter.util.Duration
import com.twitter.util.Future
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class FinatraTestServerInterpreter extends TestServerInterpreter[Future, Any, FinatraServerOptions, FinatraRoute] {
  override def route(es: List[ServerEndpoint[Any, Future]], interceptors: Interceptors): FinatraRoute = {
    implicit val serverOptions: FinatraServerOptions = interceptors(FinatraServerOptions.customiseInterceptors).options
    val interpreter = FinatraServerInterpreter(serverOptions)
    es.map(interpreter.toRoute).last
  }

  override def serverWithStop(
      routes: NonEmptyList[FinatraRoute],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, (Port, KillSwitch)] = FinatraTestServerInterpreter.serverWithStop(routes, gracefulShutdownTimeout)
}

object FinatraTestServerInterpreter {
  def serverWithStop(
      routes: NonEmptyList[FinatraRoute],
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, (Port, KillSwitch)] = {
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
      .make(
        bind.map(server =>
          (
            server.httpExternalPort(),
            IO { server.close(Duration.fromMilliseconds(gracefulShutdownTimeout.map(_.toMillis).getOrElse(50))) }
          )
        )
      ) { case (_, release) =>
        release
      }
  }
}
