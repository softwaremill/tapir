package sttp.tapir.server.netty.cats

import cats.effect.{Deferred, IO, Resource}
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.model.IncompleteRequestBodyException
import sttp.tapir.server.tests.CreateServerTest
import sttp.tapir.tests.Test

import java.io.{BufferedReader, InputStreamReader}
import java.net.Socket
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

class NettyFs2StreamingRequestBodyTest[OPTIONS, ROUTE](createServerTest: CreateServerTest[IO, Fs2Streams[IO], OPTIONS, ROUTE]) {
  import createServerTest._

  implicit val m: MonadError[IO] = new CatsMonadError[IO]()

  def tests(): List[Test] = List(
    {
      val logicStarted = Deferred.unsafe[IO, Unit]
      val readResult = Deferred.unsafe[IO, Either[Throwable, String]]

      testServerLogic(
        endpoint.put
          .in("streamInterrupted")
          .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))
          .serverLogicSuccess[IO] { body =>
            IO.uncancelable { _ =>
              logicStarted.complete(()) >>
                body
                  .through(fs2.text.utf8.decode)
                  .compile
                  .string
                  .attempt
                  .flatMap(readResult.complete(_).void)
            }
          },
        "closing the connection mid-body fails the request body stream"
      ) { (_, baseUri) =>
        val port = baseUri.port.get
        val requestHead =
          s"PUT /streamInterrupted HTTP/1.1\r\nHost: localhost:$port\r\nContent-Type: text/plain\r\nContent-Length: 10000\r\n\r\n"
            .getBytes(StandardCharsets.UTF_8)

        Resource
          .fromAutoCloseable(IO.blocking(new Socket("localhost", port)))
          .use { socket =>
            send(socket, requestHead) >> logicStarted.get.timeout(5.seconds) >>
              send(socket, "abcd".getBytes(StandardCharsets.UTF_8)) >> IO.blocking(socket.shutdownOutput()) >>
              readResult.get.timeout(5.seconds)
          }
          .map(_ shouldBe Left(IncompleteRequestBodyException(4, 10000)))
      }
    },
    // #5503: the request body is only pulled while the response body is written, i.e. after the response headers
    testServerLogic(
      endpoint.put
        .in("expectContinueEcho")
        .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))
        .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))
        .serverLogicSuccess[IO](body => IO.pure(body)),
      "request body echoed as a stream is sent after 100 Continue"
    ) { (_, baseUri) =>
      val port = baseUri.port.get
      val requestHead =
        s"PUT /expectContinueEcho HTTP/1.1\r\nHost: localhost:$port\r\nContent-Type: text/plain\r\nContent-Length: 4\r\nExpect: 100-continue\r\n\r\n"
          .getBytes(StandardCharsets.UTF_8)

      Resource
        .fromAutoCloseable(IO.blocking(new Socket("localhost", port)))
        .use { socket =>
          for {
            _ <- IO.blocking(socket.setSoTimeout(10000))
            _ <- send(socket, requestHead)
            continue <- readChunkedResponse(socket)
            _ <- send(socket, "abcd".getBytes(StandardCharsets.UTF_8))
            response <- readChunkedResponse(socket)
          } yield List(continue, response)
        }
        .map(_ shouldBe List(("HTTP/1.1 100 Continue", ""), ("HTTP/1.1 200 OK", "abcd")))
    }
  )

  /** Reads the status line and the chunked body (if any) of a response. Each call has its own reader, which is safe only if the server
    * doesn't send anything more until the client sends the next request part.
    */
  private def readChunkedResponse(socket: Socket): IO[(String, String)] = IO.blocking {
    val in = new BufferedReader(new InputStreamReader(socket.getInputStream, StandardCharsets.UTF_8))
    val statusLine = in.readLine()
    val headers = Iterator.continually(in.readLine()).takeWhile(line => line != null && line.nonEmpty).toList
    val body = new StringBuilder
    if (headers.exists(_.equalsIgnoreCase("transfer-encoding: chunked"))) {
      var chunkSize = Integer.parseInt(in.readLine(), 16)
      while (chunkSize > 0) {
        body.append(in.readLine())
        chunkSize = Integer.parseInt(in.readLine(), 16)
      }
    }
    (statusLine, body.toString)
  }

  private def send(socket: Socket, bytes: Array[Byte]): IO[Unit] =
    IO.blocking {
      socket.getOutputStream.write(bytes)
      socket.getOutputStream.flush()
    }

}
