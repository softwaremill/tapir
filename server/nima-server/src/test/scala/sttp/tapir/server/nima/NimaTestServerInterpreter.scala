package sttp.tapir.server.nima

import cats.data.NonEmptyList
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

  override def serverWithStop(
      nimaRoutes: NonEmptyList[Handler],
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, (Port, KillSwitch)] = {
    val bind = IO.blocking {
      WebServer
        .builder()
        .routing { (builder: HttpRouting.Builder) =>
          nimaRoutes.iterator
            .foreach(nimaHandler => builder.any(nimaHandler))
        }
        .shutdownGracePeriod(Duration.ofMillis(gracefulShutdownTimeout.map(_.toMillis).getOrElse(0L)))
        .build()
        .start()
    }

    Resource
      .make(bind.map(b => (b.port, IO.blocking(b.stop()).map(_ => ())))) { case (_, release) =>
        release
      }
  }
}
