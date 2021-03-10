package sttp.tapir.server.vertx.encoders

import java.io.{File, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.streams.ReadStream
import io.vertx.core.Future
import io.vertx.ext.web.RoutingContext
import sttp.capabilities.Streams
import sttp.model.{Header, Part}
import sttp.tapir.internal.{ParamsAsAny, charset}
import sttp.tapir.server.ServerDefaults
import sttp.tapir.server.interpreter.{EncodeOutputBody, EncodeOutputs, OutputValues}
import sttp.tapir.server.vertx.streams.{Pipe, ReadStreamCompatible}
import sttp.tapir.server.vertx.VertxEndpointOptions
import sttp.tapir.{CodecFormat, EndpointOutput, RawBodyType, WebSocketBodyOutput}

import scala.util.control.NonFatal

/** All the necessary methods to write Endpoint.outputs to Vert.x HttpServerResponse
  * Contains:
  *   - Headers handling
  *   - Body handling (string, binaries, files, multipart, ...)
  *   - Stream handling
  */
object VertxOutputEncoders {

  type RoutingContextHandler = RoutingContext => Unit
  type RoutingContextHandlerWithLength = Option[Long] => RoutingContext => Unit

  /** Creates a function, that given a RoutingContext will write the result to its response, according to the endpoint definition
    * @param output the endpoint definition
    * @param result the result to encode
    * @param isError boolean indicating if the result is an error or not
    * @param logWhenHandled an optional function that will be invoked once the response has been successfully written, with the status code returned
    * @tparam O type of the result to encode
    * @return a function, that given a RoutingContext will write the output to its HTTP response
    */
  private[vertx] def apply[O, S: ReadStreamCompatible](
      output: EndpointOutput[O],
      result: O,
      isError: Boolean = false,
      logWhenHandled: Int => Unit = { _ => }
  )(implicit endpointOptions: VertxEndpointOptions): RoutingContextHandler = { rc =>
    val resp = rc.response
    val options: OutputValues[RoutingContextHandlerWithLength, Nothing] = OutputValues.empty
    try {
      var outputValues = encodeOutputs.apply(output, ParamsAsAny(result), options)
      if (isError && outputValues.statusCode.isEmpty) {
        outputValues = outputValues.withStatusCode(ServerDefaults.StatusCodes.error)
      }
      setStatus(outputValues)(resp)
      forwardHeaders(outputValues)(resp)
      outputValues.body match {
        case Some(responseHandler) => responseHandler.merge(outputValues.contentLength)(rc)
        case None                  => resp.end()
      }
      logWhenHandled(resp.getStatusCode)
    } catch {
      case NonFatal(e) =>
        endpointOptions.logger.error("Internal error", e)
        rc.fail(e)
    }
  }

  private def formatToContentType(format: CodecFormat, maybeCharset: Option[Charset]): String = {
    val charset = format match {
      case CodecFormat.TextPlain() => maybeCharset.orElse(Some(StandardCharsets.UTF_8))
      case CodecFormat.TextHtml()  => maybeCharset.orElse(Some(StandardCharsets.UTF_8))
      case _                       => None
    }
    charset.fold(format.mediaType)(format.mediaType.charset(_)).toString // README: Charset doesn't seem to be accepted in tests
  }

  private def forwardHeaders(outputValues: OutputValues[RoutingContextHandlerWithLength, Nothing])(resp: HttpServerResponse): Unit = {
    outputValues.headers.foreach { case (k, v) => resp.headers.add(k, v) }
    if (!resp.headers.contains(HttpHeaders.CONTENT_LENGTH.toString)) {
      outputValues.contentLength.foreach { length => resp.headers.add(HttpHeaders.CONTENT_LENGTH.toString, length.toString) }
    }
  }

  private def forwardHeaders(headers: Seq[Header], resp: HttpServerResponse): Unit =
    headers.foreach { h => resp.headers.add(h.name, h.value) }

  private def setStatus[O](outputValues: OutputValues[O, Nothing])(resp: HttpServerResponse): Unit =
    outputValues.statusCode.map(_.code).foreach(resp.setStatusCode)

  private def encodeOutputs[S](implicit
      endpointOptions: VertxEndpointOptions,
      vertxCompatiable: ReadStreamCompatible[S]
  ): EncodeOutputs[RoutingContextHandlerWithLength, Nothing, S] =
    new EncodeOutputs[RoutingContextHandlerWithLength, Nothing, S](
      new EncodeOutputBody[RoutingContextHandlerWithLength, Nothing, S] {
        override val streams: Streams[S] = vertxCompatiable.streams
        override def rawValueToBody[R](v: R, format: CodecFormat, bodyType: RawBodyType[R]): RoutingContextHandlerWithLength =
          _ =>
            BodyEncoders(bodyType.asInstanceOf[RawBodyType[Any]], formatToContentType(format, charset(bodyType)), v)(endpointOptions)(
              _
            )

        override def streamValueToBody(
            v: streams.BinaryStream,
            format: CodecFormat,
            charset: Option[Charset]
        ): RoutingContextHandlerWithLength = { contentLength =>
          StreamEncoders(
            formatToContentType(format, charset),
            vertxCompatiable.asReadStream(v.asInstanceOf[vertxCompatiable.streams.BinaryStream]),
            contentLength
          )(_)
        }

        override def webSocketPipeToBody[REQ, RESP](
            pipe: streams.Pipe[REQ, RESP],
            o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, S]
        ): Nothing =
          ???
      }
    )

  private object BodyEncoders {
    def apply[CF <: CodecFormat, R](bodyType: RawBodyType[R], contentType: String, r: R)(implicit
        endpointOptions: VertxEndpointOptions
    ): RoutingContextHandler = { rc =>
      val resp = rc.response
      if (resp.headers.get(HttpHeaders.CONTENT_TYPE.toString) == null) {
        resp.putHeader(HttpHeaders.CONTENT_TYPE.toString, contentType)
      }
      handleFullBody(bodyType, r)(endpointOptions)(rc): Unit
    }

    private def handleFullBody[R](bodyType: RawBodyType[R], r: R)(implicit
        endpointOptions: VertxEndpointOptions
    ): RoutingContext => Future[Void] = { rc =>
      val resp = rc.response
      bodyType match {
        case RawBodyType.StringBody(charset) => resp.end(r.toString, charset.toString)
        case RawBodyType.ByteArrayBody       => resp.end(Buffer.buffer(r.asInstanceOf[Array[Byte]]))
        case RawBodyType.ByteBufferBody      => resp.end(Buffer.buffer().setBytes(0, r.asInstanceOf[ByteBuffer]))
        case RawBodyType.InputStreamBody =>
          inputStreamToBuffer(r.asInstanceOf[InputStream], rc.vertx).flatMap(resp.end)
        case RawBodyType.FileBody         => resp.sendFile(r.asInstanceOf[File].getPath)
        case m: RawBodyType.MultipartBody => handleMultipleBodyParts(m, r)(endpointOptions)(rc)
      }
    }

    private def handleMultipleBodyParts[CF <: CodecFormat, R](
        multipart: RawBodyType[R] with RawBodyType.MultipartBody,
        r: R
    )(implicit endpointOptions: VertxEndpointOptions): RoutingContext => Future[Void] = { rc =>
      val resp = rc.response
      resp.setChunked(true)
      resp.putHeader(HttpHeaders.CONTENT_TYPE.toString, "multipart/form-data")

      r.asInstanceOf[Seq[Part[_]]]
        .foldLeft(Future.succeededFuture[Void]())({ (acc, part) =>
          acc.flatMap { _ =>
            handleBodyPart(multipart, part)(endpointOptions)(rc)
          }
        })
        .flatMap { _ =>
          resp.end()
        }
    }

    private def handleBodyPart[T](m: RawBodyType.MultipartBody, part: Part[T])(implicit
        endpointOptions: VertxEndpointOptions
    ): RoutingContext => Future[Void] = { rc =>
      val resp = rc.response
      m.partType(part.name)
        .map { partType =>
          writePartHeaders(part)(resp).flatMap { contentType =>
            writeBodyPart(partType.asInstanceOf[RawBodyType[Any]], contentType, part.body)(endpointOptions)(rc)
          }
        }
        .getOrElse(Future.succeededFuture[Void]())
    }

    private def writePartHeaders(part: Part[_]): HttpServerResponse => Future[String] = { resp =>
      forwardHeaders(part.headers, resp)
      val partContentType = part.contentType.getOrElse("application/octet-stream")
      val dispositionParams = part.otherDispositionParams + (Part.NameDispositionParam -> part.name)
      val dispositionsHeaderParts = dispositionParams.map { case (k, v) => s"""$k="$v"""" }
      resp
        .write(s"${HttpHeaders.CONTENT_DISPOSITION}: form-data; ${dispositionsHeaderParts.mkString(", ")}")
        .flatMap(_ => resp.write("\n"))
        .flatMap(_ => resp.write(s"${HttpHeaders.CONTENT_TYPE}: $partContentType"))
        .flatMap(_ => resp.write("\n\n"))
        .flatMap(_ => Future.succeededFuture(partContentType))
    }

    private def writeBodyPart[CF <: CodecFormat, R](bodyType: RawBodyType[R], contentType: String, r: R)(implicit
        endpointOptions: VertxEndpointOptions
    ): RoutingContext => Future[Void] = { rc =>
      val resp = rc.response
      resp
        .write(s"${HttpHeaders.CONTENT_TYPE}: $contentType")
        .flatMap(_ => resp.write("\n"))
        .flatMap { _ =>
          bodyType match {
            case RawBodyType.StringBody(charset) =>
              resp.write(r.toString, charset.toString)
            case RawBodyType.ByteArrayBody =>
              resp.write(Buffer.buffer(r.asInstanceOf[Array[Byte]]))
            case RawBodyType.ByteBufferBody =>
              resp.write(Buffer.buffer.setBytes(0, r.asInstanceOf[ByteBuffer]))
            case RawBodyType.InputStreamBody =>
              inputStreamToBuffer(r.asInstanceOf[InputStream], rc.vertx).flatMap(resp.write)
            case RawBodyType.FileBody =>
              val file = r.asInstanceOf[File]
              rc.vertx.fileSystem
                .readFile(file.getAbsolutePath)
                .flatMap { buf =>
                  resp
                    .write(s"""${HttpHeaders.CONTENT_DISPOSITION.toString}: file; file="${file.getName}"""")
                    .flatMap(_ => resp.write("\n"))
                    .flatMap(_ => resp.write(buf))
                    .flatMap(_ => resp.write("\n\n"))
                }

            case m: RawBodyType.MultipartBody => handleMultipleBodyParts(m, r)(endpointOptions)(rc)
          }

        }
    }
  }

  private object StreamEncoders {
    def apply(contentType: String, stream: ReadStream[Buffer], contentLength: Option[Long]): RoutingContextHandler = { rc =>
      val resp = rc.response

      resp.putHeader(HttpHeaders.CONTENT_TYPE.toString, contentType)
      contentLength match {
        case Some(length) =>
          resp.putHeader(HttpHeaders.CONTENT_LENGTH.toString, length.toString)
        case None =>
          resp.setChunked(true)
      }

      Pipe(stream, resp)
    }
  }

}
