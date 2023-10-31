package sttp.tapir.server.netty.cats

import cats.effect.IO
import cats.syntax.all._
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.monad.MonadError
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.tests.{CreateServerTest, _}
import sttp.tapir.tests._
import sttp.tapir.{CodecFormat, _}

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global

class NettyFs2StreamingCancellationTest[OPTIONS, ROUTE](createServerTest: CreateServerTest[IO, Fs2Streams[IO], OPTIONS, ROUTE]) {
  import createServerTest._

  implicit val m: MonadError[IO] = new CatsMonadError[IO]()
  def tests(): List[Test] = List({
    val buffer = Queue.unbounded[IO, Byte].unsafeRunSync()
    val body_20_slowly_emitted_bytes =
      fs2.Stream.awakeEvery[IO](100.milliseconds).map(_ => 42.toByte).evalMap(b => { buffer.offer(b) >> IO.pure(b) }).take(100)
    testServer(
      endpoint.get
        .in("streamCanceled")
        .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))),
      "Client cancelling streaming triggers cancellation on the server"
    )(_ => pureResult(body_20_slowly_emitted_bytes.asRight[Unit])) { (backend, baseUri) =>

      val expectedMaxAccumulated = 3

      basicRequest
        .get(uri"$baseUri/streamCanceled")
        .send(backend)
        .timeout(300.millis)
        .attempt >>
        IO.sleep(600.millis)
          .flatMap(_ =>
            buffer.size.flatMap(accumulated =>
              IO(
                assert(
                  accumulated <= expectedMaxAccumulated,
                  s"Buffer accumulated $accumulated elements. Expected < $expectedMaxAccumulated due to cancellation."
                )
              )
            )
          )
    }
  })
}
