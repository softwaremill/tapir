package tapir.server.http4s

import cats.effect.{ContextShift, Sync}
import org.http4s.HttpRoutes
import tapir.typelevel.ParamsAsArgs
import tapir.{Defaults, Endpoint, StatusCode}

trait Http4sServer {
  implicit class RichHttp4sHttpEndpoint[I, E, O](e: Endpoint[I, E, O]) {
    def toRoutes[F[_]: Sync: ContextShift, FN[_]](logic: FN[F[Either[E, O]]],
                                                  statusMapper: O => StatusCode = Defaults.statusMapper,
                                                  errorStatusMapper: E => StatusCode = Defaults.errorStatusMapper)(
        implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN],
        serverOptions: Http4sServerOptions[F]): HttpRoutes[F] = {
      new EndpointToHttp4sServer(serverOptions).toRoutes(e)(logic, statusMapper, errorStatusMapper)
    }
  }
}
