package sttp.tapir.server.tests

import cats.effect.{IO, Resource}

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.Socket
import java.nio.charset.StandardCharsets

/** A minimal HTTP/1.1 client over a raw socket, for tests that need control over when each part of a request is sent, or need to see
  * interim (`1xx`) responses, which HTTP clients handle transparently.
  */
object RawHttpClient {
  case class ResponseHead(statusLine: String, headers: List[String]) {
    def contentLength: Int = header("content-length").map(_.toInt).getOrElse(0)
    def chunked: Boolean = header("transfer-encoding").exists(_.equalsIgnoreCase("chunked"))

    private def header(name: String): Option[String] =
      headers.collectFirst { case h if h.toLowerCase.startsWith(s"$name:") => h.substring(name.length + 1).trim }
  }
  case class Response(head: ResponseHead, body: String)

  /** A socket with a read timeout, so that a server which doesn't respond fails the test instead of hanging it. */
  def clientSocket(port: Int, readTimeoutMillis: Int = 10000): Resource[IO, Socket] =
    Resource.fromAutoCloseable(IO.blocking {
      val socket = new Socket("localhost", port)
      socket.setSoTimeout(readTimeoutMillis)
      socket
    })

  /** The head of a `PUT` request with a `text/plain` body; `Content-Length` is computed from `body`, which is not included. */
  def requestHead(port: Int, path: String, body: String, extraHeaders: List[String]): Array[Byte] = {
    val headers = List(
      s"Host: localhost:$port",
      "Content-Type: text/plain",
      s"Content-Length: ${body.getBytes(StandardCharsets.UTF_8).length}"
    ) ++ extraHeaders
    s"PUT $path HTTP/1.1\r\n${headers.map(_ + "\r\n").mkString}\r\n".getBytes(StandardCharsets.UTF_8)
  }

  def send(socket: Socket, bytes: Array[Byte]): IO[Unit] =
    IO.blocking {
      socket.getOutputStream.write(bytes)
      socket.getOutputStream.flush()
    }

  /** Reads a response with a `Content-Length` or chunked body (or no body). */
  def readResponse(in: InputStream): IO[Response] =
    readResponseHead(in).flatMap { head =>
      IO.blocking {
        val body = if (head.chunked) readChunkedBody(in) else readBytes(in, head.contentLength)
        Response(head, new String(body, StandardCharsets.UTF_8))
      }
    }

  def readResponseHead(in: InputStream): IO[ResponseHead] = IO.blocking {
    val statusLine = readLine(in)
    val headers = Iterator.continually(readLine(in)).takeWhile(_.nonEmpty).toList
    ResponseHead(statusLine, headers)
  }

  private def readBytes(in: InputStream, count: Int): Array[Byte] = {
    val bytes = new Array[Byte](count)
    var read = 0
    while (read < count) {
      val n = in.read(bytes, read, count - read)
      if (n == -1) throw new IllegalStateException(s"Connection closed after $read of $count body bytes")
      read += n
    }
    bytes
  }

  private def readChunkedBody(in: InputStream): Array[Byte] = {
    val body = new ByteArrayOutputStream()
    var chunkSize = Integer.parseInt(readLine(in).trim, 16)
    while (chunkSize > 0) {
      body.write(readBytes(in, chunkSize))
      val _ = readLine(in) // the CRLF ending the chunk
      chunkSize = Integer.parseInt(readLine(in).trim, 16)
    }
    Iterator.continually(readLine(in)).takeWhile(_.nonEmpty).foreach(_ => ()) // trailers
    body.toByteArray
  }

  // reading byte-by-byte, so that no bytes of the following body or response are buffered and lost
  private def readLine(in: InputStream): String = {
    val line = new ByteArrayOutputStream()
    var b = in.read()
    while (b != '\n') {
      if (b == -1) throw new IllegalStateException("Connection closed while reading a response line")
      if (b != '\r') line.write(b)
      b = in.read()
    }
    new String(line.toByteArray, StandardCharsets.US_ASCII)
  }
}
