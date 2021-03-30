package sttp.tapir.server.vertx.streams

import java.nio.ByteBuffer
import io.vertx.core.buffer.Buffer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues._
import _root_.zio._
import _root_.zio.clock
import _root_.zio.duration._
import _root_.zio.stream.ZStream
import _root_.zio.clock.Clock
import _root_.zio.internal.tracing.TracingConfig
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.vertx.VertxZioServerOptions

class ZStreamTest extends AnyFlatSpec with Matchers {

  val runtime = Runtime.default.mapPlatform(_.withTracingConfig(TracingConfig.disabled))

  val options = VertxZioServerOptions.default.copy(maxQueueSizeForReadStream = 4)

  def intAsBuffer(int: Int): Chunk[Byte] = {
    val buffer = ByteBuffer.allocate(4)
    buffer.putInt(int)
    buffer.flip()
    Chunk.fromByteBuffer(buffer)
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

  val schedule = (Schedule.spaced(50.millis) >>> Schedule.elapsed).whileOutput(_ < 15.seconds)

  def eventually[A](task: Task[A])(cond: PartialFunction[A, Unit]): Task[A] =
    task.tap(a => ZIO.effect(cond(a))).retry(schedule).provideLayer(Clock.live)

  "ZioReadStreamCompatible" should "convert zio stream to read stream" in {
    val stream: ZioStreams.BinaryStream = ZStream
      .tick(100.millis)
      .mapAccum(0)((acc, _) => (acc + 1, acc))
      .haltAfter(3.seconds)
      .map(intAsBuffer)
      .flattenChunks
      .provideLayer(clock.Clock.live)
    val readStream = zio.zioReadStreamCompatible(options, runtime).asReadStream(stream)
    runtime
      .unsafeRunSync(for {
        ref <- ZRef.make[List[Int]](Nil)
        completed <- ZRef.make[Boolean](false)
        _ <- Task.effect {
          readStream.handler { buffer =>
            runtime.unsafeRunSync(ref.update(_ :+ bufferAsInt(buffer)))
            ()
          }
        }
        _ <- Task.effect {
          readStream.endHandler { _ =>
            runtime.unsafeRunSync(completed.set(true))
            ()
          }
        }
        _ <- Task.effect(readStream.resume())
        _ <- eventually(ref.get)({ case _ :: _ => () })
        _ <- Task.effect(readStream.pause())
        _ <- ZIO.sleep(1.seconds)
        snapshot2 <- ref.get
        _ <- Task.effect(readStream.resume())
        snapshot3 <- eventually(ref.get)({ case list => list.length should be > snapshot2.length })
        _ = shouldIncreaseMonotonously(snapshot3)
        _ <- eventually(completed.get)({ case true => () })
      } yield ())
      .toEither
      .value
  }

  it should "interrupt read stream after zio stream interruption" in {
    val stream = ZStream
      .tick(100.millis)
      .mapAccum(0)((acc, _) => (acc + 1, acc))
      .haltAfter(7.seconds)
      .map(intAsBuffer)
      .flattenChunks
      .provideLayer(clock.Clock.live) ++ ZStream.fail(new Exception("!"))
    val readStream = zio.zioReadStreamCompatible(options, runtime).asReadStream(stream)
    runtime
      .unsafeRunSync(for {
        ref <- ZRef.make[List[Int]](Nil)
        completedRef <- ZRef.make[Boolean](false)
        interruptedRef <- ZRef.make[Option[Throwable]](None)
        _ <- Task.effect {
          readStream.handler { buffer =>
            runtime.unsafeRunSync(ref.update(_ :+ bufferAsInt(buffer)))
            ()
          }
        }
        _ <- Task.effect {
          readStream.endHandler { _ =>
            runtime.unsafeRunSync(completedRef.set(true))
            ()
          }
        }
        _ <- Task.effect {
          readStream.exceptionHandler { cause =>
            runtime.unsafeRunSync(interruptedRef.set(Some(cause)))
            ()
          }
        }
        _ <- Task.effect(readStream.resume())
        snapshot <- eventually(ref.get)({ case list => list.length should be > 3 })
        _ = shouldIncreaseMonotonously(snapshot)
        _ <- eventually(completedRef.get &&& interruptedRef.get)({ case (false, Some(_)) =>
        })
      } yield ())
      .toEither
      .value
  }

  it should "drain read stream without pauses if buffer has enough space" in {
    val opts = options.copy(maxQueueSizeForReadStream = 128)
    val count = 100
    val readStream = new FakeStream()
    val stream = zio.zioReadStreamCompatible(opts, runtime).fromReadStream(readStream)
    runtime
      .unsafeRunSync(for {
        resultFiber <- stream
          .mapChunks((chunkAsInt _).andThen(Chunk.single))
          .toIterator
          .map(_.toList)
          .useNow
          .fork
        _ <- ZIO.effect {
          (1 to count).foreach { i =>
            readStream.handle(intAsVertxBuffer(i))
          }
          readStream.end()
        }
        result <- resultFiber.join
      } yield {
        val successes = result.collect { case Right(i) => i }
        shouldIncreaseMonotonously(successes)
        successes should have size count.toLong
        readStream.pauseCount shouldBe 0
        // readStream.resumeCount shouldBe 0
      })
      .toEither
      .value
  }

  it should "drain read stream with small buffer" in {
    val opts = options.copy(maxQueueSizeForReadStream = 4)
    val count = 100
    val readStream = new FakeStream()
    val stream = zio.zioReadStreamCompatible(opts, runtime).fromReadStream(readStream)
    runtime
      .unsafeRunSync(for {
        resultFiber <- stream
          .mapChunks((chunkAsInt _).andThen(Chunk.single))
          .mapM(i => ZIO.sleep(50.millis).as(i))
          .toIterator
          .map(_.toList)
          .useNow
          .fork
        _ <- ZIO
          .effect({
            (1 to count).foreach { i =>
              Thread.sleep(25)
              readStream.handle(intAsVertxBuffer(i))
            }
            readStream.end()
          })
          .fork
        result <- resultFiber.join
      } yield {
        val successes = result.collect { case Right(i) => i }
        shouldIncreaseMonotonously(successes)
        successes should have size count.toLong
        readStream.pauseCount should be > 0
        readStream.resumeCount should be > 0
      })
      .toEither
      .value
  }

  it should "drain failed read stream" in {
    val opts = options.copy(maxQueueSizeForReadStream = 4)
    val count = 50
    val readStream = new FakeStream()
    val stream = zio.zioReadStreamCompatible(opts, runtime).fromReadStream(readStream)
    runtime
      .unsafeRunSync(for {
        resultFiber <- stream
          .mapChunks((chunkAsInt _).andThen(Chunk.single))
          .mapM(i => ZIO.sleep(50.millis).as(i))
          .toIterator
          .map(_.toList)
          .useNow
          .fork
        _ <- ZIO
          .effect({
            (1 to count).foreach { i =>
              Thread.sleep(25)
              readStream.handle(intAsVertxBuffer(i))
            }
            readStream.fail(new Exception("!"))
          })
          .fork
        result <- resultFiber.join
      } yield {
        val successes = result.collect { case Right(i) => i }
        shouldIncreaseMonotonously(successes)
        successes should have size count.toLong
        readStream.pauseCount should be > 0
        readStream.resumeCount should be > 0
        result.lastOption.collect { case Left(e) => e } should not be empty
      })
      .toEither
      .value
  }
}
