package sttp.tapir.server

import cats.effect.{IO, Resource}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.client4.{PartialRequest, WebSocketStreamBackend, asStringAlways, basicRequest}
import sttp.monad.MonadError

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.{ConnectException, Socket}
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

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

  def rawGet(path: String = "/"): Array[Byte] = s"GET $path HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

  def sendRaw(socket: Socket, bytes: Array[Byte]): IO[Unit] =
    IO.blocking {
      socket.getOutputStream.write(bytes)
      socket.getOutputStream.flush()
    }

  def connectionRefused(port: Int): IO[Boolean] =
    IO.blocking(new Socket("localhost", port).close()).attempt.map(_.left.exists(_.isInstanceOf[ConnectException]))

  /** Waits until connections are refused. Netty servers stop listening once they start draining, so this waits until they are draining. */
  def awaitConnectionsRefused(port: Int): IO[Unit] =
    (IO.sleep(100.millis) >> connectionRefused(port)).iterateUntil(identity).void

  /** Reads a single response, returning its status code and body. Doesn't read past the end of the response. */
  def readRawResponse(socket: Socket): IO[(Int, String)] =
    readRawResponseWithHeaders(socket).map { case (statusCode, _, body) => (statusCode, body) }

  /** As [[readRawResponse]], also returning the headers, lower-cased. */
  def readRawResponseWithHeaders(socket: Socket): IO[(Int, List[String], String)] = IO.blocking {
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

    (statusCode, headers, new String(body, StandardCharsets.UTF_8))
  }

  /** Runs `run`, returning the threads named `threadNamePrefix*` which it starts, and which are still alive 5s after it ends. */
  def threadsLeftBy(threadNamePrefix: String)(run: IO[Any]): IO[Set[Thread]] = {
    def threads(): Set[Thread] =
      Thread.getAllStackTraces.keySet.toArray(new Array[Thread](0)).filter(_.getName.startsWith(threadNamePrefix)).toSet
    for {
      before <- IO(threads())
      _ <- run
      leftAlive <- IO.blocking((threads() -- before).filter { t => t.join(5000); t.isAlive })
    } yield leftAlive
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
