package sttp.tapir.server.netty.cats

import cats.effect.IO
import cats.effect.kernel.Resource.ExitCase
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.monad.MonadError
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.tests.{CreateServerTest, _}
import sttp.tapir.tests._
import sttp.tapir.{CodecFormat, _}

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

class NettyFs2StreamingCancellationTest[OPTIONS, ROUTE](createServerTest: CreateServerTest[IO, Fs2Streams[IO], OPTIONS, ROUTE])
    extends EitherValues {
  import createServerTest._

  implicit val m: MonadError[IO] = new CatsMonadError[IO]()

  def tests(): List[Test] = List({
    val buffer = Queue.unbounded[IO, Option[Byte]].unsafeRunSync()

    def readBuffer: IO[List[Byte]] =
      fs2.Stream.fromQueueNoneTerminated(buffer).compile.toList

    val body_20_slowly_emitted_bytes =
      fs2.Stream
        .awakeEvery[IO](100.milliseconds)
        .map(_ => 42.toByte)
        .onFinalizeCase {
          case ExitCase.Canceled => buffer.offer(None)
          case _                 => IO.unit
        }

    testServer(
      endpoint.get
        .in("streamCanceled")
        .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))),
      "Client cancelling streaming triggers cancellation on the server"
    )(_ => pureResult(body_20_slowly_emitted_bytes.asRight[Unit])) { (backend, baseUri) =>
      // How this test works:
      // 1. The endpoint emits a byte continuously every 100 millis
      // 2. The client connects and reads bytes, putting them in a buffer
      // 3. The client cancels and disconnects after 1 second (using .timeout on the stream draining operation)
      // 4. The endpoint logic reacts to cancelation and signals the end of the buffer (by putting a None in it)
      // 5. The client tries to read all bytes from the buffer, which would fail with a timeout if the None element from point 4. wasn't triggered correctly
      basicRequest
        .get(uri"$baseUri/streamCanceled")
        .response(asStreamUnsafe(Fs2Streams[IO]))
        .send(backend)
        .flatMap(_.body.value.evalMap(b => buffer.offer(Some(b))).compile.drain)
        .timeout(1000.millis)
        .attempt
        .void >> readBuffer.timeout(5.seconds).map(bytes => assert(bytes.length > 1))
    }
  })
}
