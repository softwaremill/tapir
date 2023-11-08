package sttp.tapir.server.nima

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.helidon.webserver.WebServer
import io.helidon.webserver.http.{Handler, HttpRouting}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

class NimaTestServerInterpreter() extends TestServerInterpreter[Id, Any, NimaServerOptions, Handler] {
  override def route(es: List[ServerEndpoint[Any, Id]], interceptors: Interceptors): Handler = {
    val serverOptions: NimaServerOptions = interceptors(NimaServerOptions.customiseInterceptors).options
    NimaServerInterpreter(serverOptions).toHandler(es)
  }

  override def server(nimaRoutes: NonEmptyList[Handler]): Resource[IO, Port] = {
    val bind = IO.blocking {
      WebServer
        .builder()
        .routing { (builder: HttpRouting.Builder) =>
          nimaRoutes.iterator
            .foreach(nimaHandler => builder.any(nimaHandler))
        }
        .build()
        .start()
    }

    Resource
      .make(bind) { binding =>
        IO.blocking(binding.stop()).map(_ => ())
      }
      .map(b => b.port)
  }
}
