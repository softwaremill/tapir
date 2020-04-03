package sttp.tapir.client.sttp

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

import sttp.client._
import sttp.model.Uri.PathSegment
import sttp.model.{HeaderNames, Method, Part, Uri}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir._
import sttp.tapir.internal._

class EndpointToSttpClient(clientOptions: SttpClientOptions) {
  def toSttpRequestUnsafe[I, E, O, S](e: Endpoint[I, E, O, S], baseUri: Uri): I => Request[Either[E, O], S] = { params =>
    toSttpRequest(e, baseUri)(params).mapResponse(getOrThrow)
  }

  def toSttpRequest[S, O, E, I](e: Endpoint[I, E, O, S], baseUri: Uri): I => Request[DecodeResult[Either[E, O]], S] = { params =>
    val (uri, req1) =
      setInputParams(
        e.input.asVectorOfSingleInputs,
        paramsTupleToParams(params),
        0,
        baseUri,
        basicRequest.asInstanceOf[PartialAnyRequest]
      )

    val req2 = req1.copy[Identity, Any, Any](method = sttp.model.Method(e.input.method.getOrElse(Method.GET).method), uri = uri)

    val responseAs = fromMetadata { meta =>
      val output = if (meta.isSuccess) e.output else e.errorOutput
      if (output == EndpointOutput.Void()) {
        throw new IllegalStateException(s"Got response: $meta, cannot map to a void output of: $e.")
      }

      responseAsFromOutputs(meta, output)
    }.mapWithMetadata { (body, meta) =>
        val output = if (meta.isSuccess) e.output else e.errorOutput
        val params = getOutputParams(output.asVectorOfSingleOutputs, body, meta)
        params.map(p => if (meta.isSuccess) Right(p) else Left(p))
      }
      .map {
        case DecodeResult.Error(o, e) =>
          DecodeResult.Error(o, new IllegalArgumentException(s"Cannot decode from $o of request ${req2.method} ${req2.uri}", e))
        case other => other
      }

    req2.response(responseAs).asInstanceOf[Request[DecodeResult[Either[E, O]], S]]
  }

  private def getOutputParams(outputs: Vector[EndpointOutput.Single[_]], body: Any, meta: ResponseMetadata): DecodeResult[Any] = {
    val partialDecodeResults = outputs
      .flatMap {
        case EndpointIO.Body(_, codec, _) =>
          Some(codec.decode(body))

        case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(codec, _, _)) =>
          Some(codec.decode(body))

        case EndpointIO.Header(name, codec, _) =>
          Some(codec.decode(meta.headers(name).toList))

        case EndpointIO.Headers(codec, _) =>
          Some(codec.decode(meta.headers.toList))

        case EndpointIO.MappedTuple(tuple, codec) =>
          Some(getOutputParams(tuple.asVectorOfSingleOutputs, body, meta).flatMap { outputParams => codec.decode(outputParams) })

        case EndpointOutput.StatusCode(_, codec, _) =>
          Some(codec.decode(meta.code))

        case EndpointOutput.FixedStatusCode(_, codec, _) =>
          if (codec.hIsUnit) None else Some(codec.decode(()))

        case EndpointIO.FixedHeader(_, codec, _) =>
          if (codec.hIsUnit) None else Some(codec.decode(()))

        case EndpointOutput.OneOf(mappings, codec) =>
          mappings
            .find(mapping => mapping.statusCode.isEmpty || mapping.statusCode.contains(meta.code)) match {
            case Some(mapping) =>
              Some(getOutputParams(mapping.output.asVectorOfSingleOutputs, body, meta).flatMap(codec.decode))
            case None =>
              Some(
                DecodeResult.Error(
                  meta.statusText,
                  new IllegalArgumentException(s"Cannot find mapping for status code ${meta.code} in outputs $outputs")
                )
              )
          }
        case EndpointOutput.MappedTuple(tuple, codec) =>
          Some(getOutputParams(tuple.asVectorOfSingleOutputs, body, meta).flatMap(codec.decode))
      }

