package sttp.tapir.client.http4s

import cats.Applicative
import cats.effect.{Blocker, ContextShift, Effect, Sync}
import cats.implicits._
import fs2.Chunk
import org.http4s._
import org.http4s.headers.`Content-Type`
import sttp.capabilities.Streams
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.internal.{CombineParams, Params, ParamsAsAny, RichEndpointOutput, SplitParams}
import sttp.tapir.{
  Codec,
  CodecFormat,
  DecodeResult,
  Endpoint,
  EndpointIO,
  EndpointInput,
  EndpointOutput,
  Mapping,
  RawBodyType,
  StreamBodyIO
}

import java.io.{ByteArrayInputStream, File, InputStream}
import java.nio.ByteBuffer
import scala.collection.Seq

private[http4s] class EndpointToHttp4sClient(blocker: Blocker, clientOptions: Http4sClientOptions) {

  def toHttp4sRequest[I, E, O, R, F[_]: ContextShift: Effect](
      e: Endpoint[I, E, O, R],
      baseUriStr: Option[String]
  ): I => (Request[F], Response[F] => F[DecodeResult[Either[E, O]]]) = { params =>
    val baseUri = Uri.unsafeFromString(baseUriStr.getOrElse("/"))
    val baseRequest = Request[F](uri = baseUri)
    val request = setInputParams[I, F](e.input, ParamsAsAny(params), baseRequest)

    def responseParser(response: Response[F]): F[DecodeResult[Either[E, O]]] = {
      parseHttp4sResponse(e).apply(response)
    }

    (request, responseParser)
  }

  def toHttp4sRequestUnsafe[I, E, O, R, F[_]: ContextShift: Effect](
      e: Endpoint[I, E, O, R],
      baseUriStr: Option[String]
  ): I => (Request[F], Response[F] => F[Either[E, O]]) = { params =>
    val (request, safeResponseParser) = toHttp4sRequest[I, E, O, R, F](e, baseUriStr).apply(params)

    def unsafeResponseParser(response: Response[F]): F[Either[E, O]] =
      safeResponseParser(response).map {
        case DecodeResult.Value(v)    => v
        case DecodeResult.Error(_, e) => throw e
        case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
      }

    (request, unsafeResponseParser)
  }

  @scala.annotation.tailrec
  private def setInputParams[I, F[_]: ContextShift: Effect](
      input: EndpointInput[I],
      params: Params,
      req: Request[F]
  ): Request[F] = {
    def value: I = params.asAny.asInstanceOf[I]

    input match {
      case EndpointInput.FixedMethod(m, _, _) => req.withMethod(Method.fromString(m.method).right.get)
      case EndpointInput.FixedPath(p, _, _)   => req.withUri(req.uri.addPath(p))
      case EndpointInput.PathCapture(_, codec, _) =>
        val path = codec.asInstanceOf[PlainCodec[Any]].encode(value: Any)
        req.withUri(req.uri.addPath(path))
      case EndpointInput.PathsCapture(codec, _) =>
        val pathFragments = codec.encode(value)
        val uri = pathFragments.foldLeft(req.uri)(_.addPath(_))
        req.withUri(uri)
      case EndpointInput.Query(name, codec, _) =>
        val encodedParams = codec.encode(value)
        req.withUri(req.uri.withQueryParam(name, encodedParams))
      case EndpointInput.Cookie(name, codec, _) =>
        codec.encode(value).foldLeft(req)(_.addCookie(name, _))
      case EndpointInput.QueryParams(codec, _) =>
        val uri = codec.encode(value).toMultiSeq.foldLeft(req.uri) { case (currentUri, (key, values)) =>
          currentUri.withQueryParam(key, values)
        }
        req.withUri(uri)
      case EndpointIO.Empty(_, _)              => req
      case EndpointIO.Body(bodyType, codec, _) => setBody(value, bodyType, codec, req)
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(streams, _, _, _)) =>
        setStreamingBody(streams)(value.asInstanceOf[streams.BinaryStream], req)
      case EndpointIO.Header(name, codec, _) =>
        val headers = codec.encode(value).map(value => Header(name, value))
        req.putHeaders(headers: _*)
      case EndpointIO.Headers(codec, _) =>
        val headers = codec.encode(value).map(h => Header(h.name, h.value))
        req.putHeaders(headers: _*)
      case EndpointIO.FixedHeader(h, _, _)           => req.putHeaders(Header(h.name, h.value))
      case EndpointInput.ExtractFromRequest(_, _)    => req // ignoring
      case a: EndpointInput.Auth[_]                  => setInputParams(a.input, params, req)
      case EndpointInput.Pair(left, right, _, split) => handleInputPair(left, right, params, split, req)
      case EndpointIO.Pair(left, right, _, split)    => handleInputPair(left, right, params, split, req)
      case EndpointInput.MappedPair(wrapped, codec)  => handleMapped(wrapped, codec.asInstanceOf[Mapping[Any, Any]], params, req)
      case EndpointIO.MappedPair(wrapped, codec)     => handleMapped(wrapped, codec.asInstanceOf[Mapping[Any, Any]], params, req)
    }
  }

  private def setBody[R, T, CF <: CodecFormat, F[_]: ContextShift: Effect](
      value: T,
      bodyType: RawBodyType[R],
      codec: Codec[R, T, CF],
      req: Request[F]
  ): Request[F] = {
    val encoded: R = codec.encode(value)

    val newReq = bodyType match {
      case RawBodyType.StringBody(charset) =>
        val entityEncoder = EntityEncoder.stringEncoder[F](Charset.fromNioCharset(charset))
        req.withEntity(encoded.asInstanceOf[String])(entityEncoder)
      case RawBodyType.ByteArrayBody =>
        req.withEntity(encoded.asInstanceOf[Array[Byte]])
      case RawBodyType.ByteBufferBody =>
        val entityEncoder = EntityEncoder.chunkEncoder[F].contramap(Chunk.byteBuffer)
        req.withEntity(encoded.asInstanceOf[ByteBuffer])(entityEncoder)
      case RawBodyType.InputStreamBody =>
        val entityEncoder = EntityEncoder.inputStreamEncoder[F, InputStream](blocker)
        req.withEntity(Applicative[F].pure(encoded.asInstanceOf[InputStream]))(entityEncoder)
      case RawBodyType.FileBody =>
        val entityEncoder = EntityEncoder.fileEncoder[F](blocker)
        req.withEntity(encoded.asInstanceOf[File])(entityEncoder)
      case _: RawBodyType.MultipartBody =>
        throw new IllegalArgumentException("Multipart body isn't supported yet")
    }
    val contentType = `Content-Type`.parse(codec.format.mediaType.toString()).right.get

    newReq.withContentType(contentType)
  }

  private def setStreamingBody[S, F[_]](
      streams: Streams[S]
  )(value: streams.BinaryStream, request: Request[F]): Request[F] =
    streams match {
      case _: Fs2Streams[_] =>
        request.withEntity(value.asInstanceOf[Fs2Streams[F]#BinaryStream])
      case _ =>
        throw new IllegalArgumentException("Only Fs2Streams streaming is supported")
    }

  private def handleInputPair[I, F[_]: ContextShift: Effect](
      left: EndpointInput[_],
      right: EndpointInput[_],
      params: Params,
      split: SplitParams,
      currentReq: Request[F]
  ): Request[F] = {
    val (leftParams, rightParams) = split(params)

    val req2 = setInputParams(left.asInstanceOf[EndpointInput[Any]], leftParams, currentReq)
    setInputParams(right.asInstanceOf[EndpointInput[Any]], rightParams, req2)
  }

  private def handleMapped[II, T, F[_]: ContextShift: Effect](
      tuple: EndpointInput[II],
      codec: Mapping[T, II],
      params: Params,
      req: Request[F]
  ): Request[F] =
    setInputParams(tuple.asInstanceOf[EndpointInput[Any]], ParamsAsAny(codec.encode(params.asAny.asInstanceOf[II])), req)

  private def parseHttp4sResponse[I, E, O, R, F[_]: Sync: ContextShift](
      e: Endpoint[I, E, O, R]
  ): Response[F] => F[DecodeResult[Either[E, O]]] = { response =>
    val code = sttp.model.StatusCode(response.status.code)

    val parser = if (code.isSuccess) responseFromOutput[F](e.output) else responseFromOutput[F](e.errorOutput)
    val output = if (code.isSuccess) e.output else e.errorOutput

    // headers with cookies
    val headers: Map[String, List[String]] = response.headers.toList.groupBy(_.name.value).mapValues(_.map(_.value)).toMap

    parser(response).map { responseBody =>
      val params = getOutputParams(output, responseBody, headers, code, response.status.reason)

      params.map(_.asAny).map(p => if (code.isSuccess) Right(p.asInstanceOf[O]) else Left(p.asInstanceOf[E]))
    }
  }

  private def responseFromOutput[F[_]: Sync: ContextShift](out: EndpointOutput[_]): Response[F] => F[Any] = { response =>
    bodyIsStream(out) match {
      case Some(streams) =>
        streams match {
          case _: Fs2Streams[_] =>
            val body: Fs2Streams[F]#BinaryStream = response.body
            body.asInstanceOf[Any].pure[F]
          case _ => throw new IllegalArgumentException("Only Fs2Streams streaming is supported")
        }
      case None =>
        out.bodyType
          .map[F[Any]] {
            case RawBodyType.StringBody(charset) =>
              response.body.compile.toVector.map(bytes => new String(bytes.toArray, charset).asInstanceOf[Any])
            case RawBodyType.ByteArrayBody =>
              response.body.compile.toVector.map(_.toArray).map(_.asInstanceOf[Any])
            case RawBodyType.ByteBufferBody =>
              response.body.compile.toVector.map(_.toArray).map(java.nio.ByteBuffer.wrap).map(_.asInstanceOf[Any])
            case RawBodyType.InputStreamBody =>
              response.body.compile.toVector.map(_.toArray).map(new ByteArrayInputStream(_)).map(_.asInstanceOf[Any])
            case RawBodyType.FileBody =>
              val file = clientOptions.createFile()
              response.body.through(fs2.io.file.writeAll(file.toPath, blocker)).compile.drain.map(_ => file.asInstanceOf[Any])
            case RawBodyType.MultipartBody(_, _) => throw new IllegalArgumentException("Multipart bodies aren't supported in responses")
          }
          .getOrElse[F[Any]](((): Any).pure[F])
    }
  }

  private def bodyIsStream[I](out: EndpointOutput[I]): Option[Streams[_]] = {
    out.traverseOutputs { case EndpointIO.StreamBodyWrapper(StreamBodyIO(streams, _, _, _)) =>
      Vector(streams)
    }.headOption
  }

  private def getOutputParams(
      output: EndpointOutput[_],
      body: => Any,
      headers: Map[String, Seq[String]],
      code: sttp.model.StatusCode,
      statusText: String
  ): DecodeResult[Params] = {
    output match {
      case s: EndpointOutput.Single[_] =>
        (s match {
          case EndpointIO.Body(_, codec, _)                               => codec.decode(body)
          case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, _)) => codec.decode(body)
          case EndpointOutput.WebSocketBodyWrapper(_) =>
            DecodeResult.Error("", new IllegalArgumentException("WebSocket aren't supported yet"))
          case EndpointIO.Header(name, codec, _) => codec.decode(headers(name).toList)
          case EndpointIO.Headers(codec, _) =>
            val h = headers.flatMap { case (k, v) => v.map(sttp.model.Header(k, _)) }.toList
            codec.decode(h)
          case EndpointOutput.StatusCode(_, codec, _)      => codec.decode(code)
          case EndpointOutput.FixedStatusCode(_, codec, _) => codec.decode(())
          case EndpointIO.FixedHeader(_, codec, _)         => codec.decode(())
          case EndpointIO.Empty(codec, _)                  => codec.decode(())
          case EndpointOutput.OneOf(mappings, codec) =>
            mappings
              .find(mapping => mapping.statusCode.isEmpty || mapping.statusCode.contains(code)) match {
              case Some(mapping) =>
                getOutputParams(mapping.output, body, headers, code, statusText).flatMap(p => codec.decode(p.asAny))
              case None =>
                DecodeResult.Error(
                  statusText,
                  new IllegalArgumentException(s"Cannot find mapping for status code ${code} in outputs $output")
                )
            }

          case EndpointIO.MappedPair(wrapped, codec) =>
            getOutputParams(wrapped, body, headers, code, statusText).flatMap(p => codec.decode(p.asAny))
          case EndpointOutput.MappedPair(wrapped, codec) =>
            getOutputParams(wrapped, body, headers, code, statusText).flatMap(p => codec.decode(p.asAny))

        }).map(ParamsAsAny)

      case EndpointOutput.Void()                        => DecodeResult.Error("", new IllegalArgumentException("Cannot convert a void output to a value!"))
      case EndpointOutput.Pair(left, right, combine, _) => handleOutputPair(left, right, combine, body, headers, code, statusText)
      case EndpointIO.Pair(left, right, combine, _)     => handleOutputPair(left, right, combine, body, headers, code, statusText)
    }
  }

  private def handleOutputPair(
      left: EndpointOutput[_],
      right: EndpointOutput[_],
      combine: CombineParams,
      body: => Any,
      headers: Map[String, Seq[String]],
      code: sttp.model.StatusCode,
      statusText: String
  ): DecodeResult[Params] = {
    val l = getOutputParams(left, body, headers, code, statusText)
    val r = getOutputParams(right, body, headers, code, statusText)
    l.flatMap(leftParams => r.map(rightParams => combine(leftParams, rightParams)))
  }

}
