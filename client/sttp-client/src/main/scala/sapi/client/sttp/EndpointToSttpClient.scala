package sapi.client.sttp

import com.softwaremill.sttp._
import sapi.TypeMapper.{RequiredTextTypeMapper, TextTypeMapper}
import sapi.internal.SeqToParams
import sapi.typelevel.ParamsToFn
import sapi.{Id, _}

object EndpointToSttpClient {
  def toSttpRequest[I, E, O, S, FN[_]](e: Endpoint[I, E, O], host: String)(
      implicit paramsToFn: ParamsToFn[I, FN]): FN[Request[Either[E, O], Nothing]] = {

    paramsToFn(args => {
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
          val v = m.asInstanceOf[RequiredTextTypeMapper[Any]].toString(paramsToFn.arg(args, i): Any)
          uri = uri.copy(path = uri.path :+ v)
        case EndpointInput.Query(name, m, _, _) =>
          i += 1
          m.asInstanceOf[TextTypeMapper[Any]].toOptionalString(paramsToFn.arg(args, i)).foreach { v => uri = uri.param(name, v)
          }
        case EndpointIO.Body(m, _, _) =>
          i += 1
          m.asInstanceOf[TypeMapper[Any, _]].toOptionalString(paramsToFn.arg(args, i)).foreach { v => req1 = req1.body(v)
          }
        case EndpointIO.Header(name, m, _, _) =>
          i += 1
          m.asInstanceOf[TextTypeMapper[Any]].toOptionalString(paramsToFn.arg(args, i)).foreach { v => req1 = req1.header(name, v)
          }
      }

      var req2 = req1.copy[Id, Either[Any, Any], Nothing](method = com.softwaremill.sttp.Method(e.method.m), uri = uri)

      if (e.output.outputs.nonEmpty || e.errorOutput.outputs.nonEmpty) {
        val responseAs = asString.mapWithMetadata { (body, meta) =>
          val outputs = if (meta.isSuccess) e.output.outputs else e.errorOutput.outputs

          val values = outputs
            .map {
              case EndpointIO.Body(m, _, _) =>
                val so = if (m.isOptional && body == "") None else Some(body)
                m.fromOptionalString(so).getOrThrow(InvalidOutput)

              case EndpointIO.Header(name, m, _, _) =>
                m.fromOptionalString(meta.header(name)).getOrThrow(InvalidOutput)
            }

          val params = SeqToParams(values)
          if (meta.isSuccess) Right(params) else Left(params)
        }

        req2 = req2.response(responseAs.asInstanceOf[ResponseAs[Either[Any, Any], Nothing]]).parseResponseIf(_ => true)
      }

      req2.asInstanceOf[Request[Either[E, O], Nothing]]
    })
  }
}
