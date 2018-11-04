package sapi.server

import akka.http.scaladsl.server.{Directive, Route}
import sapi._
import shapeless.HList
import shapeless.ops.function.FnToProduct
import shapeless.ops.hlist.Tupler

import scala.concurrent.Future

package object akkahttp {
  implicit class RichAkkaHttpEndpoint[I <: HList, O <: HList, OE <: HList](val e: Endpoint[I, O, OE]) extends AnyVal {
    def toDirective[T](implicit t: Tupler.Aux[I, T]): Directive[T] = EndpointToAkkaServer.toDirective(e)

    def toRoute[T, TO, TOE, F](logic: F)(implicit oToTuple: HListToResult.Aux[O, TO],
                                         oeToTuple: HListToResult.Aux[OE, TOE],
                                         tt: FnToProduct.Aux[F, I => Future[Either[TOE, TO]]]): Route =
      EndpointToAkkaServer.toRoute(e)(logic)
  }
}
