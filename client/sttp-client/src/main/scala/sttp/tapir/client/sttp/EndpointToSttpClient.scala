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
        e.input,
        params,
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
        val params = getOutputParams(output, body, meta)
        params.map(p => if (meta.isSuccess) Right(p) else Left(p))
      }
      .map {
        case DecodeResult.Error(o, e) =>
          DecodeResult.Error(o, new IllegalArgumentException(s"Cannot decode from $o of request ${req2.method} ${req2.uri}", e))
        case other => other
      }

    req2.response(responseAs).asInstanceOf[Request[DecodeResult[Either[E, O]], S]]
  }

  private def getOutputParams(output: EndpointOutput[_], body: Any, meta: ResponseMetadata): DecodeResult[Any] = output match {
    case EndpointIO.Body(_, codec, _)                                        => codec.decode(body)
    case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(codec, _, _)) => codec.decode(body)
    case EndpointIO.Header(name, codec, _)                                   => codec.decode(meta.headers(name).toList)
    case EndpointIO.Headers(codec, _)                                        => codec.decode(meta.headers.toList)
    case EndpointOutput.StatusCode(_, codec, _)                              => codec.decode(meta.code)
    case EndpointOutput.FixedStatusCode(_, codec, _)                         => codec.decode(())
    case EndpointIO.FixedHeader(_, codec, _)                                 => codec.decode(())
    case EndpointOutput.OneOf(mappings, codec) =>
      mappings
        .find(mapping => mapping.statusCode.isEmpty || mapping.statusCode.contains(meta.code)) match {
        case Some(mapping) =>
          getOutputParams(mapping.output, body, meta).flatMap(codec.decode)
        case None =>
          DecodeResult.Error(
            meta.statusText,
            new IllegalArgumentException(s"Cannot find mapping for status code ${meta.code} in outputs $output")
          )
      }

    case EndpointIO.MappedMultiple(tuple, codec)     => getOutputParams(tuple, body, meta).flatMap(codec.decode)
    case EndpointOutput.MappedMultiple(tuple, codec) => getOutputParams(tuple, body, meta).flatMap(codec.decode)

    case EndpointOutput.Void() => DecodeResult.Error("", new IllegalArgumentException("Cannot convert a void output to a value!"))

    case EndpointOutput.Multiple(outputs, mkParams, _) => handleOutputTuple(outputs, mkParams, body, meta)
    case EndpointIO.Multiple(outputs, mkParams, _)     => handleOutputTuple(outputs, mkParams, body, meta)
  }

  private def handleOutputTuple(
      outputs: Vector[EndpointOutput[_]],
      mkParams: MkParams,
      body: Any,
      meta: ResponseMetadata
  ): DecodeResult[Any] =
    DecodeResult.sequence(outputs.map(getOutputParams(_, body, meta))).map(vs => mkParams(vs.toVector))

  private type PartialAnyRequest = PartialRequest[Any, Any]

  @scala.annotation.tailrec
  private def setInputParams[I](
      input: EndpointInput[I],
      value: I,
      uri: Uri,
      req: PartialAnyRequest
  ): (Uri, PartialAnyRequest) = {
    input match {
      case EndpointInput.FixedMethod(_, _, _) => (uri, req)
      case EndpointInput.FixedPath(p, _, _)   => (uri.copy(pathSegments = uri.pathSegments :+ PathSegment(p)), req)
      case EndpointInput.PathCapture(_, codec, _) =>
        val v = codec.asInstanceOf[PlainCodec[Any]].encode(value: Any)
        (uri.copy(pathSegments = uri.pathSegments :+ PathSegment(v)), req)
      case EndpointInput.PathsCapture(codec, _) =>
        val ps = codec.encode(value)
        (uri.copy(pathSegments = uri.pathSegments ++ ps.map(PathSegment(_))), req)
      case EndpointInput.Query(name, codec, _) =>
        val uri2 = codec.encode(value).foldLeft(uri) { case (u, v) => u.param(name, v) }
        (uri2, req)
      case EndpointInput.Cookie(name, codec, _) =>
        val req2 = codec.encode(value).foldLeft(req) { case (r, v) => r.cookie(name, v) }
        (uri, req2)
      case EndpointInput.QueryParams(codec, _) =>
        val mqp = codec.encode(value)
        val uri2 = uri.params(mqp.toSeq: _*)
        (uri2, req)
      case EndpointIO.Body(bodyType, codec, _) =>
        val req2 = setBody(value, bodyType, codec, req)
        (uri, req2)
      case EndpointIO.StreamBodyWrapper(_) =>
        val req2 = req.streamBody(value)
        (uri, req2)
      case EndpointIO.Header(name, codec, _) =>
        val req2 = codec
          .encode(value)
          .foldLeft(req) { case (r, v) => r.header(name, v) }
        (uri, req2)
      case EndpointIO.Headers(codec, _) =>
        val headers = codec.encode(value)
        val req2 = headers.foldLeft(req) {
          case (r, h) =>
            val replaceExisting = HeaderNames.ContentType.equalsIgnoreCase(h.name) || HeaderNames.ContentLength.equalsIgnoreCase(h.name)
            r.header(h, replaceExisting)
        }
        (uri, req2)
      case EndpointIO.FixedHeader(h, _, _) =>
        val req2 = req.header(h)
        (uri, req2)
      case EndpointInput.ExtractFromRequest(_, _) =>
        // ignoring
        (uri, req)
      case a: EndpointInput.Auth[_]                    => setInputParams(a.input, value, uri, req)
      case EndpointInput.Multiple(inputs, _, unParams) => handleInputTuple(inputs, value, unParams, uri, req)
      case EndpointIO.Multiple(inputs, _, unParams)    => handleInputTuple(inputs, value, unParams, uri, req)
      case EndpointInput.MappedMultiple(tuple, codec)  => handleMapped(tuple, codec.asInstanceOf[Mapping[Any, Any]], value, uri, req)
      case EndpointIO.MappedMultiple(tuple, codec)     => handleMapped(tuple, codec.asInstanceOf[Mapping[Any, Any]], value, uri, req)
    }
  }

  def handleInputTuple(
      inputs: Vector[EndpointInput[_]],
      value: Any,
      unParams: UnParams,
      uri: Uri,
      req: PartialAnyRequest
  ): (Uri, PartialAnyRequest) = {
    val inputsValues = unParams(value)
    if ((inputs.isEmpty && value != (())) || (inputs.nonEmpty && inputsValues.length != inputs.length))
      throw new IllegalArgumentException(s"Mismatch between input value: $value, and inputs: $inputs")

    inputs.zip(inputsValues).foldLeft((uri, req)) {
      case ((uri2, req2), (input, inputValue)) =>
        setInputParams(input.asInstanceOf[EndpointInput[Any]], inputValue, uri2, req2)
    }
  }

  private def handleMapped[II, T](
      tuple: EndpointInput[II],
      codec: Mapping[T, II],
      value: Any,
      uri: Uri,
      req: PartialAnyRequest
  ): (Uri, PartialAnyRequest) = {
    setInputParams(tuple.asInstanceOf[EndpointInput[Any]], codec.encode(value.asInstanceOf[II]), uri, req)
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
      case _: EndpointIO.StreamBodyWrapper[_, _]     => true
      case EndpointIO.Multiple(inputs, _, _)         => inputs.exists(i => bodyIsStream(i))
      case EndpointOutput.Multiple(inputs, _, _)     => inputs.exists(i => bodyIsStream(i))
      case EndpointIO.MappedMultiple(wrapped, _)     => bodyIsStream(wrapped)
      case EndpointOutput.MappedMultiple(wrapped, _) => bodyIsStream(wrapped)
      case _                                         => false
    }
  }

  private def getOrThrow[T](dr: DecodeResult[T]): T = dr match {
    case DecodeResult.Value(v)    => v
    case DecodeResult.Error(_, e) => throw e
    case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
  }

}
