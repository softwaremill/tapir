package sttp.tapir.server.vertx.encoders

import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.{HttpHeaders, HttpServerResponse}
import io.vertx.ext.web.RoutingContext
import sttp.capabilities.Streams
import sttp.model.{HasHeaders, Part}
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.vertx.VertxServerOptions
import sttp.tapir.server.vertx.streams.{Pipe, ReadStreamCompatible}

import java.io.{File, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset

class VertxToResponseBody[F[_], S: ReadStreamCompatible](serverOptions: VertxServerOptions[F])
    extends ToResponseBody[RoutingContext => Unit, S] {
  private val readStreamCompatible = ReadStreamCompatible[S]
  override val streams: Streams[S] = readStreamCompatible.streams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): RoutingContext => Unit = { rc =>
    val resp = rc.response
    bodyType match {
      case RawBodyType.StringBody(charset) => resp.end(v.toString, charset.toString)
      case RawBodyType.ByteArrayBody       => resp.end(Buffer.buffer(v.asInstanceOf[Array[Byte]]))
      case RawBodyType.ByteBufferBody      => resp.end(Buffer.buffer().setBytes(0, v.asInstanceOf[ByteBuffer]))
      case RawBodyType.InputStreamBody =>
        inputStreamToBuffer(v.asInstanceOf[InputStream], rc.vertx).flatMap(resp.end)
      case RawBodyType.FileBody         => resp.sendFile(v.asInstanceOf[File].getPath)
      case m: RawBodyType.MultipartBody => handleMultipleBodyParts(m, v)(serverOptions)(rc)
    }
    ()
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): RoutingContext => Unit = { rc =>
    Pipe(readStreamCompatible.asReadStream(v.asInstanceOf[readStreamCompatible.streams.BinaryStream]), rc.response)
  }

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, S]
  ): RoutingContext => Unit = throw new UnsupportedOperationException()

  private def handleMultipleBodyParts[CF <: CodecFormat, R](
      multipart: RawBodyType[R] with RawBodyType.MultipartBody,
      r: R
  )(implicit endpointOptions: VertxServerOptions[F]): RoutingContext => Future[Void] = { rc =>
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
      endpointOptions: VertxServerOptions[F]
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
    part.headers.foreach { h => resp.headers.add(h.name, h.value) }
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
      endpointOptions: VertxServerOptions[F]
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
