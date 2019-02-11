package tapir.server.akkahttp

import akka.http.scaladsl.server.{Directive, Route}
import tapir.server.{ServerDefaults, StatusMapper}
import tapir.typelevel.{ParamsAsArgs, ParamsToTuple}
import tapir.Endpoint

import scala.concurrent.Future

trait AkkaHttpServer {
  implicit class RichAkkaHttpEndpoint[I, E, O](e: Endpoint[I, E, O, AkkaStream]) {
    def toDirective[T](implicit paramsToTuple: ParamsToTuple.Aux[I, T], akkaHttpOptions: AkkaHttpServerOptions): Directive[T] =
      new EndpointToAkkaServer(akkaHttpOptions).toDirective(e)

    def toRoute[FN[_]](logic: FN[Future[Either[E, O]]])(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN],
                                                        serverOptions: AkkaHttpServerOptions,
                                                        statusMapper: StatusMapper[O] = ServerDefaults.statusMapper,
                                                        errorStatusMapper: StatusMapper[E] = ServerDefaults.errorStatusMapper): Route =
      new EndpointToAkkaServer(serverOptions).toRoute(e)(logic, statusMapper, errorStatusMapper)
  }
}
