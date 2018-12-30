package tapir.server.akkahttp

import akka.http.scaladsl.server.{Directive, Route}
import tapir.internal.DefaultStatusMappers
import tapir.typelevel.{ParamsAsArgs, ParamsToTuple}
import tapir.{Endpoint, StatusCode}

import scala.concurrent.Future

trait AkkaHttpServer {
  implicit class RichAkkaHttpEndpoint[I, E, O](e: Endpoint[I, E, O]) {
    def toDirective[T](implicit paramsToTuple: ParamsToTuple.Aux[I, T]): Directive[T] = EndpointToAkkaServer.toDirective(e)

    def toRoute[FN[_]](
        logic: FN[Future[Either[E, O]]],
        statusMapper: O => StatusCode = DefaultStatusMappers.out,
        errorStatusMapper: E => StatusCode = DefaultStatusMappers.error)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Route =
      EndpointToAkkaServer.toRoute(e)(logic, statusMapper, errorStatusMapper)
  }
}
