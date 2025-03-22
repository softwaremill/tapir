package sttp.tapir.client.sttp4

import sttp.client4._
import sttp.model._
import sttp.tapir._
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.client.ClientOutputParams
import sttp.tapir.internal._

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.ByteBuffer
import scala.annotation.tailrec

private[sttp4] trait EndpointToSttpClient {
  protected type PartialAnyRequest = PartialRequest[_]

  protected def isSuccess(meta: ResponseMetadata): Boolean = meta.isSuccess

  final protected def prepareRequestWithInput[A, E, O, I, R](
      e: Endpoint[A, I, E, O, R],
      baseUri: Option[Uri],
      aParams: A,
      iParams: I
  ): Request[?] = {
    val (uri1, req1) =
      setInputParams(
        e.securityInput,
        ParamsAsAny(aParams),
        baseUri.getOrElse(Uri(None, None, Uri.EmptyPath, Nil, None)),
        basicRequest.asInstanceOf[PartialAnyRequest]
      )

    val (uri2, req2) =
      setInputParams(
        e.input,
        ParamsAsAny(iParams),
        uri1,
        req1
      )

    req2.method(sttp.model.Method(e.method.getOrElse(Method.GET).method), uri2)
  }

  final protected def mapReqOutputWithMetadata[A, I, E, O, R, T](
      e: Endpoint[A, I, E, O, R],
      body: T,
      meta: ResponseMetadata,
      clientOutputParams: ClientOutputParams
  ): DecodeResult[Either[Any, Any]] = {
    val output = if (isSuccess(meta)) e.output else e.errorOutput
    val params = clientOutputParams(output, body, meta)
    params.map(_.asAny).map(p => if (isSuccess(meta)) Right(p) else Left(p))
  }

  final protected def mapDecodeError(decodeResult: DecodeResult[Either[Any, Any]], req: Request[?]): DecodeResult[Either[Any, Any]] = {
    decodeResult match {
      case DecodeResult.Error(o, e) =>
        DecodeResult.Error(o, new IllegalArgumentException(s"Cannot decode from: $o, request: ${req.method} ${req.uri}", e))
      case other => other
    }
  }

  final protected def outToResponseAs(out: EndpointOutput[_], clientOptions: SttpClientOptions): ResponseAs[Any] =
    out.bodyType
      .map {
        case RawBodyType.StringBody(charset)  => asStringAlways(charset.name())
        case RawBodyType.ByteArrayBody        => asByteArrayAlways
        case RawBodyType.ByteBufferBody       => asByteArrayAlways.map(ByteBuffer.wrap)
        case RawBodyType.InputStreamBody      => asByteArrayAlways.map(new ByteArrayInputStream(_))
        case RawBodyType.FileBody             => asFileAlways(clientOptions.createFile()).map(d => FileRange(d))
        case RawBodyType.InputStreamRangeBody => asByteArrayAlways.map(b => InputStreamRange(() => new ByteArrayInputStream(b)))
        case RawBodyType.MultipartBody(_, _)  => throw new IllegalArgumentException("Multipart bodies aren't supported in responses")
      }
      .getOrElse(ignore)

  @tailrec
  private def setInputParams[I](
      input: EndpointInput[I],
      params: Params,
      uri: Uri,
      req: PartialAnyRequest
  ): (Uri, PartialAnyRequest) = {
    def value: I = params.asAny.asInstanceOf[I]
    input match {
      case EndpointInput.FixedMethod(_, _, _) => (uri, req)
      case EndpointInput.FixedPath(p, _, _)   => (uri.addPath(p), req)
      case EndpointInput.PathCapture(_, codec, _) =>
        val v = codec.asInstanceOf[PlainCodec[Any]].encode(value: Any)
        (uri.addPath(v), req)
      case EndpointInput.PathsCapture(codec, _) =>
        val ps = codec.encode(value)
        (uri.addPath(ps), req)
      case EndpointInput.Query(name, Some(flagValue), _, _) if value == flagValue =>
        (uri.withParams(uri.params.param(name, Nil)), req)
      case EndpointInput.Query(name, _, codec, _) =>
        val uri2 = codec.encode(value).foldLeft(uri) { case (u, v) => u.addParam(name, v) }
        (uri2, req)
      case EndpointInput.Cookie(name, codec, _) =>
        val req2 = codec.encode(value).foldLeft(req) { case (r, v) => r.cookie(name, v) }
        (uri, req2)
      case EndpointInput.QueryParams(codec, _) =>
        val mqp = codec.encode(value)
        val uri2 = uri.addParams(mqp.toSeq: _*)
        (uri2, req)
      case EndpointIO.Empty(_, _) => (uri, req)
      case EndpointIO.Body(bodyType, codec, _) =>
        val req2 = setBody(value, bodyType, codec, req)
        (uri, req2)
      case EndpointIO.OneOfBody(EndpointIO.OneOfBodyVariant(_, Left(body)) :: _, _) => setInputParams(body, params, uri, req)
      case EndpointIO.OneOfBody(
            EndpointIO.OneOfBodyVariant(_, Right(EndpointIO.StreamBodyWrapper(StreamBodyIO(streams, _, _, _, _)))) :: _,
            _
          ) =>
        val req2 = req.body(value.asInstanceOf[InputStream])
        (uri, req2)
      case EndpointIO.OneOfBody(Nil, _)                                    => throw new RuntimeException("One of body without variants")
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(streams, _, _, _, _)) => (uri, req)
      case EndpointIO.Header(name, codec, _) =>
        val req2 = codec
          .encode(value)
          .foldLeft(req) { case (r, v) => r.header(name, v) }
        (uri, req2)
      case EndpointIO.Headers(codec, _) =>
        val headers = codec.encode(value)
        val req2 = headers.foldLeft(req) { case (r, h) =>
          val onDuplicate =
            if (HeaderNames.ContentType.equalsIgnoreCase(h.name) || HeaderNames.ContentLength.equalsIgnoreCase(h.name))
              DuplicateHeaderBehavior.Replace
            else DuplicateHeaderBehavior.Add
          r.header(h, onDuplicate)
        }
        (uri, req2)
      case EndpointIO.FixedHeader(h, _, _) =>
        val req2 = req.header(h)
        (uri, req2)
      case EndpointInput.ExtractFromRequest(_, _) =>
        // ignoring
        (uri, req)
      case a: EndpointInput.Auth[_, _]               => setInputParams(a.input, params, uri, req)
      case EndpointInput.Pair(left, right, _, split) => handleInputPair(left, right, params, split, uri, req)
      case EndpointIO.Pair(left, right, _, split)    => handleInputPair(left, right, params, split, uri, req)
      case EndpointInput.MappedPair(wrapped, codec)  => handleMapped(wrapped, codec, params, uri, req)
      case EndpointIO.MappedPair(wrapped, codec)     => handleMapped(wrapped, codec, params, uri, req)
    }
  }

  private def handleInputPair(
      left: EndpointInput[_],
      right: EndpointInput[_],
      params: Params,
      split: SplitParams,
      uri: Uri,
      req: PartialAnyRequest
  ): (Uri, PartialAnyRequest) = {
    val (leftParams, rightParams) = split(params)
    val (uri2, req2) = setInputParams(left.asInstanceOf[EndpointInput[Any]], leftParams, uri, req)
    setInputParams(right.asInstanceOf[EndpointInput[Any]], rightParams, uri2, req2)
  }

  private def handleMapped[II, T](
      tuple: EndpointInput[_],
      codec: Mapping[T, II],
      params: Params,
      uri: Uri,
      req: PartialAnyRequest
  ): (Uri, PartialAnyRequest) = {
    setInputParams(tuple, ParamsAsAny(codec.encode(params.asAny.asInstanceOf[II])), uri, req)
  }

  private def setBody[L, H, CF <: CodecFormat](
      v: H,
      bodyType: RawBodyType[L],
      codec: Codec[L, H, CF],
      req: PartialAnyRequest
  ): PartialAnyRequest = {
    // If true, Content-Type header was explicitly set, so the body's default value
    // or the codec's media type should not override it.
    val wasContentTypeAlreadySet = req.header(HeaderNames.ContentType).nonEmpty

    val encoded = codec.encode(v)
    val req2 = bodyType match {
      case RawBodyType.StringBody(charset)  => req.body(encoded, charset.name())
      case RawBodyType.ByteArrayBody        => req.body(encoded)
      case RawBodyType.ByteBufferBody       => req.body(encoded)
      case RawBodyType.InputStreamBody      => req.body(encoded)
      case RawBodyType.FileBody             => req.body(encoded.file)
      case RawBodyType.InputStreamRangeBody => req.body(encoded.inputStream())
      case m: RawBodyType.MultipartBody =>
        val parts: Seq[Part[BasicBodyPart]] = (encoded: Seq[RawPart]).flatMap { p =>
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

    if (wasContentTypeAlreadySet) req2 else req2.contentType(codec.format.mediaType)
  }

  private def partToSttpPart[T](p: Part[T], bodyType: RawBodyType[T]): Part[BasicBodyPart] =
    bodyType match {
      case RawBodyType.StringBody(charset)  => multipart(p.name, p.body, charset.toString)
      case RawBodyType.ByteArrayBody        => multipart(p.name, p.body)
      case RawBodyType.ByteBufferBody       => multipart(p.name, p.body)
      case RawBodyType.InputStreamBody      => multipart(p.name, p.body)
      case RawBodyType.FileBody             => multipartFile(p.name, p.body.asInstanceOf[FileRange].file)
      case RawBodyType.InputStreamRangeBody => multipart(p.name, p.body.inputStream())
      case RawBodyType.MultipartBody(_, _)  => throw new IllegalArgumentException("Nested multipart bodies aren't supported")
    }
}
