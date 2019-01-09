package tapir.server.akkahttp

import akka.http.scaladsl.server.{Directive, Route}
import tapir.typelevel.{ParamsAsArgs, ParamsToTuple}
import tapir.{Defaults, Endpoint, StatusCode}

import scala.concurrent.Future

trait AkkaHttpServer {
  implicit class RichAkkaHttpEndpoint[I, E, O](e: Endpoint[I, E, O]) {
    def toDirective[T](implicit paramsToTuple: ParamsToTuple.Aux[I, T], akkaHttpOptions: AkkaHttpServerOptions): Directive[T] =
      new EndpointToAkkaServer(akkaHttpOptions).toDirective(e)

    def toRoute[FN[_]](logic: FN[Future[Either[E, O]]],
                       statusMapper: O => StatusCode = Defaults.statusMapper,
                       errorStatusMapper: E => StatusCode = Defaults.errorStatusMapper)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN],
                                                                                        serverOptions: AkkaHttpServerOptions): Route =
      new EndpointToAkkaServer(serverOptions).toRoute(e)(logic, statusMapper, errorStatusMapper)
  }
}
