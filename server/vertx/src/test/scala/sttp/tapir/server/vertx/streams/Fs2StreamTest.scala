package sttp.tapir.server.vertx.streams

import java.nio.ByteBuffer
import cats.effect.{ContextShift, IO, Timer}
import cats.effect.concurrent.Ref
import cats.syntax.flatMap._
import cats.syntax.option._
import _root_.fs2.Stream
import _root_.fs2.Chunk
import io.vertx.core.buffer.Buffer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.server.vertx.VertxCatsEndpointOptions

import scala.concurrent.duration._
import scala.util.control.NonFatal

class Fs2StreamTest extends AnyFlatSpec with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)
  implicit val options: VertxCatsEndpointOptions[IO] = VertxCatsEndpointOptions.default[IO].copy(maxQueueSizeForReadStream = 4)

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
        .handleErrorWith({ case NonFatal(e) =>
          if (attempts < maxAttempts) {
            timer.sleep(frequency) *> internal(attempts + 1)
          } else {
            IO.raiseError(e)
          }
        })

    internal(0)
  }

  "Fs2ReadStreamCompatible" should "convert fs2 stream to read stream" in {
    val stream = Stream
      .unfoldChunkEval(0)({ num =>
        IO.delay(100.millis).as(((intAsBuffer(num), num + 1)).some)
      })
      .interruptAfter(4.seconds)

    (for {
      ref <- Ref.of[IO, List[Int]](Nil)
      readStream = fs2.fs2ReadStreamCompatible[IO].asReadStream(stream)
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
      _ <- eventually(completed.get)({ case true => () })
    } yield ()).unsafeRunSync()
  }

  it should "interrupt read stream after zio stream interruption" in {
    val stream = Stream.unfoldChunkEval(0)({ num =>
      if (num > 20) {
        IO.raiseError(new Exception("!"))
      } else {
        timer.sleep(100.millis).as(((intAsBuffer(num), num + 1)).some)
      }
    }) //.interruptAfter(2.seconds)
    val readStream = fs2.fs2ReadStreamCompatible[IO].asReadStream(stream)
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
    } yield ()).unsafeRunSync()
  }

  it should "drain read stream without pauses if buffer has enough space" in {
    val opts = options.copy(maxQueueSizeForReadStream = 128)
    val count = 100
    val readStream = new FakeStream()
    val stream = fs2.fs2ReadStreamCompatible[IO](opts, implicitly).fromReadStream(readStream)
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
      result <- resultFiber.join
    } yield {
      shouldIncreaseMonotonously(result)
      result should have size count.toLong
      readStream.pauseCount shouldBe 0
      // readStream.resumeCount should be <= 1
    }).unsafeRunSync()
  }

  it should "drain read stream with small buffer" in {
    val count = 100
    val readStream = new FakeStream()
    val stream = fs2.fs2ReadStreamCompatible[IO].fromReadStream(readStream)
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
      result <- resultFiber.join
    } yield {
      shouldIncreaseMonotonously(result)
      result should have size count.toLong
      readStream.pauseCount should be > 0
      readStream.resumeCount should be > 0
    }).unsafeRunSync()
  }

  it should "drain failed read stream" in {
    val count = 50
    val readStream = new FakeStream()
    val stream = fs2.fs2ReadStreamCompatible[IO].fromReadStream(readStream)
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
          readStream.fail(new Exception("!"))
        })
        .start
      result <- resultFiber.join.attempt
    } yield {
      result.isLeft shouldBe true
    }).unsafeRunSync()
  }
}
