package tapir.server.http4s

import cats.effect.{ContextShift, Sync}
import org.http4s.{EntityBody, HttpRoutes}
import tapir.typelevel.ParamsAsArgs
import tapir.{Defaults, Endpoint, StatusCode}

trait Http4sServer {
  implicit class RichHttp4sHttpEndpoint[I, E, O, F[_]](e: Endpoint[I, E, O, EntityBody[F]]) {
    def toRoutes[FN[_]](logic: FN[F[Either[E, O]]],
                        statusMapper: O => StatusCode = Defaults.statusMapper,
                        errorStatusMapper: E => StatusCode = Defaults.errorStatusMapper)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN],
                                                                                         serverOptions: Http4sServerOptions[F],
                                                                                         fs: Sync[F],
                                                                                         fcs: ContextShift[F]): HttpRoutes[F] = {
      new EndpointToHttp4sServer(serverOptions).toRoutes(e)(logic, statusMapper, errorStatusMapper)
    }
  }
}
