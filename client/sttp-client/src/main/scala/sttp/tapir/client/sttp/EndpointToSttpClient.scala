package sttp.tapir.client.sttp

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

import sttp.client._
import sttp.model.Uri.PathSegment
import sttp.model.{HeaderNames, Method, MultiQueryParams, Part, Uri}
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

    req2.response(responseAs).asInstanceOf[Request[DecodeResult[Either[E, O]], S]]
  }

  private def getOutputParams(outputs: Vector[EndpointOutput.Single[_]], body: Any, meta: ResponseMetadata): DecodeResult[Any] = {
    val partialDecodeResults = outputs
      .flatMap {
        case EndpointIO.Body(codec, _) =>
          val so = if (codec.meta.schema.isOptional && body == "") None else Some(body)
          Some(codec.rawDecode(so))

        case EndpointIO.StreamBodyWrapper(_) =>
          Some(DecodeResult.Value(body))

        case EndpointIO.Header(name, codec, _) =>
          Some(codec.rawDecode(meta.headers(name).toList))

        case EndpointIO.Headers(_) =>
          Some(DecodeResult.Value(meta.headers.map(h => (h.name, h.value))))

        case EndpointIO.Mapped(wrapped, f, _) =>
          val outputParams = getOutputParams(wrapped.asVectorOfSingleOutputs, body, meta)
          Some(outputParams.map(f.asInstanceOf[Any => Any].apply))

        case EndpointOutput.StatusCode(_) =>
          Some(DecodeResult.Value(meta.code))

        case EndpointOutput.FixedStatusCode(_, _) =>
          None
        case EndpointIO.FixedHeader(_, _, _) =>
          None

        case EndpointOutput.OneOf(mappings) =>
          val mapping = mappings
            .find(mapping => mapping.statusCode.isEmpty || mapping.statusCode.contains(meta.code))
            .getOrElse(throw new IllegalArgumentException(s"Cannot find mapping for status code ${meta.code} in outputs $outputs"))
          Some(getOutputParams(mapping.output.asVectorOfSingleOutputs, body, meta))

        case EndpointOutput.Mapped(wrapped, f, _) =>
          val outputParams = getOutputParams(wrapped.asVectorOfSingleOutputs, body, meta)
          Some(outputParams.map(f.asInstanceOf[Any => Any].apply))
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
        wrapped: EndpointInput[II],
        g: T => II,
        tail: Vector[EndpointInput.Single[_]]
    ): (Uri, PartialAnyRequest) = {
      val (uri2, req2) = setInputParams(
        wrapped.asVectorOfSingleInputs,
        paramsTupleToParams(g(params(paramIndex).asInstanceOf[T])),
        0,
        uri,
        req
      )

      setInputParams(tail, params, paramIndex + 1, uri2, req2)
    }

    inputs match {
      case Vector() => (uri, req)
      case EndpointInput.FixedMethod(_) +: tail =>
        setInputParams(tail, params, paramIndex, uri, req)
      case EndpointInput.FixedPath(p) +: tail =>
        setInputParams(tail, params, paramIndex, uri.copy(pathSegments = uri.pathSegments :+ PathSegment(p)), req)
      case EndpointInput.PathCapture(codec, _, _) +: tail =>
        val v = codec.asInstanceOf[PlainCodec[Any]].encode(params(paramIndex): Any)
        setInputParams(tail, params, paramIndex + 1, uri.copy(pathSegments = uri.pathSegments :+ PathSegment(v)), req)
      case EndpointInput.PathsCapture(_) +: tail =>
        val ps = params(paramIndex).asInstanceOf[Seq[String]]
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
      case EndpointInput.QueryParams(_) +: tail =>
        val mqp = params(paramIndex).asInstanceOf[MultiQueryParams]
        val uri2 = uri.params(mqp.toSeq: _*)
        setInputParams(tail, params, paramIndex + 1, uri2, req)
      case EndpointIO.Body(codec, _) +: tail =>
        val req2 = setBody(params(paramIndex), codec, req)
        setInputParams(tail, params, paramIndex + 1, uri, req2)
      case EndpointIO.StreamBodyWrapper(_) +: tail =>
        val req2 = req.streamBody(params(paramIndex))
        setInputParams(tail, params, paramIndex + 1, uri, req2)
      case EndpointIO.Header(name, codec, _) +: tail =>
        val req2 = codec
          .encode(params(paramIndex))
          .foldLeft(req) { case (r, v) => r.header(name, v) }
        setInputParams(tail, params, paramIndex + 1, uri, req2)
      case EndpointIO.Headers(_) +: tail =>
        val headers = params(paramIndex).asInstanceOf[Seq[(String, String)]]
        val req2 = headers.foldLeft(req) {
          case (r, (k, v)) =>
            val replaceExisting = HeaderNames.ContentType.equalsIgnoreCase(k) || HeaderNames.ContentLength.equalsIgnoreCase(k)
            r.header(k, v, replaceExisting)
        }
        setInputParams(tail, params, paramIndex + 1, uri, req2)
      case EndpointIO.FixedHeader(name, value, _) +: tail =>
        val req2 = Seq(value)
          .foldLeft(req) { case (r, v) => r.header(name, v) }
        setInputParams(tail, params, paramIndex, uri, req2)
      case EndpointInput.ExtractFromRequest(_) +: tail =>
        // ignoring
        setInputParams(tail, params, paramIndex + 1, uri, req)
      case (a: EndpointInput.Auth[_]) +: tail =>
        setInputParams(a.input +: tail, params, paramIndex, uri, req)
      case EndpointInput.Mapped(wrapped, _, g) +: tail =>
        handleMapped(wrapped, g, tail)
      case EndpointIO.Mapped(wrapped, _, g) +: tail =>
        handleMapped(wrapped, g, tail)
    }
  }

  private def setBody[T, CF <: CodecFormat, R](v: T, codec: CodecForOptional[T, CF, R], req: PartialAnyRequest): PartialAnyRequest = {
    codec
      .encode(v)
      .map { t =>
        val req2 = codec.meta.rawValueType match {
          case StringValueType(charset) => req.body(t, charset.name())
          case ByteArrayValueType       => req.body(t)
          case ByteBufferValueType      => req.body(t)
          case InputStreamValueType     => req.body(t)
          case FileValueType            => req.body(t)
          case mvt: MultipartValueType =>
            val parts: Seq[Part[BasicRequestBody]] = (t: Seq[RawPart]).flatMap { p =>
              mvt.partCodecMeta(p.name).map { partCodecMeta =>
                // copying the name & body - this also sets a default content type
                val sttpPart1 = partToSttpPart(p.asInstanceOf[Part[Any]], partCodecMeta.asInstanceOf[CodecMeta[_, _, Any]])
                  .contentType(partCodecMeta.format.mediaType)
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

        req2.contentType(codec.meta.format.mediaType)
      }
      .getOrElse(req)
  }

  private def partToSttpPart[R](p: Part[R], codecMeta: CodecMeta[_, _, R]): Part[BasicRequestBody] = codecMeta.rawValueType match {
    case StringValueType(charset) => multipart(p.name, p.body, charset.toString)
    case ByteArrayValueType       => multipart(p.name, p.body)
    case ByteBufferValueType      => multipart(p.name, p.body)
    case InputStreamValueType     => multipart(p.name, p.body)
    case FileValueType            => multipartFile(p.name, p.body)
    case MultipartValueType(_, _) => throw new IllegalArgumentException("Nested multipart bodies aren't supported")
  }

  private def responseAsFromOutputs(meta: ResponseMetadata, out: EndpointOutput[_]): ResponseAs[Any, Any] = {
    if (bodyIsStream(out)) asStreamAlways[Any]
    else {
      out.bodyType
        .map {
          case StringValueType(charset) => asStringAlways(charset.name())
          case ByteArrayValueType       => asByteArrayAlways
          case ByteBufferValueType      => asByteArrayAlways.map(ByteBuffer.wrap)
          case InputStreamValueType     => asByteArrayAlways.map(new ByteArrayInputStream(_))
          case FileValueType            => asFileAlways(clientOptions.createFile(meta))
          case MultipartValueType(_, _) => throw new IllegalArgumentException("Multipart bodies aren't supported in responses")
        }
        .getOrElse(ignore)
    }.asInstanceOf[ResponseAs[Any, Any]]
  }

  private def bodyIsStream[I](out: EndpointOutput[I]): Boolean = {
    out match {
      case _: EndpointIO.StreamBodyWrapper[_, _] => true
      case EndpointIO.Multiple(inputs)           => inputs.exists(i => bodyIsStream(i))
      case EndpointOutput.Multiple(inputs)       => inputs.exists(i => bodyIsStream(i))
      case EndpointIO.Mapped(wrapped, _, _)      => bodyIsStream(wrapped)
      case EndpointOutput.Mapped(wrapped, _, _)  => bodyIsStream(wrapped)
      case _                                     => false
    }
  }

  private def getOrThrow[T](dr: DecodeResult[T]): T = dr match {
    case DecodeResult.Value(v)    => v
    case DecodeResult.Error(o, e) => throw new IllegalArgumentException(s"Cannot decode from $o", e)
    case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
  }
}
