package sttp.tapir.client.sttp

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import sttp.capabilities.{Effect, Streams}
import sttp.client3._
import sttp.model.Uri.PathSegment
import sttp.model.{HeaderNames, Method, Part, ResponseMetadata, StatusCode, Uri}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.EndpointOutput.WebSocketBody
import sttp.tapir._
import sttp.tapir.internal._
import sttp.ws.WebSocket

private[sttp] class EndpointToSttpClient[F[_], R](clientOptions: SttpClientOptions, wsToPipe: WebSocketToPipe[R])(implicit
    monad: MonadError[F]
) {
  def toSttpRequest[O, E, I](
      e: Endpoint[I, E, O, R with Effect[F]],
      baseUri: Option[Uri]
  ): I => F[Request[F[DecodeResult[Either[E, O]]], R]] = { params =>
    setInputParams(
      e.input,
      ParamsAsAny(params),
      baseUri.getOrElse(Uri(None, None, Nil, Nil, None)),
      basicRequest.asInstanceOf[PartialAnyRequest]
    ).map { case (uri, req1) =>
      val req2 = req1.copy[Identity, Any, R](method = sttp.model.Method(e.input.method.getOrElse(Method.GET).method), uri = uri)

      val isWebSocket = bodyIsWebSocket(e.output)

      def isSuccess(meta: ResponseMetadata) = if (isWebSocket) meta.code == StatusCode.SwitchingProtocols else meta.isSuccess

      val responseAs = fromMetadata(
        responseAsFromOutputs(e.errorOutput, isWebSocket = false),
        ConditionalResponseAs(isSuccess, responseAsFromOutputs(e.output, isWebSocket))
      ).mapWithMetadata { (body, meta) =>
        val output = if (isSuccess(meta)) e.output else e.errorOutput
        getOutputParams(output, body, meta).map { params =>
          params.map(_.asAny).map(p => if (isSuccess(meta)) Right(p) else Left(p)) match {
            case DecodeResult.Error(o, e) =>
              DecodeResult.Error(o, new IllegalArgumentException(s"Cannot decode from $o of request ${req2.method} ${req2.uri}", e))
            case other => other
          }
        }
      }

      req2.response(responseAs).asInstanceOf[Request[F[DecodeResult[Either[E, O]]], R]]
    }
  }

  private def getOutputParams(output: EndpointOutput[_, R with Effect[F]], body: Any, meta: ResponseMetadata): F[DecodeResult[Params]] = {
    output match {
      case s: EndpointOutput.Single[_, _] =>
        (s match {
          case EndpointIO.Body(_, codec, _)          => codec.decode(body).unit
          case EndpointIO.StreamBody(_, codec, _, _) => codec.decode(body).unit
          case o @ EndpointOutput.WebSocketBody(streams, _, _, codec, _, _, _, _, _, _, _) =>
            val _streams = streams.asInstanceOf[wsToPipe.S]
            codec
              .decode(
                wsToPipe
                  .apply(_streams)(
                    body.asInstanceOf[WebSocket[wsToPipe.F]],
                    o.asInstanceOf[WebSocketBody[Any, _, _, _, wsToPipe.S]]
                  )
              )
              .unit
          case EndpointIO.Header(name, codec, _)           => codec.decode(meta.headers(name).toList).unit
          case EndpointIO.Headers(codec, _)                => codec.decode(meta.headers.toList).unit
          case EndpointOutput.StatusCode(_, codec, _)      => codec.decode(meta.code).unit
          case EndpointOutput.FixedStatusCode(_, codec, _) => codec.decode(()).unit
          case EndpointIO.FixedHeader(_, codec, _)         => codec.decode(()).unit
          case EndpointIO.Empty(codec, _)                  => codec.decode(()).unit
          case EndpointOutput.OneOf(mappings, codec) =>
            mappings
              .find(mapping => mapping.statusCode.isEmpty || mapping.statusCode.contains(meta.code)) match {
              case Some(mapping) =>
                getOutputParams(mapping.output, body, meta).mapDecode(p => codec.decode(p.asAny))
              case None =>
                (DecodeResult
                  .Error(
                    meta.statusText,
                    new IllegalArgumentException(s"Cannot find mapping for status code ${meta.code} in outputs $output")
                  ): DecodeResult[_]).unit
            }

          case EndpointIO.MappedPair(wrapped, codec)     => getOutputParams(wrapped, body, meta).mapDecode(p => codec.decode(p.asAny))
          case EndpointOutput.MappedPair(wrapped, codec) => getOutputParams(wrapped, body, meta).mapDecode(p => codec.decode(p.asAny))
          case EndpointOutput.MapEffect(output, f, _)    => handleOutputMapEffect(output, f, body, meta)
          case EndpointIO.MapEffect(output, f, _)        => handleOutputMapEffect(output, f, body, meta)
        }).mapDecode(a => DecodeResult.Value(ParamsAsAny(a)))

      case EndpointOutput.Void() =>
        (DecodeResult.Error("", new IllegalArgumentException("Cannot convert a void output to a value!")): DecodeResult[Params]).unit
      case EndpointOutput.Pair(left, right, combine, _) => handleOutputPair(left, right, combine, body, meta)
      case EndpointIO.Pair(left, right, combine, _)     => handleOutputPair(left, right, combine, body, meta)
    }
  }

  private def handleOutputPair(
      left: EndpointOutput[_, R with Effect[F]],
      right: EndpointOutput[_, R with Effect[F]],
      combine: CombineParams,
      body: Any,
      meta: ResponseMetadata
  ): F[DecodeResult[Params]] = {
    val l = getOutputParams(left, body, meta)
    val r = getOutputParams(right, body, meta)
    l.flatMapDecode(leftParams => r.mapDecode(rightParams => DecodeResult.Value(combine(leftParams, rightParams))))
  }

  private def handleOutputMapEffect[T, U](
      output: EndpointOutput[T, _],
      f: MonadError[Any] => Any => Any,
      body: Any,
      meta: ResponseMetadata
  ): F[DecodeResult[U]] = {
    getOutputParams(output.asInstanceOf[EndpointOutput[T, R with Effect[F]]], body, meta).flatMapDecode { params =>
      f.asInstanceOf[MonadError[F] => T => F[DecodeResult[U]]](monad)(params.asAny.asInstanceOf[T])
    }
  }

  private implicit class FDecodeResultSyntax[T, U](tf: F[DecodeResult[T]]) {
    def mapDecode(f: T => DecodeResult[U]): F[DecodeResult[U]] = tf.map {
      case DecodeResult.Value(t)         => f(t)
      case failure: DecodeResult.Failure => failure
    }
    def flatMapDecode(f: T => F[DecodeResult[U]]): F[DecodeResult[U]] = tf.flatMap {
      case DecodeResult.Value(t)         => f(t)
      case failure: DecodeResult.Failure => (failure: DecodeResult[U]).unit
    }
  }

  private type PartialAnyRequest = PartialRequest[Any, R]

  @scala.annotation.tailrec
  private def setInputParams[I](
      input: EndpointInput[I, R with Effect[F]],
      params: Params,
      uri: Uri,
      req: PartialAnyRequest
  ): F[(Uri, PartialAnyRequest)] = {
    def value: I = params.asAny.asInstanceOf[I]
    input match {
      case EndpointInput.FixedMethod(_, _, _) => (uri, req).unit
      case EndpointInput.FixedPath(p, _, _)   => (uri.copy(pathSegments = uri.pathSegments :+ PathSegment(p)), req).unit
      case EndpointInput.PathCapture(_, codec, _) =>
        val v = codec.asInstanceOf[PlainCodec[Any]].encode(value: Any)
        (uri.copy(pathSegments = uri.pathSegments :+ PathSegment(v)), req).unit
      case EndpointInput.PathsCapture(codec, _) =>
        val ps = codec.encode(value)
        (uri.copy(pathSegments = uri.pathSegments ++ ps.map(PathSegment(_))), req).unit
      case EndpointInput.Query(name, codec, _) =>
        val uri2 = codec.encode(value).foldLeft(uri) { case (u, v) => u.addParam(name, v) }
        (uri2, req).unit
      case EndpointInput.Cookie(name, codec, _) =>
        val req2 = codec.encode(value).foldLeft(req) { case (r, v) => r.cookie(name, v) }
        (uri, req2).unit
      case EndpointInput.QueryParams(codec, _) =>
        val mqp = codec.encode(value)
        val uri2 = uri.addParams(mqp.toSeq: _*)
        (uri2, req).unit
      case EndpointIO.Empty(_, _) => (uri, req).unit
      case EndpointIO.Body(bodyType, codec, _) =>
        val req2 = setBody(value, bodyType, codec, req)
        (uri, req2).unit
      case EndpointIO.StreamBody(streams, _, _, _) =>
        val req2 = req.streamBody(streams)(value.asInstanceOf[streams.BinaryStream])
        (uri, req2).unit
      case EndpointIO.Header(name, codec, _) =>
        val req2 = codec
          .encode(value)
          .foldLeft(req) { case (r, v) => r.header(name, v) }
        (uri, req2).unit
      case EndpointIO.Headers(codec, _) =>
        val headers = codec.encode(value)
        val req2 = headers.foldLeft(req) { case (r, h) =>
          val replaceExisting = HeaderNames.ContentType.equalsIgnoreCase(h.name) || HeaderNames.ContentLength.equalsIgnoreCase(h.name)
          r.header(h, replaceExisting)
        }
        (uri, req2).unit
      case EndpointIO.FixedHeader(h, _, _) =>
        val req2 = req.header(h)
        (uri, req2).unit
      case EndpointInput.ExtractFromRequest(_, _) =>
        // ignoring
        (uri, req).unit
      case a: EndpointInput.Auth[_, _]               => setInputParams(a.input, params, uri, req)
      case EndpointInput.Pair(left, right, _, split) => handleInputPair(left, right, params, split, uri, req)
      case EndpointIO.Pair(left, right, _, split)    => handleInputPair(left, right, params, split, uri, req)
      case EndpointInput.MappedPair(wrapped, codec)  => handleMapped(wrapped, codec.asInstanceOf[Mapping[Any, Any]], params, uri, req)
      case EndpointIO.MappedPair(wrapped, codec)     => handleMapped(wrapped, codec.asInstanceOf[Mapping[Any, Any]], params, uri, req)
      case EndpointInput.MapEffect(input, _, g)      => handleMapEffect(input, g, params, uri, req)
      case EndpointIO.MapEffect(input, _, g)         => handleMapEffect(input, g, params, uri, req)
    }
  }

  def handleInputPair(
      left: EndpointInput[_, R with Effect[F]],
      right: EndpointInput[_, R with Effect[F]],
      params: Params,
      split: SplitParams,
      uri: Uri,
      req: PartialAnyRequest
  ): F[(Uri, PartialAnyRequest)] = {
    val (leftParams, rightParams) = split(params)
    setInputParams(left.asInstanceOf[EndpointInput[Any, R with Effect[F]]], leftParams, uri, req).flatMap { case (uri2, req2) =>
      setInputParams(right.asInstanceOf[EndpointInput[Any, R with Effect[F]]], rightParams, uri2, req2)
    }
  }

  private def handleMapped[II, T](
      tuple: EndpointInput[II, R with Effect[F]],
      codec: Mapping[T, II],
      params: Params,
      uri: Uri,
      req: PartialAnyRequest
  ): F[(Uri, PartialAnyRequest)] = {
    setInputParams(
      tuple.asInstanceOf[EndpointInput[Any, R with Effect[F]]],
      ParamsAsAny(codec.encode(params.asAny.asInstanceOf[II])),
      uri,
      req
    )
  }

  private def handleMapEffect[T, U](
      input: EndpointInput[T, R with Effect[F]],
      g: MonadError[F] => U => F[T],
      params: Params,
      uri: Uri,
      req: PartialAnyRequest
  ): F[(Uri, PartialAnyRequest)] = {
    g(monad)(params.asAny.asInstanceOf[U]).flatMap { t =>
      setInputParams(input, ParamsAsAny(t), uri, req)
    }
  }

  private def setBody[L, H, CF <: CodecFormat](
      v: H,
      bodyType: RawBodyType[L],
      codec: Codec[L, H, CF],
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
        val parts: Seq[Part[RequestBody[Any]]] = (encoded: Seq[RawPart]).flatMap { p =>
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

  private def partToSttpPart[T](p: Part[T], bodyType: RawBodyType[T]): Part[RequestBody[Any]] =
    bodyType match {
      case RawBodyType.StringBody(charset) => multipart(p.name, p.body, charset.toString)
      case RawBodyType.ByteArrayBody       => multipart(p.name, p.body)
      case RawBodyType.ByteBufferBody      => multipart(p.name, p.body)
      case RawBodyType.InputStreamBody     => multipart(p.name, p.body)
      case RawBodyType.FileBody            => multipartFile(p.name, p.body)
      case RawBodyType.MultipartBody(_, _) => throw new IllegalArgumentException("Nested multipart bodies aren't supported")
    }

  private def responseAsFromOutputs(out: EndpointOutput[_, _], isWebSocket: Boolean): ResponseAs[Any, Any] = {
    ((bodyIsStream(out), isWebSocket) match {
      case (Some(streams), _) => asStreamAlwaysUnsafe(streams)
      case (_, true)          => asWebSocketAlwaysUnsafe
      case (None, false) =>
        out.bodyType
          .map {
            case RawBodyType.StringBody(charset) => asStringAlways(charset.name())
            case RawBodyType.ByteArrayBody       => asByteArrayAlways
            case RawBodyType.ByteBufferBody      => asByteArrayAlways.map(ByteBuffer.wrap)
            case RawBodyType.InputStreamBody     => asByteArrayAlways.map(new ByteArrayInputStream(_))
            case RawBodyType.FileBody            => asFileAlways(clientOptions.createFile())
            case RawBodyType.MultipartBody(_, _) => throw new IllegalArgumentException("Multipart bodies aren't supported in responses")
          }
          .getOrElse(ignore)
    }).asInstanceOf[ResponseAs[Any, Any]]
  }

  private def bodyIsStream[I](out: EndpointOutput[I, _]): Option[Streams[_]] = {
    out.traverseOutputs { case EndpointIO.StreamBody(streams, _, _, _) =>
      Vector(streams)
    }.headOption
  }

  private def bodyIsWebSocket[I](out: EndpointOutput[I, _]): Boolean = {
    out.traverseOutputs { case _: EndpointOutput.WebSocketBody[_, _, _, _, _] =>
      Vector(())
    }.nonEmpty
  }

}
