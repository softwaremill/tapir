package sapi.client

import com.softwaremill.sttp.{Id, Request}
import sapi.Endpoint
import shapeless.HList
import shapeless.ops.function

package object sttp {
  implicit class RichEndpoint[I <: HList](e: Endpoint[Id, I]) {
    def toSttpClient: HostToClient[I, Request[?, Nothing]] = EndpointToSttpClient.toClient(e)
  }

  trait HostToClient[I <: HList, R[_]] {
    def using[F](host: String)(implicit tt: function.FnFromProduct.Aux[I => R[String], F]): F
  }
}
