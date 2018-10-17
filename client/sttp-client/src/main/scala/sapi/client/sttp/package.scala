package sapi.client

import com.softwaremill.sttp.{Id, Request}
import sapi.Endpoint
import shapeless.HList
import shapeless.ops.function

package object sttp {
  implicit class RichEndpoint[I <: HList, O](val e: Endpoint[Id, I, O]) extends AnyVal {
    def toSttpClient: HostToClient[I, O, Request[?, Nothing]] = EndpointToSttpClient.toClient(e)
  }

  trait HostToClient[I <: HList, O, R[_]] {
    def using[F](host: String)(implicit tt: function.FnFromProduct.Aux[I => R[O], F]): F
  }
}
