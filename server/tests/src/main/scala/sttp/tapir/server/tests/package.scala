package sttp.tapir.server

import cats.effect.{IO, Resource}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.client4.{PartialRequest, WebSocketStreamBackend, asStringAlways, basicRequest}
import sttp.monad.MonadError

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.Socket
import java.nio.charset.StandardCharsets

package object tests {
  val backendResource: Resource[IO, WebSocketStreamBackend[IO, Fs2Streams[IO]]] = HttpClientFs2Backend.resource()
  val basicStringRequest: PartialRequest[String] = basicRequest.response(asStringAlways)
  def pureResult[F[_]: MonadError, T](t: T): F[T] = MonadError[F].unit(t)
  def suspendResult[F[_]: MonadError, T](t: => T): F[T] = MonadError[F].eval(t)
  def blockingResult[F[_]: MonadError, T](t: => T): F[T] = MonadError[F].blocking(t)

  /** For tests which need to control when each part of a request is sent, or to see interim (`1xx`) responses, which HTTP clients hide.
    * Reads fail if nothing arrives for 10s, so that a missing response fails the test instead of hanging it.
    */
  def rawSocket(port: Int): Resource[IO, Socket] =
    Resource.fromAutoCloseable(IO.blocking {
      val socket = new Socket("localhost", port)
      socket.setSoTimeout(10000)
      socket
    })

  def sendRaw(socket: Socket, bytes: Array[Byte]): IO[Unit] =
    IO.blocking {
      socket.getOutputStream.write(bytes)
      socket.getOutputStream.flush()
    }

  /** Reads a single response, returning its status code and body. Doesn't read past the end of the response. */
  def readRawResponse(socket: Socket): IO[(Int, String)] = IO.blocking {
    val in = socket.getInputStream
    val statusCode = readRawLine(in).split(' ')(1).toInt
    val headers = Iterator.continually(readRawLine(in)).takeWhile(_.nonEmpty).map(_.toLowerCase).toList
    def header(name: String) = headers.collectFirst { case h if h.startsWith(name + ":") => h.substring(name.length + 1).trim }

    val body =
      if (header("transfer-encoding").contains("chunked")) {
        val out = new ByteArrayOutputStream()
        def readChunkSize() = Integer.parseInt(readRawLine(in).takeWhile(_ != ';').trim, 16)
        var chunkSize = readChunkSize()
        while (chunkSize > 0) {
          out.write(readRawBytes(in, chunkSize))
          val _ = readRawLine(in) // the CRLF after the chunk
          chunkSize = readChunkSize()
        }
        Iterator.continually(readRawLine(in)).takeWhile(_.nonEmpty).foreach(_ => ()) // trailers
        out.toByteArray
      } else
        header("content-length") match {
          case Some(length)                                                       => readRawBytes(in, length.toInt)
          case None if statusCode < 200 || statusCode == 204 || statusCode == 304 => Array.emptyByteArray
          case None => in.readAllBytes() // delimited by closing the connection
        }

    (statusCode, new String(body, StandardCharsets.UTF_8))
  }

  private def readRawLine(in: InputStream): String = {
    val line = new StringBuilder
    var c = in.read()
    while (c != '\n') {
      if (c == -1) throw new IllegalStateException(s"Connection closed, after reading: $line")
      if (c != '\r') line.append(c.toChar)
      c = in.read()
    }
    line.toString
  }

  private def readRawBytes(in: InputStream, n: Int): Array[Byte] = {
    val bytes = in.readNBytes(n)
    if (bytes.length < n) throw new IllegalStateException(s"Connection closed, after reading ${bytes.length} of $n bytes")
    bytes
  }
}
