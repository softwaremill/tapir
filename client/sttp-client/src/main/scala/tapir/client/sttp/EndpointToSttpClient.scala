package tapir.client.sttp

import com.softwaremill.sttp._
import tapir.TypeMapper.{RequiredTextTypeMapper, TextTypeMapper}
import tapir.internal.SeqToParams
import tapir.typelevel.ParamsAsArgs
import tapir._

object EndpointToSttpClient {
  // don't look. The code is really, really ugly.

  def toSttpRequest[I, E, O, S](e: Endpoint[I, E, O], baseUri: Uri)(
      implicit paramsAsArgs: ParamsAsArgs[I]): paramsAsArgs.FN[Request[Either[E, O], Nothing]] = {
    paramsAsArgs.toFn(params => {
      val baseReq = sttp
        .response(ignore)
        .mapResponse(Right(_): Either[Any, Any])

      val (uri, req) = setInputParams(e.input.asVectorOfSingle, params, paramsAsArgs, 0, baseUri, baseReq)

      var req2 = req.copy[Id, Either[Any, Any], Nothing](method = com.softwaremill.sttp.Method(e.method.m), uri = uri)

      if (e.output.asVectorOfSingle.nonEmpty || e.errorOutput.asVectorOfSingle.nonEmpty) {
        val baseResponseAs: ResponseAs[String, Nothing] = if (hasBody(e.output) || hasBody(e.errorOutput)) asString else ignore.map(_ => "")
        val responseAs = baseResponseAs.mapWithMetadata { (body, meta) =>
          val outputs = if (meta.isSuccess) e.output.asVectorOfSingle else e.errorOutput.asVectorOfSingle
          val params = getOutputParams(outputs, body, meta)
          if (meta.isSuccess) Right(params) else Left(params)
        }

        req2 = req2.response(responseAs.asInstanceOf[ResponseAs[Either[Any, Any], Nothing]]).parseResponseIf(_ => true)
      }

      req2.asInstanceOf[Request[Either[E, O], Nothing]]
    })
  }

  private def getOutputParams(outputs: Vector[EndpointIO.Single[_]], body: String, meta: ResponseMetadata): Any = {
    val values = outputs
      .map {
        case EndpointIO.Body(m, _, _) =>
          val so = if (m.isOptional && body == "") None else Some(body)
          m.fromOptionalString(so).getOrThrow(InvalidOutput)

        case EndpointIO.Header(name, m, _, _) =>
          m.fromOptionalString(meta.header(name)).getOrThrow(InvalidOutput)

        case EndpointIO.Mapped(wrapped, f, _, _) =>
          f.asInstanceOf[Any => Any].apply(getOutputParams(wrapped.asVectorOfSingle, body, meta))
      }

    SeqToParams(values)
  }

  private def setInputParams[I](inputs: Vector[EndpointInput.Single[_]],
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

  private def hasBody[O](io: EndpointIO[O]): Boolean = {
    io match {
      case _: EndpointIO.Body[_, _]            => true
      case EndpointIO.Multiple(ios)            => ios.exists(hasBody(_))
      case EndpointIO.Mapped(wrapped, _, _, _) => hasBody(wrapped)
      case _                                   => false
    }
  }
}
