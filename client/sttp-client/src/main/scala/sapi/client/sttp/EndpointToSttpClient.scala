package sapi.client.sttp

import com.softwaremill.sttp._
import sapi.EndpointLogicFn.FnFromHList
import sapi.internal.SeqToHList
import sapi.{Id, _}
import shapeless._

object EndpointToSttpClient {
  def toSttpRequest[I <: HList, O <: HList, E <: HList, S, FN[_]](e: Endpoint[I, O, E], host: String)(
      implicit endpointLogicFn: EndpointLogicFn[I, E, O],
      fnFromHList: FnFromHList[I, FN]): FN[Request[Either[endpointLogicFn.TE, endpointLogicFn.TO], Nothing]] = {

    fnFromHList(args => {
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

      if (e.output.outputs.nonEmpty || e.errorOutput.outputs.nonEmpty) {
        val responseAs = asString.mapWithMetadata {
          (body, meta) =>
            val (outputs, toTuple: HListToResult[HList]) =
              if (meta.isSuccess) (e.output.outputs, endpointLogicFn.oToResult) else (e.errorOutput.outputs, endpointLogicFn.eToResult)

            val values = outputs
              .map {
                case EndpointIO.Body(m, _, _) =>
                  val so = if (m.isOptional && body == "") None else Some(body)
                  m.fromOptionalString(so).getOrThrow(InvalidOutput)

                case EndpointIO.Header(name, m, _, _) =>
                  m.fromOptionalString(meta.header(name)).getOrThrow(InvalidOutput)
              }

            toTuple(SeqToHList(values))
        }

        req2 = req2.response(responseAs.asInstanceOf[ResponseAs[Either[Any, Any], Nothing]]).parseResponseIf(_ => true)
      }

      req2.asInstanceOf[Request[Either[endpointLogicFn.TE, endpointLogicFn.TO], Nothing]]
    })
  }
}
