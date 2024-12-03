package sttp.tapir.server.netty.cats

import cats.effect.{IO, Resource}
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import io.netty.channel.EventLoopGroup
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.model.HeaderNames
import sttp.tapir._
import sttp.tapir.server.netty.NettyConfig
import sttp.tapir.tests.Test

import scala.concurrent.duration.DurationInt

class NettyCatsRequestTimeoutTest(
    dispatcher: Dispatcher[IO],
    eventLoopGroup: EventLoopGroup,
    backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets]
) {
  def tests(): List[Test] = List(
    Test("chunked transmission lasts longer than given timeout") {
      val givenRequestTimeout = 2.seconds
      val howManyChunks: Int = 2
      val chunkSize = 100
      val millisBeforeSendingSecondChunk = 1000L

      val e =
        endpoint.post
          .in(header[Long](HeaderNames.ContentLength))
          .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
          .out(header[Long](HeaderNames.ContentLength))
          .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
          .serverLogicSuccess[IO] { case (length, stream) =>
            IO((length, stream))
          }

      val config =
        NettyConfig.default
          .eventLoopGroup(eventLoopGroup)
          .randomPort
          .withDontShutdownEventLoopGroupOnClose
          .noGracefulShutdown
          .requestTimeout(givenRequestTimeout)

      val bind = NettyCatsServer(dispatcher, config).addEndpoint(e).start()

      def iterator(howManyChunks: Int, chunkSize: Int): Iterator[Byte] = new Iterator[Iterator[Byte]] {
        private var chunksToGo: Int = howManyChunks

        def hasNext: Boolean = {
          if (chunksToGo == 1)
            Thread.sleep(millisBeforeSendingSecondChunk)
          chunksToGo > 0
        }

        def next(): Iterator[Byte] = {
          chunksToGo -= 1
          List.fill('A')(chunkSize).map(_.toByte).iterator
        }
      }.flatten

      val inputStream = fs2.Stream.fromIterator[IO](iterator(howManyChunks, chunkSize), chunkSize = chunkSize)

      Resource
        .make(bind)(_.stop())
        .map(_.port)
        .use { port =>
          basicRequest
            .post(uri"http://localhost:$port")
            .contentLength(howManyChunks * chunkSize)
            .streamBody(Fs2Streams[IO])(inputStream)
            .send(backend)
            .map { _ =>
              fail("I've got a bad feeling about this.")
            }
        }
        .attempt
        .map {
          case Left(ex: sttp.client3.SttpClientException.TimeoutException) =>
            ex.getCause.getMessage shouldBe "request timed out"
          case Left(ex: sttp.client3.SttpClientException.ReadException) if ex.getCause.isInstanceOf[java.io.IOException] =>
            println(s"Unexpected IOException: $ex")
            fail(s"Unexpected IOException: $ex")
          case Left(ex) =>
            fail(s"Unexpected exception: $ex")
          case Right(_) =>
            fail("Expected an exception but got success")
        }
        .unsafeToFuture()
    }
  )
}
