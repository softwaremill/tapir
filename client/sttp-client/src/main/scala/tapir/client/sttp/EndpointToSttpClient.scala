package tapir.client.sttp

import com.softwaremill.sttp._
import tapir.TypeMapper.{RequiredTextTypeMapper, TextTypeMapper}
import tapir.internal.SeqToParams
import tapir.typelevel.ParamsAsArgs
import tapir.{Id, _}

object EndpointToSttpClient {
  // don't look. The code is really, really ugly.

  def toSttpRequest[I, E, O, S](e: Endpoint[I, E, O], baseUri: Uri)(
      implicit paramsAsArgs: ParamsAsArgs[I]): paramsAsArgs.FN[Request[Either[E, O], Nothing]] = {
    paramsAsArgs.toFn(params => {
      val baseReq = sttp
        .response(ignore)
        .mapResponse(Right(_): Either[Any, Any])

      val (uri, req) = setInputParams(e.input.inputs, params, paramsAsArgs, 0, baseUri, baseReq)

      var req2 = req.copy[Id, Either[Any, Any], Nothing](method = com.softwaremill.sttp.Method(e.method.m), uri = uri)

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

  private def setInputParams[I](inputs: Vector[EndpointInput[_]],
                                params: I,
                                paramsAsArgs: ParamsAsArgs[I],
                                paramIndex: Int,
                                uri: Uri,
                                req: PartialRequest[Either[Any, Any], Nothing]): (Uri, PartialRequest[Either[Any, Any], Nothing]) = {
    inputs match {
      case Vector() => (uri, req)
      case EndpointInput.PathSegment(p) +: tail =>
        setInputParams(tail, params, paramsAsArgs, paramIndex, uri.copy(path = uri.path :+ p), req)
      case EndpointInput.PathCapture(m, _, _, _) +: tail =>
        val v = m.asInstanceOf[RequiredTextTypeMapper[Any]].toString(paramsAsArgs.paramAt(params, paramIndex): Any)
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri.copy(path = uri.path :+ v), req)
      case EndpointInput.Query(name, m, _, _) +: tail =>
        val uri2 = m
          .asInstanceOf[TextTypeMapper[Any]]
          .toOptionalString(paramsAsArgs.paramAt(params, paramIndex))
          .map(v => uri.param(name, v))
          .getOrElse(uri)
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri2, req)
      case EndpointIO.Body(m, _, _) +: tail =>
        val req2 = m
          .asInstanceOf[TypeMapper[Any, _]]
          .toOptionalString(paramsAsArgs.paramAt(params, paramIndex))
          .map(v => req.body(v))
          .getOrElse(req)
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri, req2)
      case EndpointIO.Header(name, m, _, _) +: tail =>
        val req2 = m
          .asInstanceOf[TypeMapper[Any, _]]
          .toOptionalString(paramsAsArgs.paramAt(params, paramIndex))
          .map(v => req.header(name, v))
          .getOrElse(req)
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri, req2)
      case EndpointInput.Mapped(wrapped, _, g, wrappedParamsAsArgs) +: tail =>
        val (uri2, req2) = setInputParams(
          wrapped.asVectorOfSingle,
          g(paramsAsArgs.paramAt(params, paramIndex)),
          wrappedParamsAsArgs,
          0,
          uri,
          req
        )

        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri2, req2)
    }
  }
}
