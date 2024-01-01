package sttp.tapir.server.vertx.cats.streams

import _root_.fs2.{Chunk, Stream}
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import cats.effect._
import cats.syntax.option._
import io.vertx.core.buffer.Buffer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable
import sttp.tapir.server.vertx.cats.{VertxCatsServerOptions, streams}
import sttp.tapir.server.vertx.streams.FakeStream

import java.nio.ByteBuffer
import scala.concurrent.duration._

class Fs2StreamTest extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  private val (dispatcher, shutdownDispatcher) = Dispatcher[IO].allocated.unsafeRunSync()

  override protected def afterAll(): Unit = {
    shutdownDispatcher.unsafeRunSync()
    super.afterAll()
  }

  val options: VertxCatsServerOptions[IO] = VertxCatsServerOptions.default(dispatcher).copy(maxQueueSizeForReadStream = 4)

  def intAsBuffer(int: Int): Chunk[Byte] = {
    val buffer = ByteBuffer.allocate(4)
    buffer.putInt(int)
    buffer.flip()
    Chunk.array(buffer.array)
  }

  def intAsVertxBuffer(int: Int): Buffer =
    Buffer.buffer(intAsBuffer(int).toArray)

  def bufferAsInt(buffer: Buffer): Int = {
    val bs = buffer.getBytes()
    (bs(0) & 0xff) << 24 | (bs(1) & 0xff) << 16 | (bs(2) & 0xff) << 8 | (bs(3) & 0xff)
  }

  def chunkAsInt(chunk: Chunk[Byte]): Int =
    bufferAsInt(Buffer.buffer(chunk.toArray))

  def shouldIncreaseMonotonously(xs: List[Int]): Unit = {
    all(xs.iterator.sliding(2).map(_.toList).toList) should matchPattern {
      case (first: Int) :: (second: Int) :: Nil if first + 1 == second =>
    }
    ()
  }

  def eventually[A](io: IO[A])(cond: PartialFunction[A, Unit]): IO[A] = {
    val frequency = 50.millis
    val timeout = 15.seconds
    val maxAttempts = (timeout / frequency).toInt

    def internal(attempts: Int): IO[A] =
      io.flatTap(a => IO.delay(cond(a)))
        .handleErrorWith { case e =>
          if (attempts < maxAttempts) {
            Temporal[IO].sleep(frequency) *> internal(attempts + 1)
          } else {
            IO.raiseError(e)
          }
        }

    internal(0)
  }

  // retryable due to https://github.com/softwaremill/tapir/issues/1169
  "Fs2ReadStreamCompatible" should "convert fs2 stream to read stream" taggedAs (Retryable) in {
    val stream = Stream
      .unfoldChunkEval(0)({ num =>
        IO.delay(100.millis).as(((intAsBuffer(num), num + 1)).some)
      })

    (for {
      ref <- Ref.of[IO, List[Int]](Nil)
      dfd <- Deferred[IO, Either[Throwable, Unit]]
      readStream = streams.fs2.fs2ReadStreamCompatible[IO](options).asReadStream(stream.interruptWhen(dfd))
      completed <- Ref[IO].of(false)
      _ <- IO.delay {
        readStream.handler { buffer =>
          ref.update(_ :+ bufferAsInt(buffer)).unsafeRunSync()
        }
      }
      _ <- IO.delay {
        readStream.endHandler { _ =>
          completed.set(true).unsafeRunSync()
        }
      }
      _ <- IO.delay(readStream.resume())
      _ <- eventually(ref.get)({ case _ :: _ => () })
      _ <- IO.delay(readStream.pause())
      _ <- IO.sleep(1.second)
      snapshot2 <- ref.get
      _ <- IO.delay(readStream.resume())
      snapshot3 <- eventually(ref.get)({ case list => list.length should be > snapshot2.length })
      _ = shouldIncreaseMonotonously(snapshot3)
      _ <- dfd.complete(Right(()))
      _ <- eventually(completed.get)({ case true => () })
    } yield succeed).unsafeToFuture()
  }

  it should "interrupt read stream after zio stream interruption" in {
    val stream = Stream.unfoldChunkEval(0)({ num =>
      if (num > 20) {
        IO.raiseError(new Exception("!"))
      } else {
        Temporal[IO].sleep(100.millis).as(((intAsBuffer(num), num + 1)).some)
      }
    }) // .interruptAfter(2.seconds)
    val readStream = streams.fs2.fs2ReadStreamCompatible[IO](options).asReadStream(stream)
    (for {
      ref <- Ref.of[IO, List[Int]](Nil)
      completedRef <- Ref[IO].of(false)
      interruptedRef <- Ref.of[IO, Option[Throwable]](None)
      _ <- IO.delay {
        readStream.handler { buffer =>
          ref.update(_ :+ bufferAsInt(buffer)).unsafeRunSync()
        }
      }
      _ <- IO.delay {
        readStream.endHandler { _ =>
          completedRef.set(true).unsafeRunSync()
        }
      }
      _ <- IO.delay {
        readStream.exceptionHandler { cause =>
          interruptedRef.set(Some(cause)).unsafeRunSync()
        }
      }
      _ <- IO.delay(readStream.resume())
      snapshot <- eventually(ref.get)({ case list => list.length should be > 10 })
      _ = shouldIncreaseMonotonously(snapshot)
      _ <- eventually(for {
        completed <- completedRef.get
        interrupted <- interruptedRef.get
      } yield (completed, interrupted))({ case (false, Some(_)) =>
      })
    } yield succeed).unsafeToFuture()
  }

  it should "drain read stream without pauses if buffer has enough space" in {
    val opts = options.copy(maxQueueSizeForReadStream = 128)
    val count = 100
    val readStream = new FakeStream()
    val stream = streams.fs2.fs2ReadStreamCompatible[IO](opts)(implicitly).fromReadStream(readStream, None)
    (for {
      resultFiber <- stream
        .chunkN(4)
        .map(chunkAsInt)
        .compile
        .toList
        .start
      _ <- IO.delay {
        (1 to count).foreach { i =>
          readStream.handle(intAsVertxBuffer(i))
        }
        readStream.end()
      }
      result <- resultFiber.joinWith(IO.pure(Nil))
    } yield {
      shouldIncreaseMonotonously(result)
      result should have size count.toLong
      readStream.pauseCount shouldBe 0
      // readStream.resumeCount should be <= 1
    }).unsafeToFuture()
  }

  it should "drain read stream with small buffer" in {
    val count = 100
    val readStream = new FakeStream()
    val stream = streams.fs2.fs2ReadStreamCompatible[IO](options).fromReadStream(readStream, None)
    (for {
      resultFiber <- stream
        .chunkN(4)
        .map(chunkAsInt)
        .evalMap(i => IO.sleep(50.millis).as(i))
        .compile
        .toList
        .start
      _ <- IO
        .delay({
          (1 to count).foreach { i =>
            Thread.sleep(25)
            readStream.handle(intAsVertxBuffer(i))
          }
          readStream.end()
        })
        .start
      result <- resultFiber.joinWith(IO.pure(Nil))
    } yield {
      shouldIncreaseMonotonously(result)
      result should have size count.toLong
      readStream.pauseCount should be > 0
      readStream.resumeCount should be > 0
    }).unsafeToFuture()
  }

  it should "drain failed read stream" in {
    val ex = new Exception("!")
    val count = 50
    val readStream = new FakeStream()
    val stream = streams.fs2.fs2ReadStreamCompatible[IO](options).fromReadStream(readStream, None)
    (for {
      resultFiber <- stream
        .chunkN(4)
        .map(chunkAsInt)
        .evalMap(i => IO.sleep(50.millis).as(i))
        .compile
        .toList
        .start
      _ <- IO
        .delay({
          (1 to count).foreach { i =>
            Thread.sleep(25)
            readStream.handle(intAsVertxBuffer(i))
          }
          readStream.fail(ex)
        })
        .start
      result <- resultFiber.join.attempt
    } yield {
      result shouldBe Right(Outcome.errored(ex))
    }).unsafeToFuture()
  }
}
