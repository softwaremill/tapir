package tapir.server.http4s

import cats.effect.Sync
import org.http4s.HttpRoutes
import tapir.Endpoint
import tapir.typelevel.ParamsAsArgs

trait Http4sServer {
  implicit class RichHttp4sHttpEndpoint[I, E, O](e: Endpoint[I, E, O]) {
    def toHttp4sRoutes[F[_]: Sync, FN[_]](logic: FN[F[Either[E, O]]])(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): HttpRoutes[F] = {
      EndpointToHttp4sServer.toHttp4sRoutes(e)(logic)
    }
  }
}
