package sttp.tapir.server.nima

import cats.effect.{IO, Resource}
import io.helidon.webserver.WebServer
import io.helidon.webserver.http.{Handler, HttpRouting}
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._

import scala.concurrent.duration.FiniteDuration
import java.time.Duration

class NimaTestServerInterpreter() extends TestServerInterpreter[Identity, Any, NimaServerOptions, Handler] {
  override def route(es: List[ServerEndpoint[Any, Identity]], interceptors: Interceptors): Handler = {
    val serverOptions: NimaServerOptions = interceptors(NimaServerOptions.customiseInterceptors).options
    NimaServerInterpreter(serverOptions).toHandler(es)
  }

  override def server(
      route: Handler,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, Port] = {
    val bind = IO.blocking {
      WebServer
        .builder()
        .routing { (builder: HttpRouting.Builder) =>
          builder.any(route): Unit
        }
        .shutdownGracePeriod(Duration.ofMillis(gracefulShutdownTimeout.map(_.toMillis).getOrElse(0L)))
        .build()
        .start()
    }

    Resource
      .make(bind)(server => IO.blocking { val _ = server.stop() })
      .map(_.port)
  }
}
