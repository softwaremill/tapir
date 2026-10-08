package sttp.tapir.server.netty.cats

import cats.effect.{IO, Resource}
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.model.IncompleteRequestBodyException
import sttp.tapir.server.tests.CreateServerTest
import sttp.tapir.tests.Test

import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.duration._

class NettyFs2StreamingInterruptedTest[OPTIONS, ROUTE](createServerTest: CreateServerTest[IO, Fs2Streams[IO], OPTIONS, ROUTE]) {
  import createServerTest._

  implicit val m: MonadError[IO] = new CatsMonadError[IO]()

  def tests(): List[Test] = List({
    val logicStarted = new AtomicBoolean(false)
    val readResults = new AtomicReference(Vector.empty[Either[Throwable, String]])

    testServerLogic(
      endpoint.put
        .in("streamInterrupted")
        .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))
        .serverLogicSuccess[IO] { body =>
          IO.uncancelable { _ =>
            IO(logicStarted.set(true)) >>
              body
                .through(fs2.text.utf8.decode)
                .compile
                .string
                .attempt
                .flatMap(result => IO(readResults.getAndUpdate(_ :+ result)).void)
          }
        },
      "closing the connection mid-body fails the request body stream"
    ) { (_, baseUri) =>
      val port = baseUri.port.get
      val requestHead =
        s"PUT /streamInterrupted HTTP/1.1\r\nHost: localhost:$port\r\nContent-Type: text/plain\r\nContent-Length: 10000\r\n\r\n"
          .getBytes(StandardCharsets.UTF_8)

      Resource
        .fromAutoCloseable(IO.blocking(createSocket(port)))
        .use { socket =>
          send(socket, requestHead) >> awaitUntil(logicStarted.get()) >>
            send(socket, "abcd".getBytes(StandardCharsets.UTF_8)) >> IO.blocking(socket.shutdownOutput()) >>
            awaitUntil(readResults.get().nonEmpty)
        }
        .map(_ => readResults.get() shouldBe Vector(Left(IncompleteRequestBodyException(4, 10000))))
    }
  })

  private def awaitUntil(condition: => Boolean): IO[Unit] =
    (IO.sleep(10.millis) >> IO(condition)).iterateUntil(identity).timeout(5.seconds).void

  private def send(socket: Socket, bytes: Array[Byte]): IO[Unit] =
    IO.blocking {
      socket.getOutputStream.write(bytes)
      socket.getOutputStream.flush()
    }

  def createSocket(port: Int): Socket = {
    val socket = new Socket("localhost", port)
    socket.setSoTimeout(5000)
    socket
  }
}
