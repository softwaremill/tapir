package sapi.server

import akka.http.scaladsl.server.{Directive, Route}
import sapi._
import sapi.typelevel.{ParamsToFn, ParamsToTuple}

import scala.concurrent.Future

package object akkahttp {
  implicit class RichAkkaHttpEndpoint[I, E, O](val e: Endpoint[I, E, O]) extends AnyVal {
    def toDirective[T](implicit paramsToTuple: ParamsToTuple.Aux[I, T]): Directive[T] = EndpointToAkkaServer.toDirective(e)

    def toRoute[FN[_]](logic: FN[Future[Either[E, O]]])(implicit fnFromParams: ParamsToFn[I, FN]): Route =
      EndpointToAkkaServer.toRoute(e)(logic)
  }
}
