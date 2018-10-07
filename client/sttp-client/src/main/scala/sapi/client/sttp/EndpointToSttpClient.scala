package sapi.client.sttp

import com.softwaremill.sttp._
import sapi.{Id, _}
import shapeless.HList
import shapeless.ops.function

object EndpointToSttpClient {
  def toClient[I <: HList](e: Endpoint[Id, I]): HostToClient[I, Request[?, Nothing]] = {
    new HostToClient[I, Request[?, Nothing]] {
      override def using[F](host: String)(implicit tt: function.FnFromProduct.Aux[I => Request[String, Nothing], F]): F = {
        tt(args => {
          var uri = uri"$host"
          var i = -1
          e.input.inputs.foreach {
            case EndpointInput.PathSegment(p) =>
              uri = uri.copy(path = uri.path :+ p)
            case EndpointInput.PathCapture(m) =>
              i += 1
              val v = m.toString(HList.unsafeGet(args, i))
              uri = uri.copy(path = uri.path :+ v)
            case EndpointInput.Query(name, m) =>
              i += 1
              val v = m.toString(HList.unsafeGet(args, i))
              uri = uri.param(name, v)
          }

          e.method match {
            case _root_.sapi.Method.GET => sttp.get(uri)
          }
        })
      }
    }
  }
}