    DecodeResult.sequence(partialDecodeResults).map(SeqToParams(_))
  }

  private type PartialAnyRequest = PartialRequest[Any, Any]

  private def paramsTupleToParams[I](params: I): Vector[Any] = ParamsToSeq(params).toVector

  private def setInputParams[I](
      inputs: Vector[EndpointInput.Single[_]],
      params: Vector[Any],
      paramIndex: Int,
      uri: Uri,
      req: PartialAnyRequest
  ): (Uri, PartialAnyRequest) = {
    def handleMapped[II, T](
        tuple: EndpointInput[II],
        codec: Mapping[T, II],
        tail: Vector[EndpointInput.Single[_]]
    ): (Uri, PartialAnyRequest) = {
      val (uri2, req2) = setInputParams(
        tuple.asVectorOfSingleInputs,
        paramsTupleToParams(codec.encode(params(paramIndex).asInstanceOf[II])),
        0,
        uri,
        req
      )

      setInputParams(tail, params, paramIndex + 1, uri2, req2)
    }

    inputs match {
      case Vector() => (uri, req)
      case EndpointInput.FixedMethod(_, codec, _) +: tail =>
        setInputParams(tail, params, if (codec.hIsUnit) paramIndex else paramIndex + 1, uri, req)
      case EndpointInput.FixedPath(p, codec, _) +: tail =>
        setInputParams(
          tail,
          params,
          if (codec.hIsUnit) paramIndex else paramIndex + 1,
          uri.copy(pathSegments = uri.pathSegments :+ PathSegment(p)),
          req
        )
      case EndpointInput.PathCapture(_, codec, _) +: tail =>
        val v = codec.asInstanceOf[PlainCodec[Any]].encode(params(paramIndex): Any)
        setInputParams(tail, params, paramIndex + 1, uri.copy(pathSegments = uri.pathSegments :+ PathSegment(v)), req)
      case EndpointInput.PathsCapture(codec, _) +: tail =>
        val ps = codec.encode(params(paramIndex).asInstanceOf[List[String]])
        setInputParams(tail, params, paramIndex + 1, uri.copy(pathSegments = uri.pathSegments ++ ps.map(PathSegment(_))), req)
      case EndpointInput.Query(name, codec, _) +: tail =>
        val uri2 = codec
          .encode(params(paramIndex))
          .foldLeft(uri) { case (u, v) => u.param(name, v) }
        setInputParams(tail, params, paramIndex + 1, uri2, req)
      case EndpointInput.Cookie(name, codec, _) +: tail =>
        val req2 = codec
          .encode(params(paramIndex))
          .foldLeft(req) { case (r, v) => r.cookie(name, v) }
        setInputParams(tail, params, paramIndex + 1, uri, req2)
      case EndpointInput.QueryParams(codec, _) +: tail =>
        val mqp = codec.encode(params(paramIndex))
        val uri2 = uri.params(mqp.toSeq: _*)
        setInputParams(tail, params, paramIndex + 1, uri2, req)
      case EndpointIO.Body(bodyType, codec, _) +: tail =>
        val req2 = setBody(params(paramIndex), bodyType, codec, req)
        setInputParams(tail, params, paramIndex + 1, uri, req2)
      case EndpointIO.StreamBodyWrapper(_) +: tail =>
        val req2 = req.streamBody(params(paramIndex))
        setInputParams(tail, params, paramIndex + 1, uri, req2)
      case EndpointIO.Header(name, codec, _) +: tail =>
        val req2 = codec
          .encode(params(paramIndex))
          .foldLeft(req) { case (r, v) => r.header(name, v) }
        setInputParams(tail, params, paramIndex + 1, uri, req2)
      case EndpointIO.Headers(codec, _) +: tail =>
        val headers = codec.encode(params(paramIndex))
        val req2 = headers.foldLeft(req) {
          case (r, h) =>
            val replaceExisting = HeaderNames.ContentType.equalsIgnoreCase(h.name) || HeaderNames.ContentLength.equalsIgnoreCase(h.name)
            r.header(h, replaceExisting)
        }
        setInputParams(tail, params, paramIndex + 1, uri, req2)
      case EndpointIO.FixedHeader(h, codec, _) +: tail =>
        val req2 = req.header(h)
        setInputParams(tail, params, if (codec.hIsUnit) paramIndex else paramIndex + 1, uri, req2)
      case EndpointInput.ExtractFromRequest(_, _) +: tail =>
        // ignoring
        setInputParams(tail, params, paramIndex + 1, uri, req)
      case (a: EndpointInput.Auth[_]) +: tail =>
        setInputParams(a.input +: tail, params, paramIndex, uri, req)
      case EndpointInput.MappedTuple(tuple, codec) +: tail =>
        handleMapped(tuple, codec, tail)
      case EndpointIO.MappedTuple(tuple, codec) +: tail =>
        handleMapped(tuple, codec, tail)
    }
  }

  private def setBody[R, T, CF <: CodecFormat](
      v: T,
      bodyType: RawBodyType[R],
      codec: Codec[R, T, CF],
      req: PartialAnyRequest
  ): PartialAnyRequest = {
    val encoded = codec.encode(v)
    val req2 = bodyType match {
      case RawBodyType.StringBody(charset) => req.body(encoded, charset.name())
      case RawBodyType.ByteArrayBody       => req.body(encoded)
      case RawBodyType.ByteBufferBody      => req.body(encoded)
      case RawBodyType.InputStreamBody     => req.body(encoded)
      case RawBodyType.FileBody            => req.body(encoded)
      case m: RawBodyType.MultipartBody =>
        val parts: Seq[Part[BasicRequestBody]] = (encoded: Seq[RawPart]).flatMap { p =>
          m.partType(p.name).map { partType =>
            // copying the name & body
            val sttpPart1 = partToSttpPart(p.asInstanceOf[Part[Any]], partType.asInstanceOf[RawBodyType[Any]])
            // copying the headers; overwriting the content type if it is specified
            val sttpPart2 = p.headers.foldLeft(sttpPart1) { (part, header) =>
              part.header(header, replaceExisting = header.is(HeaderNames.ContentType))
            }
            // copying the other disposition params (e.g. filename)
            p.otherDispositionParams.foldLeft(sttpPart2) { case (part, (k, v)) => part.dispositionParam(k, v) }
          }
        }

        req.multipartBody(parts.toList)
    }

    req2.contentType(codec.format.mediaType)
  }

  private def partToSttpPart[R](p: Part[R], bodyType: RawBodyType[R]): Part[BasicRequestBody] = bodyType match {
    case RawBodyType.StringBody(charset) => multipart(p.name, p.body, charset.toString)
    case RawBodyType.ByteArrayBody       => multipart(p.name, p.body)
    case RawBodyType.ByteBufferBody      => multipart(p.name, p.body)
    case RawBodyType.InputStreamBody     => multipart(p.name, p.body)
    case RawBodyType.FileBody            => multipartFile(p.name, p.body)
    case RawBodyType.MultipartBody(_, _) => throw new IllegalArgumentException("Nested multipart bodies aren't supported")
  }

  private def responseAsFromOutputs(meta: ResponseMetadata, out: EndpointOutput[_]): ResponseAs[Any, Any] = {
    if (bodyIsStream(out)) asStreamAlways[Any]
    else {
      out.bodyType
        .map {
          case RawBodyType.StringBody(charset) => asStringAlways(charset.name())
          case RawBodyType.ByteArrayBody       => asByteArrayAlways
          case RawBodyType.ByteBufferBody      => asByteArrayAlways.map(ByteBuffer.wrap)
          case RawBodyType.InputStreamBody     => asByteArrayAlways.map(new ByteArrayInputStream(_))
          case RawBodyType.FileBody            => asFileAlways(clientOptions.createFile(meta))
          case RawBodyType.MultipartBody(_, _) => throw new IllegalArgumentException("Multipart bodies aren't supported in responses")
        }
        .getOrElse(ignore)
    }.asInstanceOf[ResponseAs[Any, Any]]
  }

  private def bodyIsStream[I](out: EndpointOutput[I]): Boolean = {
    out match {
      case _: EndpointIO.StreamBodyWrapper[_, _]  => true
      case EndpointIO.Tuple(inputs)               => inputs.exists(i => bodyIsStream(i))
      case EndpointOutput.Tuple(inputs)           => inputs.exists(i => bodyIsStream(i))
      case EndpointIO.MappedTuple(wrapped, _)     => bodyIsStream(wrapped)
      case EndpointOutput.MappedTuple(wrapped, _) => bodyIsStream(wrapped)
      case _                                      => false
    }
  }

  private def getOrThrow[T](dr: DecodeResult[T]): T = dr match {
    case DecodeResult.Value(v)    => v
    case DecodeResult.Error(_, e) => throw e
    case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
  }

}
