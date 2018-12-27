package tapir.client.sttp

import com.softwaremill.sttp._
import tapir.GeneralCodec.PlainCodec
import tapir.internal.SeqToParams
import tapir.typelevel.ParamsAsArgs
import tapir._

object EndpointToSttpClient {
  // don't look. The code is really, really ugly.

  def toSttpRequest[I, E, O](e: Endpoint[I, E, O], baseUri: Uri)(
      implicit paramsAsArgs: ParamsAsArgs[I]): paramsAsArgs.FN[Request[Either[E, O], Nothing]] = {
    paramsAsArgs.toFn(params => {
      val baseReq = sttp
        .response(ignore)
        .mapResponse(Right(_): Either[Any, Any])

      val (uri, req) = setInputParams(e.input.asVectorOfSingle, params, paramsAsArgs, 0, baseUri, baseReq)

      var req2 = req.copy[Id, Either[Any, Any], Nothing](method = com.softwaremill.sttp.Method(e.method.m), uri = uri)

      if (e.output.asVectorOfSingle.nonEmpty || e.errorOutput.asVectorOfSingle.nonEmpty) {
        // by default, reading the body as specified by the output, and optionally adjusting to the error output
        // if there's no body in the output, reading the body as specified by the error output
        // otherwise, ignoring
        val outputBodyType = bodyType(e.output)
        val errorOutputBodyType = bodyType(e.errorOutput)
        val baseResponseAs = outputBodyType
          .orElse(errorOutputBodyType)
          .map {
            case StringValueType(charset) => asString(charset.name())
            case ByteArrayValueType       => asByteArray
          }
          .getOrElse(ignore)

        val responseAs = baseResponseAs.mapWithMetadata {
          (body, meta) =>
            val outputs = if (meta.isSuccess) e.output.asVectorOfSingle else e.errorOutput.asVectorOfSingle

            // the body type of the success output takes priority; that's why it might not match
            val adjustedBody =
              if (meta.isSuccess || outputBodyType.isEmpty || outputBodyType == errorOutputBodyType) body
              else errorOutputBodyType.map(adjustBody(body, _)).getOrElse(body)

            val params = getOutputParams(outputs, adjustedBody, meta)
            if (meta.isSuccess) Right(params) else Left(params)
        }

        req2 = req2.response(responseAs.asInstanceOf[ResponseAs[Either[Any, Any], Nothing]]).parseResponseIf(_ => true)
      }

      req2.asInstanceOf[Request[Either[E, O], Nothing]]
    })
  }

  private def getOutputParams(outputs: Vector[EndpointIO.Single[_]], body: Any, meta: ResponseMetadata): Any = {
    val values = outputs
      .map {
        case EndpointIO.Body(codec, _, _) =>
          val so = if (codec.isOptional && body == "") None else Some(body)
          codec.decodeOptional(so).getOrThrow(InvalidOutput)

        case EndpointIO.Header(name, codec, _, _) =>
          codec.decodeOptional(meta.header(name)).getOrThrow(InvalidOutput)

        case EndpointIO.Mapped(wrapped, f, _, _) =>
          f.asInstanceOf[Any => Any].apply(getOutputParams(wrapped.asVectorOfSingle, body, meta))
      }

    SeqToParams(values)
  }

  private type PartialAnyRequest = PartialRequest[Either[Any, Any], Nothing]

  private def setInputParams[I](inputs: Vector[EndpointInput.Single[_]],
                                params: I,
                                paramsAsArgs: ParamsAsArgs[I],
                                paramIndex: Int,
                                uri: Uri,
                                req: PartialAnyRequest): (Uri, PartialAnyRequest) = {

    def handleMapped[II, T](wrapped: EndpointInput[II],
                            g: T => II,
                            wrappedParamsAsArgs: ParamsAsArgs[II],
                            tail: Vector[EndpointInput.Single[_]]): (Uri, PartialAnyRequest) = {
      val (uri2, req2) = setInputParams(
        wrapped.asVectorOfSingle,
        g(paramsAsArgs.paramAt(params, paramIndex).asInstanceOf[T]),
        wrappedParamsAsArgs,
        0,
        uri,
        req
      )

      setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri2, req2)
    }

    inputs match {
      case Vector() => (uri, req)
      case EndpointInput.PathSegment(p) +: tail =>
        setInputParams(tail, params, paramsAsArgs, paramIndex, uri.copy(path = uri.path :+ p), req)
      case EndpointInput.PathCapture(codec, _, _, _) +: tail =>
        val v = codec.asInstanceOf[PlainCodec[Any]].encode(paramsAsArgs.paramAt(params, paramIndex): Any)
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri.copy(path = uri.path :+ v), req)
      case EndpointInput.Query(name, codec, _, _) +: tail =>
        val uri2 = codec
          .encodeOptional(paramsAsArgs.paramAt(params, paramIndex))
          .map(v => uri.param(name, v))
          .getOrElse(uri)
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri2, req)
      case EndpointIO.Body(codec, _, _) +: tail =>
        val req2 = setBody(paramsAsArgs.paramAt(params, paramIndex), codec, req)
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri, req2)
      case EndpointIO.Header(name, codec, _, _) +: tail =>
        val req2 = codec
          .encodeOptional(paramsAsArgs.paramAt(params, paramIndex))
          .map(v => req.header(name, v))
          .getOrElse(req)
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri, req2)
      case EndpointInput.Mapped(wrapped, _, g, wrappedParamsAsArgs) +: tail =>
        handleMapped(wrapped, g, wrappedParamsAsArgs, tail)
      case EndpointIO.Mapped(wrapped, _, g, wrappedParamsAsArgs) +: tail =>
        handleMapped(wrapped, g, wrappedParamsAsArgs, tail)
    }
  }

  private def setBody[T, M <: MediaType, R](v: T, codec: GeneralCodec[T, M, R], req: PartialAnyRequest): PartialAnyRequest = {
    codec
      .encodeOptional(v)
      .map(t => codec.rawValueType.fold(t)((s, charset) => req.body(s, charset.name()), req.body(_)))
      .getOrElse(req)
  }

  private def bodyType[I](in: EndpointInput[I]): Option[RawValueType[_]] = {
    in match {
      case b: EndpointIO.Body[_, _, _]         => Some(b.codec.rawValueType)
      case EndpointIO.Multiple(inputs)         => inputs.flatMap(bodyType(_)).headOption
      case EndpointIO.Mapped(wrapped, _, _, _) => bodyType(wrapped)
      case _                                   => None
    }
  }

  // TODO: rework
  private def adjustBody(b: Any, bodyType: RawValueType[_]): Any = {
    val asByteArray = b match {
      case s: String      => s.getBytes
      case b: Array[Byte] => b
    }

    bodyType match {
      case StringValueType(charset) => new String(asByteArray, charset)
      case ByteArrayValueType       => asByteArray
    }
  }
}
