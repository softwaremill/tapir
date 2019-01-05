package tapir.server.http4s

import cats.effect.{ContextShift, Sync}
import org.http4s.HttpRoutes
import tapir.typelevel.ParamsAsArgs
import tapir.{DefaultStatusMappers, Endpoint, StatusCode}

import scala.concurrent.ExecutionContext

trait Http4sServer {
  implicit class RichHttp4sHttpEndpoint[I, E, O](e: Endpoint[I, E, O]) {
    def toRoutes[F[_]: Sync: ContextShift, FN[_]](
        logic: FN[F[Either[E, O]]],
        blockingExecutionContext: ExecutionContext = ExecutionContext.Implicits.global,
        statusMapper: O => StatusCode = DefaultStatusMappers.out,
        errorStatusMapper: E => StatusCode = DefaultStatusMappers.error)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): HttpRoutes[F] = {
      new EndpointToHttp4sServer(blockingExecutionContext).toRoutes(e)(logic, statusMapper, errorStatusMapper)
    }
  }
}
