package sttp.tapir.client.play

import play.api.libs.ws.DefaultBodyReadables._
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws._
import sttp.capabilities.Streams
import sttp.capabilities.akka.AkkaStreams
import sttp.model.{Header, Method, ResponseMetadata}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.client.ClientOutputParams
import sttp.tapir.internal.{Params, ParamsAsAny, RichEndpointOutput, SplitParams}
import sttp.tapir.{
  Codec,
  CodecFormat,
  DecodeResult,
  Endpoint,
  EndpointIO,
  EndpointInput,
  EndpointOutput,
  FileRange,
  InputStreamRange,
  Mapping,
  RawBodyType,
  StreamBodyIO,
  WebSocketBodyOutput
}

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.function.Supplier
import scala.collection.immutable.Seq

private[play] class EndpointToPlayClient(clientOptions: PlayClientOptions, ws: StandaloneWSClient) {

  def toPlayRequest[A, I, E, O, R](
      e: Endpoint[A, I, E, O, R],
      baseUri: String
  ): A => I => (StandaloneWSRequest, StandaloneWSResponse => DecodeResult[Either[E, O]]) = { aParams => iParams =>
    val req0 = setInputParams(e.securityInput, ParamsAsAny(aParams), ws.url(baseUri))
    val req = setInputParams(e.input, ParamsAsAny(iParams), req0)
      .withMethod(e.method.getOrElse(Method.GET).method)

    def responseParser(response: StandaloneWSResponse): DecodeResult[Either[E, O]] = {
      parsePlayResponse(e)(response) match {
        case DecodeResult.Error(o, e) =>
          DecodeResult.Error(o, new IllegalArgumentException(s"Cannot decode from: $o, request ${req.method} ${req.uri}", e))
        case other => other
      }
    }

    (req, responseParser)
  }

  def toPlayRequestThrowDecodeFailures[A, I, E, O, R](
      e: Endpoint[A, I, E, O, R],
      baseUri: String
  ): A => I => (StandaloneWSRequest, StandaloneWSResponse => Either[E, O]) = { aParams => iParams =>
    val (req, responseParser) = toPlayRequest(e, baseUri)(aParams)(iParams)
    def throwingResponseParser(response: StandaloneWSResponse): Either[E, O] = {
      getOrThrow(responseParser(response))
    }
    (req, throwingResponseParser)
  }

  private def parsePlayResponse[A, I, E, O, R](e: Endpoint[A, I, E, O, R]): StandaloneWSResponse => DecodeResult[Either[E, O]] = {
    response =>
      val code = sttp.model.StatusCode(response.status)

      val parser = if (code.isSuccess) responseFromOutput(e.output) else responseFromOutput(e.errorOutput)
      val output = if (code.isSuccess) e.output else e.errorOutput

      val headers = (cookiesAsHeaders(response.cookies.toVector) ++ response.headers).flatMap { case (name, values) =>
        values.map { v => Header(name, v) }
      }.toVector

      val meta = ResponseMetadata(code, response.statusText, headers)

      val params = clientOutputParams(output, parser(response), meta)

      params.map(_.asAny).map(p => if (code.isSuccess) Right(p.asInstanceOf[O]) else Left(p.asInstanceOf[E]))
  }

  private def cookiesAsHeaders(cookies: Seq[WSCookie]): Map[String, Seq[String]] = {
    Map("Set-Cookie" -> cookies.map(c => s"${c.name}=${c.value}"))
  }

  @scala.annotation.tailrec
  private def setInputParams[I](
      input: EndpointInput[I],
      params: Params,
      req: StandaloneWSRequest
  ): StandaloneWSRequest = {
    def value: I = params.asAny.asInstanceOf[I]
    def encodePathSegment(p: String): String = URLEncoder.encode(p, "UTF-8")
    input match {
      case EndpointInput.FixedMethod(m, _, _) => req.withMethod(m.method)
      case EndpointInput.FixedPath(p, _, _) =>
        req.withUrl(req.url + "/" + encodePathSegment(p))
      case EndpointInput.PathCapture(_, codec, _) =>
        val v = codec.asInstanceOf[PlainCodec[Any]].encode(value: Any)
        req.withUrl(req.url + "/" + encodePathSegment(v))
      case EndpointInput.PathsCapture(codec, _) =>
        val ps = codec.encode(value)
        req.withUrl(req.url + ps.map(encodePathSegment).mkString("/", "/", ""))
      case EndpointInput.Query(name, Some(flagValue), _, _) if value == flagValue =>
        req.addQueryStringParameters(name -> "")
      case EndpointInput.Query(name, _, codec, _) =>
        val req2 = codec.encode(value).foldLeft(req) { case (r, v) => r.addQueryStringParameters(name -> v) }
        req2
      case EndpointInput.Cookie(name, codec, _) =>
        val req2 = codec.encode(value).foldLeft(req) { case (r, v) => r.addCookies(DefaultWSCookie(name, v)) }
        req2
      case EndpointInput.QueryParams(codec, _) =>
        val mqp = codec.encode(value)
        req.addQueryStringParameters(mqp.toSeq: _*)
      case EndpointIO.Empty(_, _) => req
      case EndpointIO.Body(bodyType, codec, _) =>
        val req2 = setBody(value, bodyType, codec, req)
        req2
      case EndpointIO.OneOfBody(EndpointIO.OneOfBodyVariant(_, Left(body)) :: _, _) => setInputParams(body, params, req)
      case EndpointIO.OneOfBody(
            EndpointIO.OneOfBodyVariant(_, Right(EndpointIO.StreamBodyWrapper(StreamBodyIO(streams, _, _, _, _)))) :: _,
            _
          ) =>
        setStreamingBody(streams)(value.asInstanceOf[streams.BinaryStream], req)
      case EndpointIO.OneOfBody(Nil, _) => throw new RuntimeException("One of body without variants")
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(streams, _, _, _, _)) =>
        setStreamingBody(streams)(value.asInstanceOf[streams.BinaryStream], req)
      case EndpointIO.Header(name, codec, _) =>
        val req2 = codec
          .encode(value)
          .foldLeft(req) { case (r, v) => r.addHttpHeaders(name -> v) }
        req2
      case EndpointIO.Headers(codec, _) =>
        val headers = codec.encode(value)
        val req2 = headers.foldLeft(req) { case (r, h) => r.addHttpHeaders(h.name -> h.value) }
        req2
      case EndpointIO.FixedHeader(h, _, _) =>
        val req2 = req.addHttpHeaders(h.name -> h.value)
        req2
      case EndpointInput.ExtractFromRequest(_, _) =>
        // ignoring
        req
      case a: EndpointInput.Auth[_, _]               => setInputParams(a.input, params, req)
      case EndpointInput.Pair(left, right, _, split) => handleInputPair(left, right, params, split, req)
      case EndpointIO.Pair(left, right, _, split)    => handleInputPair(left, right, params, split, req)
      case EndpointInput.MappedPair(wrapped, codec) =>
        handleMapped(wrapped.asInstanceOf[EndpointInput[Any]], codec.asInstanceOf[Mapping[Any, Any]], params, req)
      case EndpointIO.MappedPair(wrapped, codec) =>
        handleMapped(wrapped.asInstanceOf[EndpointInput[Any]], codec.asInstanceOf[Mapping[Any, Any]], params, req)
    }
  }

  def handleInputPair(
      left: EndpointInput[_],
      right: EndpointInput[_],
      params: Params,
      split: SplitParams,
      req: StandaloneWSRequest
  ): StandaloneWSRequest = {
    val (leftParams, rightParams) = split(params)
    val req2 = setInputParams(left.asInstanceOf[EndpointInput[Any]], leftParams, req)
    setInputParams(right.asInstanceOf[EndpointInput[Any]], rightParams, req2)
  }

  private def handleMapped[II, T](
      tuple: EndpointInput[II],
      codec: Mapping[T, II],
      params: Params,
      req: StandaloneWSRequest
  ): StandaloneWSRequest = {
    setInputParams(tuple.asInstanceOf[EndpointInput[Any]], ParamsAsAny(codec.encode(params.asAny.asInstanceOf[II])), req)
  }

  private def setBody[R, T, CF <: CodecFormat](
      v: T,
      bodyType: RawBodyType[R],
      codec: Codec[R, T, CF],
      req: StandaloneWSRequest
  ): StandaloneWSRequest = {
    val encoded: R = codec.encode(v)
    val req2 = bodyType match {
      case RawBodyType.StringBody(_) =>
        val defaultStringBodyWritable: BodyWritable[String] = implicitly[BodyWritable[String]]
        val bodyWritable = BodyWritable[String](defaultStringBodyWritable.transform, codec.format.mediaType.toString)
        req.withBody(encoded.asInstanceOf[String])(bodyWritable)
      case RawBodyType.ByteArrayBody   => req.withBody(encoded.asInstanceOf[Array[Byte]])
      case RawBodyType.ByteBufferBody  => req.withBody(encoded.asInstanceOf[ByteBuffer])
      case RawBodyType.InputStreamBody =>
        // For some reason, Play comes with a Writeable for Supplier[InputStream] but not InputStream directly
        val inputStreamSupplier: Supplier[InputStream] = () => encoded.asInstanceOf[InputStream]
        req.withBody(inputStreamSupplier)
      case RawBodyType.InputStreamRangeBody =>
        val inputStreamSupplier: Supplier[InputStream] = new Supplier[InputStream] {
          override def get(): InputStream = encoded.inputStream()
        }
        req.withBody(inputStreamSupplier)
      case RawBodyType.FileBody         => req.withBody(encoded.asInstanceOf[FileRange].file)
      case _: RawBodyType.MultipartBody => throw new IllegalArgumentException("Multipart body aren't supported")
    }

    req2
  }

  private def setStreamingBody[S](streams: Streams[S])(v: streams.BinaryStream, req: StandaloneWSRequest): StandaloneWSRequest = {
    streams match {
      case AkkaStreams => req.withBody(v.asInstanceOf[AkkaStreams.BinaryStream])
      case _           => throw new IllegalArgumentException("Only AkkaStreams streaming is supported")
    }
  }

  private def getOrThrow[T](dr: DecodeResult[T]): T =
    dr match {
      case DecodeResult.Value(v)    => v
      case DecodeResult.Error(_, e) => throw e
      case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
    }

  private def responseFromOutput(out: EndpointOutput[_]): StandaloneWSResponse => Any = { response =>
    bodyIsStream(out) match {
      case Some(streams) =>
        streams match {
          case AkkaStreams => response.body[AkkaStreams.BinaryStream]
          case _           => throw new IllegalArgumentException("Only AkkaStreams streaming is supported")
        }
      case None =>
        out.bodyType
          .map {
            case RawBodyType.StringBody(_)   => response.body
            case RawBodyType.ByteArrayBody   => response.body[Array[Byte]]
            case RawBodyType.ByteBufferBody  => response.body[ByteBuffer]
            case RawBodyType.InputStreamBody => new ByteArrayInputStream(response.body[Array[Byte]])
            case RawBodyType.InputStreamRangeBody =>
              InputStreamRange(() => new ByteArrayInputStream(response.body[Array[Byte]]))
            case RawBodyType.FileBody =>
              // TODO Consider using bodyAsSource to not load the whole content in memory
              val f = clientOptions.createFile()
              val outputStream = Files.newOutputStream(f.toPath)
              outputStream.write(response.body[Array[Byte]])
              outputStream.close()
              FileRange(f)
            case RawBodyType.MultipartBody(_, _) => throw new IllegalArgumentException("Multipart bodies aren't supported in responses")
          }
          .getOrElse(()) // Unit
    }
  }

  private def bodyIsStream[I](out: EndpointOutput[I]): Option[Streams[_]] = {
    out.traverseOutputs { case EndpointIO.StreamBodyWrapper(StreamBodyIO(streams, _, _, _, _)) =>
      Vector(streams)
    }.headOption
  }

  private val clientOutputParams = new ClientOutputParams {
    override def decodeWebSocketBody(o: WebSocketBodyOutput[_, _, _, _, _], body: Any): DecodeResult[Any] =
      DecodeResult.Error("", new IllegalArgumentException("WebSocket aren't supported yet"))
  }
}
