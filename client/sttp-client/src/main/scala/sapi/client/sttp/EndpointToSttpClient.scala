package sapi.client.sttp

import com.softwaremill.sttp._
import sapi.{Id, _}
import shapeless.HList
import shapeless.ops.function

object EndpointToSttpClient {
  def toClient[I <: HList, O](e: Endpoint[Id, I, O]): HostToClient[I, O, Request[?, Nothing]] = {
    new HostToClient[I, O, Request[?, Nothing]] {
      override def using[F](host: String)(implicit tt: function.FnFromProduct.Aux[I => Request[O, Nothing], F]): F = {
        tt(args => {
          var uri = uri"$host"
          var i = -1
          e.input.inputs.foreach {
            case EndpointInput.PathSegment(p) =>
              uri = uri.copy(path = uri.path :+ p)
            case EndpointInput.PathCapture(_, m, _, _) =>
              i += 1
              val v = m.toString(HList.unsafeGet(args, i))
              uri = uri.copy(path = uri.path :+ v)
            case EndpointInput.Query(name, m, _, _) =>
              i += 1
              m.toOptionalString(HList.unsafeGet(args, i)).foreach { v =>
                uri = uri.param(name, v)
              }
          }

          val base = e.method match {
            case _root_.sapi.Method.GET     => sttp.get(uri)
            case _root_.sapi.Method.HEAD    => sttp.head(uri)
            case _root_.sapi.Method.POST    => sttp.post(uri)
            case _root_.sapi.Method.PUT     => sttp.put(uri)
            case _root_.sapi.Method.DELETE  => sttp.delete(uri)
            case _root_.sapi.Method.OPTIONS => sttp.options(uri)
            case _root_.sapi.Method.PATCH   => sttp.patch(uri)
            case m                          => sttp.copy[Id, String, Nothing](method = com.softwaremill.sttp.Method(m.m), uri = uri)
          }

          base.response(asString.map { s =>
            val so = if (e.output.isOptional && s == "") None else Some(s)
            e.output.fromOptionalString(so).getOrThrow(InvalidOutput)
          })
        })
      }
    }
  }
}
