package sapi.client.sttp

import com.softwaremill.sttp._
import sapi.{Id, _}
import shapeless.HList
import shapeless.ops.function

object EndpointToSttpClient {
  def toSttpRequest[I <: HList, O <: HList, OE <: HList, TO, TOE, F](e: Endpoint[I, O, OE], host: String)(
      implicit oToTuple: HListToResult.Aux[O, TO],
      oeToTuple: HListToResult.Aux[OE, TOE],
      tt: function.FnFromProduct.Aux[I => Request[Either[TOE, TO], Nothing], F]): F = {

    tt(args => {
      var uri = uri"$host"
      var req1 = sttp
        .response(ignore)
        .mapResponse(Right(_): Either[Any, Any])

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
        case EndpointIO.Body(m, _, _) =>
          i += 1
          m.toOptionalString(HList.unsafeGet(args, i)).foreach { v =>
            req1 = req1.body(v)
          }
        case EndpointIO.Header(name, m, _, _) =>
          i += 1
          m.toOptionalString(HList.unsafeGet(args, i)).foreach { v =>
            req1 = req1.header(name, v)
          }
      }

      var req2 = req1.copy[Id, Either[Any, Any], Nothing](method = com.softwaremill.sttp.Method(e.method.m), uri = uri)

      e.output.outputs.foreach {
        case EndpointIO.Body(m, _, _) =>
          req2 = req2.response(asString.map { s =>
            val so = if (m.isOptional && s == "") None else Some(s)
            Right(m.fromOptionalString(so).getOrThrow(InvalidOutput))
          })
      }

      req2.asInstanceOf[Request[Either[TOE, TO], Nothing]]
    })
  }
}
