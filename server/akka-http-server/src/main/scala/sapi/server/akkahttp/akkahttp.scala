package sapi.server

import akka.http.scaladsl.server.{Directive, Route}
import cats.Id
import sapi._
import shapeless.HList
import shapeless.ops.function.FnToProduct
import shapeless.ops.hlist.Tupler

import scala.concurrent.Future

package object akkahttp {
  implicit class RichAkkaHttpEndpoint[I <: HList, O](e: Endpoint[Id, I, O]) {
    def toDirective[T](implicit t: Tupler.Aux[I, T]): Directive[T] = EndpointToAkkaServer.toDirective(e)

    def toRoute[T, F](logic: F)(implicit tt: FnToProduct.Aux[F, I => Future[O]]): Route =
      EndpointToAkkaServer.toRoute(e)(logic)
  }
}
