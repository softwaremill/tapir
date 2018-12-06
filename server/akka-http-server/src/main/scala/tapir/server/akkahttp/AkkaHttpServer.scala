package tapir.server.akkahttp

import akka.http.scaladsl.server.{Directive, Route}
import tapir.Endpoint
import tapir.typelevel.{ParamsAsArgs, ParamsToTuple}

import scala.concurrent.Future

trait AkkaHttpServer {
  implicit class RichAkkaHttpEndpoint[I, E, O](e: Endpoint[I, E, O]) {
    def toDirective[T](implicit paramsToTuple: ParamsToTuple.Aux[I, T]): Directive[T] = EndpointToAkkaServer.toDirective(e)

    def toRoute[FN[_]](logic: FN[Future[Either[E, O]]])(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Route =
      EndpointToAkkaServer.toRoute(e)(logic)
  }
}
