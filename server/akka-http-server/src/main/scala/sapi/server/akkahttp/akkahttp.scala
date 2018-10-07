package sapi.server

import akka.http.scaladsl.server.{Directive, Route}
import cats.Id
import sapi._
import shapeless.HList
import shapeless.ops.function.FnToProduct
import shapeless.ops.hlist.Tupler

import scala.concurrent.Future

package object akkahttp {
  implicit class RichAkkaHttpEndpoint[I <: HList](e: Endpoint[Id, I]) {
    def toDirective[T](implicit t: Tupler.Aux[I, T]): Directive[T] = EndpointToAkkaServer.toDirective(e)

    def toRoute[T, F](logic: F)(implicit tt: FnToProduct.Aux[F, I => Future[String]]): Route =
      EndpointToAkkaServer.toRoute(e)(logic)
  }
}
