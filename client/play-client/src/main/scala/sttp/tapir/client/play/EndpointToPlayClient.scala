package sttp.tapir.client.play

import play.api.libs.ws.DefaultBodyReadables._
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws._
import sttp.capabilities.akka.AkkaStreams
import sttp.capabilities.{Effect, Streams}
import sttp.model.Method
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.internal.{CombineParams, Params, ParamsAsAny, RichEndpointInput, RichEndpointOutput, SplitParams}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Endpoint, EndpointIO, EndpointInput, EndpointOutput, Mapping, RawBodyType}

import java.io.{ByteArrayInputStream, File, InputStream}
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.function.Supplier
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

private[play] class EndpointToPlayClient(clientOptions: PlayClientOptions, ws: StandaloneWSClient)(implicit ec: ExecutionContext) {
  private implicit val monad: FutureMonad = new FutureMonad()

  def toPlayRequest[I, E, O](
      e: Endpoint[I, E, O, AkkaStreams with Effect[Future]],
      baseUri: String
  ): I => Future[(StandaloneWSRequest, StandaloneWSResponse => Future[DecodeResult[Either[E, O]]])] = { params =>
    setInputParams(e.input, ParamsAsAny(params), ws.url(baseUri)).map { req =>
      def responseParser(response: StandaloneWSResponse): Future[DecodeResult[Either[E, O]]] = {
        parsePlayResponse(e)(response).map {
          case DecodeResult.Error(o, e) =>
            DecodeResult.Error(o, new IllegalArgumentException(s"Cannot decode from $o of request ${req.method} ${req.uri}", e))
          case other => other
        }
      }

      (req.withMethod(e.input.method.getOrElse(Method.GET).method), responseParser)
    }
  }

  def toPlayRequestUnsafe[I, E, O](
      e: Endpoint[I, E, O, AkkaStreams with Effect[Future]],
      baseUri: String
  ): I => Future[(StandaloneWSRequest, StandaloneWSResponse => Future[Either[E, O]])] = { params =>
    toPlayRequest(e, baseUri)(params).map { case (req, responseParser) =>
      def unsafeResponseParser(response: StandaloneWSResponse): Future[Either[E, O]] = {
        responseParser(response).map(getOrThrow)
      }

      (req, unsafeResponseParser)
    }
  }

  private def parsePlayResponse[I, E, O, R](e: Endpoint[I, E, O, R]): StandaloneWSResponse => Future[DecodeResult[Either[E, O]]] = {
    response =>
      val code = sttp.model.StatusCode(response.status)

      val parser = if (code.isSuccess) responseFromOutput(e.output) else responseFromOutput(e.errorOutput)
      val output = if (code.isSuccess) e.output else e.errorOutput

      val headers = cookiesAsHeaders(response.cookies) ++ response.headers

      getOutputParams(output, parser(response), headers, code, response.statusText).map { params =>
        params.map(_.asAny).map(p => if (code.isSuccess) Right(p.asInstanceOf[O]) else Left(p.asInstanceOf[E]))
      }
  }

  private def getOutputParams(
      output: EndpointOutput[_, _],
      body: => Any,
      headers: Map[String, Seq[String]],
      code: sttp.model.StatusCode,
      statusText: String
  ): Future[DecodeResult[Params]] = {
    output match {
      case s: EndpointOutput.Single[_, _] =>
        (s match {
          case EndpointIO.Body(_, codec, _)          => Future.successful(codec.decode(body))
          case EndpointIO.StreamBody(_, codec, _, _) => Future.successful(codec.decode(body))
          case EndpointOutput.WebSocketBody(_, _, _, _, _, _, _, _, _, _, _) =>
            Future.successful(DecodeResult.Error("", new IllegalArgumentException("WebSocket aren't supported yet")))
          case EndpointIO.Header(name, codec, _) => Future.successful(codec.decode(headers(name).toList))
          case EndpointIO.Headers(codec, _) =>
            val h = headers.flatMap { case (k, v) => v.map(sttp.model.Header(k, _)) }.toList
            Future.successful(codec.decode(h))
          case EndpointOutput.StatusCode(_, codec, _)      => Future.successful(codec.decode(code))
          case EndpointOutput.FixedStatusCode(_, codec, _) => Future.successful(codec.decode(()))
          case EndpointIO.FixedHeader(_, codec, _)         => Future.successful(codec.decode(()))
          case EndpointIO.Empty(codec, _)                  => Future.successful(codec.decode(()))
          case EndpointOutput.OneOf(mappings, codec) =>
            mappings
              .find(mapping => mapping.statusCode.isEmpty || mapping.statusCode.contains(code)) match {
              case Some(mapping) =>
                getOutputParams(mapping.output, body, headers, code, statusText).mapDecode(p => codec.decode(p.asAny))
              case None =>
                Future.successful(
                  DecodeResult.Error(
                    statusText,
                    new IllegalArgumentException(s"Cannot find mapping for status code ${code} in outputs $output")
                  )
                )
            }

          case EndpointIO.MappedPair(wrapped, codec) =>
            getOutputParams(wrapped, body, headers, code, statusText).mapDecode(p => codec.decode(p.asAny))
          case EndpointOutput.MappedPair(wrapped, codec) =>
            getOutputParams(wrapped, body, headers, code, statusText).mapDecode(p => codec.decode(p.asAny))
          case EndpointOutput.MapEffect(output, f, _) => handleOutputMapEffect(output, f, body, headers, code, statusText)
          case EndpointIO.MapEffect(output, f, _)     => handleOutputMapEffect(output, f, body, headers, code, statusText)

        }).mapDecode(a => DecodeResult.Value(ParamsAsAny(a)))

      case EndpointOutput.Void() =>
        Future.successful(DecodeResult.Error("", new IllegalArgumentException("Cannot convert a void output to a value!")))
      case EndpointOutput.Pair(left, right, combine, _) => handleOutputPair(left, right, combine, body, headers, code, statusText)
      case EndpointIO.Pair(left, right, combine, _)     => handleOutputPair(left, right, combine, body, headers, code, statusText)
    }
  }

  private def handleOutputPair(
      left: EndpointOutput[_, _],
      right: EndpointOutput[_, _],
      combine: CombineParams,
      body: => Any,
      headers: Map[String, Seq[String]],
      code: sttp.model.StatusCode,
      statusText: String
  ): Future[DecodeResult[Params]] = {
    val l = getOutputParams(left, body, headers, code, statusText)
    val r = getOutputParams(right, body, headers, code, statusText)
    l.flatMapDecode(leftParams => r.mapDecode(rightParams => DecodeResult.Value(combine(leftParams, rightParams))))
  }

  private def handleOutputMapEffect[T, U](
      output: EndpointOutput[T, _],
      f: MonadError[Any] => Any => Any,
      body: => Any,
      headers: Map[String, Seq[String]],
      code: sttp.model.StatusCode,
      statusText: String
  ): Future[DecodeResult[U]] = {
    getOutputParams(output.asInstanceOf[EndpointOutput[T, _]], body, headers, code, statusText).flatMapDecode { params =>
      f.asInstanceOf[MonadError[Future] => T => Future[DecodeResult[U]]](monad)(params.asAny.asInstanceOf[T])
    }
  }

  private def cookiesAsHeaders(cookies: Seq[WSCookie]): Map[String, Seq[String]] = {
    Map("Set-Cookie" -> cookies.map(c => s"${c.name}=${c.value}"))
  }

  @scala.annotation.tailrec
  private def setInputParams[I](
      input: EndpointInput[I, _],
      params: Params,
      req: StandaloneWSRequest
  ): Future[StandaloneWSRequest] = {
    def value: I = params.asAny.asInstanceOf[I]
    input match {
      case EndpointInput.FixedMethod(m, _, _) => Future.successful(req.withMethod(m.method))
      case EndpointInput.FixedPath(p, _, _) =>
        Future.successful(req.withUrl(req.url + "/" + p))
      case EndpointInput.PathCapture(_, codec, _) =>
        val v = codec.asInstanceOf[PlainCodec[Any]].encode(value: Any)
        Future.successful(req.withUrl(req.url + "/" + v))
      case EndpointInput.PathsCapture(codec, _) =>
        val ps = codec.encode(value)
        Future.successful(req.withUrl(req.url + ps.mkString("/", "/", "")))
      case EndpointInput.Query(name, codec, _) =>
        val req2 = codec.encode(value).foldLeft(req) { case (r, v) => r.addQueryStringParameters(name -> v) }
        Future.successful(req2)
      case EndpointInput.Cookie(name, codec, _) =>
        val req2 = codec.encode(value).foldLeft(req) { case (r, v) => r.addCookies(DefaultWSCookie(name, v)) }
        Future.successful(req2)
      case EndpointInput.QueryParams(codec, _) =>
        val mqp = codec.encode(value)
        Future.successful(req.addQueryStringParameters(mqp.toSeq: _*))
      case EndpointIO.Empty(_, _) => Future.successful(req)
      case EndpointIO.Body(bodyType, codec, _) =>
        val req2 = setBody(value, bodyType, codec, req)
        Future.successful(req2)
      case EndpointIO.StreamBody(streams, _, _, _) =>
        val req2 = setStreamingBody(streams)(value.asInstanceOf[streams.BinaryStream], req)
        Future.successful(req2)
      case EndpointIO.Header(name, codec, _) =>
        val req2 = codec
          .encode(value)
          .foldLeft(req) { case (r, v) => r.addHttpHeaders(name -> v) }
        Future.successful(req2)
      case EndpointIO.Headers(codec, _) =>
        val headers = codec.encode(value)
        val req2 = headers.foldLeft(req) { case (r, h) => r.addHttpHeaders(h.name -> h.value) }
        Future.successful(req2)
      case EndpointIO.FixedHeader(h, _, _) =>
        val req2 = req.addHttpHeaders(h.name -> h.value)
        Future.successful(req2)
      case EndpointInput.ExtractFromRequest(_, _) =>
        // ignoring
        Future.successful(req)
      case a: EndpointInput.Auth[_, _]               => setInputParams(a.input, params, req)
      case EndpointInput.Pair(left, right, _, split) => handleInputPair(left, right, params, split, req)
      case EndpointIO.Pair(left, right, _, split)    => handleInputPair(left, right, params, split, req)
      case EndpointInput.MapEffect(input, _, g)      => handleMapEffect(input, g, params, req)
      case EndpointIO.MapEffect(input, _, g)         => handleMapEffect(input, g, params, req)
      case EndpointInput.MappedPair(wrapped, codec)  => handleMapped(wrapped, codec.asInstanceOf[Mapping[Any, Any]], params, req)
      case EndpointIO.MappedPair(wrapped, codec)     => handleMapped(wrapped, codec.asInstanceOf[Mapping[Any, Any]], params, req)
    }
  }

  def handleInputPair(
      left: EndpointInput[_, _],
      right: EndpointInput[_, _],
      params: Params,
      split: SplitParams,
      req: StandaloneWSRequest
  ): Future[StandaloneWSRequest] = {
    val (leftParams, rightParams) = split(params)
    setInputParams(left.asInstanceOf[EndpointInput[Any, Any]], leftParams, req).flatMap { req2 =>
      setInputParams(right.asInstanceOf[EndpointInput[Any, Any]], rightParams, req2)
    }
  }

  private def handleMapped[II, T](
      tuple: EndpointInput[II, _],
      codec: Mapping[T, II],
      params: Params,
      req: StandaloneWSRequest
  ): Future[StandaloneWSRequest] = {
    setInputParams(tuple.asInstanceOf[EndpointInput[Any, Any]], ParamsAsAny(codec.encode(params.asAny.asInstanceOf[II])), req)
  }

  private def handleMapEffect[T, U](
      input: EndpointInput[T, _],
      g: MonadError[Any] => Any => Any,
      params: Params,
      req: StandaloneWSRequest
  ): Future[StandaloneWSRequest] = {
    g.asInstanceOf[MonadError[Future] => U => Future[T]](monad)(params.asAny.asInstanceOf[U])
      .flatMap(t => setInputParams(input, ParamsAsAny(t), req))
  }

  //        type PlayPart = play.shaded.ahc.org.asynchttpclient.request.body.multipart.Part
  //        type PlayPartBase = play.shaded.ahc.org.asynchttpclient.request.body.multipart.PartBase

  private def setBody[R, T, CF <: CodecFormat](
      v: T,
      bodyType: RawBodyType[R],
      codec: Codec[R, T, CF],
      req: StandaloneWSRequest
  ): StandaloneWSRequest = {
    val encoded: R = codec.encode(v)
    // TODO can't we get rid of asInstanceOf ?
    val req2 = bodyType match {
      case RawBodyType.StringBody(_) =>
        // Play infer the content-type from the body, if we pass a String, it will infer "text/plain"
        // That's why we create a custom BodyWritable
        // TODO: what about charset?
        val defaultStringBodyWritable: BodyWritable[String] = implicitly[BodyWritable[String]]
        val bodyWritable = BodyWritable[String](defaultStringBodyWritable.transform, codec.format.mediaType.toString)
        req.withBody(encoded.asInstanceOf[String])(bodyWritable)
      case RawBodyType.ByteArrayBody   => req.withBody(encoded.asInstanceOf[Array[Byte]])
      case RawBodyType.ByteBufferBody  => req.withBody(encoded.asInstanceOf[ByteBuffer])
      case RawBodyType.InputStreamBody =>
        // For some reason, Play comes with a Writeable for Supplier[InputStream] but not InputStream directly
        val inputStreamSupplier: Supplier[InputStream] = () => encoded.asInstanceOf[InputStream]
        req.withBody(inputStreamSupplier)
      case RawBodyType.FileBody         => req.withBody(encoded.asInstanceOf[File])
      case m: RawBodyType.MultipartBody =>
//        val parts: Seq[PlayPart] = (encoded: Seq[RawPart]).flatMap { p =>
//          m.partType(p.name).map { partType =>
//            // name, body, content type, content length, file name
//            val playPart =
//              partToPlayPart(p.asInstanceOf[Part[Any]], partType.asInstanceOf[RawBodyType[Any]], p.contentType, p.contentLength, p.fileName)
//
//            // headers; except content type set above
//            p.headers
//              .filterNot(_.is(HeaderNames.ContentType))
//              .foreach { header =>
//                playPart.addCustomHeader(header.name, header.value)
//              }
//
//            playPart
//          }
//        }
//
        // TODO we need a BodyWritable[Source[PlayPart, _]]
        // But it's not part of Play Standalone
        // See https://github.com/playframework/playframework/blob/master/transport/client/play-ws/src/main/scala/play/api/libs/ws/WSBodyWritables.scala
        // req.withBody(Source(parts.toList))

        throw new IllegalArgumentException("Multipart body aren't supported")
    }

    req2
  }

  private def setStreamingBody[S](streams: Streams[S])(v: streams.BinaryStream, req: StandaloneWSRequest): StandaloneWSRequest = {
    streams match {
      case AkkaStreams => req.withBody(v.asInstanceOf[AkkaStreams.BinaryStream])
      case _           => throw new IllegalArgumentException("Only AkkaStreams streaming is supported")
    }
  }

//  private def partToPlayPart[R](
//      p: Part[R],
//      bodyType: RawBodyType[R],
//      contentType: Option[String],
//      contentLength: Option[Long],
//      fileName: Option[String]
//  ): PlayPartBase = {
//    // TODO can't we get rid of the asInstanceOf???
//    bodyType match {
//      case RawBodyType.StringBody(charset) => new StringPart(p.name, p.body.asInstanceOf[String], contentType.orNull, charset)
//      case RawBodyType.ByteArrayBody       => new ByteArrayPart(p.name, p.body.asInstanceOf[Array[Byte]], contentType.orNull)
//      case RawBodyType.ByteBufferBody      => new ByteArrayPart(p.name, p.body.asInstanceOf[ByteBuffer].array(), contentType.orNull)
//      case RawBodyType.InputStreamBody =>
//        new InputStreamPart(p.name, p.body.asInstanceOf[InputStream], fileName.orNull, contentLength.getOrElse(-1L), contentType.orNull)
//      case RawBodyType.FileBody            => new FilePart(p.name, p.body.asInstanceOf[File], contentType.orNull)
//      case RawBodyType.MultipartBody(_, _) => throw new IllegalArgumentException("Nested multipart bodies aren't supported")
//    }
//  }

  private def getOrThrow[T](dr: DecodeResult[T]): T =
    dr match {
      case DecodeResult.Value(v)    => v
      case DecodeResult.Error(_, e) => throw e
      case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
    }

  private def responseFromOutput(out: EndpointOutput[_, _]): StandaloneWSResponse => Any = { response =>
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
            case RawBodyType.FileBody        =>
              // TODO Consider using bodyAsSource to not load the whole content in memory
              val f = clientOptions.createFile()
              val outputStream = Files.newOutputStream(f.toPath)
              outputStream.write(response.body[Array[Byte]])
              outputStream.close()
              f
            case RawBodyType.MultipartBody(_, _) => throw new IllegalArgumentException("Multipart bodies aren't supported in responses")
          }
          .getOrElse(()) // Unit
    }
  }

  private def bodyIsStream[I](out: EndpointOutput[I, _]): Option[Streams[_]] = {
    out.traverseOutputs { case EndpointIO.StreamBody(streams, _, _, _) =>
      Vector(streams)
    }.headOption
  }

  private implicit class FutureDecodeResultSyntax[T, U](tf: Future[DecodeResult[T]]) {
    def mapDecode(f: T => DecodeResult[U]): Future[DecodeResult[U]] = tf.map {
      case DecodeResult.Value(t)         => f(t)
      case failure: DecodeResult.Failure => failure
    }
    def flatMapDecode(f: T => Future[DecodeResult[U]]): Future[DecodeResult[U]] = tf.flatMap {
      case DecodeResult.Value(t)         => f(t)
      case failure: DecodeResult.Failure => Future.successful(failure)
    }
  }
}
