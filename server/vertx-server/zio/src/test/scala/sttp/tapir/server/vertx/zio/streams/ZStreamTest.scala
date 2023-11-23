package sttp.tapir.server.vertx.zio.streams

import _root_.zio.stream.ZStream
import _root_.zio.{durationInt, Runtime => ZIORuntime, _}
import io.vertx.core.buffer.Buffer
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.vertx.streams.FakeStream
import sttp.tapir.server.vertx.zio.VertxZioServerOptions

import java.nio.ByteBuffer

class ZStreamTest extends AsyncFlatSpec with Matchers {

  private val runtime = ZIORuntime.default

  private val options = VertxZioServerOptions.default[Any].copy(maxQueueSizeForReadStream = 4)

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
    task.tap(a => ZIO.attempt(cond(a))).retry(schedule)

  "ZioReadStreamCompatible" should "convert zio stream to read stream" in {
    val stream: ZioStreams.BinaryStream = ZStream
      .tick(100.millis)
      .mapAccum(0)((acc, _) => (acc + 1, acc))
      .haltAfter(3.seconds)
      .map(intAsBuffer)
      .flattenChunks
    val readStream = zioReadStreamCompatible(options)(runtime).asReadStream(stream)
    unsafeToFuture(for {
      ref <- Ref.make[List[Int]](Nil)
      completed <- Ref.make[Boolean](false)
      _ <- ZIO.attempt {
        readStream.handler { buffer =>
          unsafeRunSync(ref.update(_ :+ bufferAsInt(buffer)))
          ()
        }
      }
      _ <- ZIO.attempt {
        readStream.endHandler { _ =>
          unsafeRunSync(completed.set(true))
          ()
        }
      }
      _ <- ZIO.attempt(readStream.resume())
      _ <- eventually(ref.get)({ case _ :: _ => () })
      _ <- ZIO.attempt(readStream.pause())
      _ <- ZIO.sleep(1.seconds)
      snapshot2 <- ref.get
      _ <- ZIO.attempt(readStream.resume())
      snapshot3 <- eventually(ref.get)({ case list => list.length should be > snapshot2.length })
      _ = shouldIncreaseMonotonously(snapshot3)
      _ <- eventually(completed.get)({ case true => () })
    } yield succeed)
  }

  it should "interrupt read stream after zio stream interruption" in {
    val stream = ZStream
      .tick(100.millis)
      .mapAccum(0)((acc, _) => (acc + 1, acc))
      .haltAfter(7.seconds)
      .map(intAsBuffer)
      .flattenChunks ++ ZStream.fail(new Exception("!"))
    val readStream = zioReadStreamCompatible(options)(runtime).asReadStream(stream)
    unsafeToFuture(for {
      ref <- Ref.make[List[Int]](Nil)
      completedRef <- Ref.make[Boolean](false)
      interruptedRef <- Ref.make[Option[Throwable]](None)
      _ <- ZIO.attempt {
        readStream.handler { buffer =>
          unsafeRunSync(ref.update(_ :+ bufferAsInt(buffer)))
          ()
        }
      }
      _ <- ZIO.attempt {
        readStream.endHandler { _ =>
          unsafeRunSync(completedRef.set(true))
          ()
        }
      }
      _ <- ZIO.attempt {
        readStream.exceptionHandler { cause =>
          unsafeRunSync(interruptedRef.set(Some(cause)))
          ()
        }
      }
      _ <- ZIO.attempt(readStream.resume())
      snapshot <- eventually(ref.get)({ case list => list.length should be > 3 })
      _ = shouldIncreaseMonotonously(snapshot)
      _ <- eventually(completedRef.get zip interruptedRef.get)({ case (false, Some(_)) =>
      })
    } yield succeed)
  }

  it should "drain read stream without pauses if buffer has enough space" in {
    val opts = options.copy(maxQueueSizeForReadStream = 128)
    val count = 100
    val readStream = new FakeStream()
    val stream = zioReadStreamCompatible(opts)(runtime).fromReadStream(readStream, None)
    unsafeToFuture(for {
      resultFiber <- ZIO
        .scoped(
          stream
            .mapChunks((chunkAsInt _).andThen(Chunk.single))
            .toIterator
            .map(_.toList)
        )
        .fork
      _ <- ZIO.attempt {
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
  }

  it should "drain read stream with small buffer" in {
    val opts = options.copy(maxQueueSizeForReadStream = 4)
    val count = 100
    val readStream = new FakeStream()
    val stream = zioReadStreamCompatible(opts)(runtime).fromReadStream(readStream, None)
    unsafeToFuture(for {
      resultFiber <- ZIO
        .scoped(
          stream
            .mapChunks((chunkAsInt _).andThen(Chunk.single))
            .mapZIO(i => ZIO.sleep(50.millis).as(i))
            .toIterator
            .map(_.toList)
        )
        .fork
      _ <- ZIO
        .attempt({
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
  }

  it should "drain failed read stream" in {
    val opts = options.copy(maxQueueSizeForReadStream = 4)
    val count = 50
    val readStream = new FakeStream()
    val stream = zioReadStreamCompatible(opts)(runtime).fromReadStream(readStream, None)
    unsafeToFuture(for {
      resultFiber <- ZIO
        .scoped(
          stream
            .mapChunks((chunkAsInt _).andThen(Chunk.single))
            .mapZIO(i => ZIO.sleep(50.millis).as(i))
            .toIterator
            .map(_.toList)
        )
        .fork
      _ <- ZIO
        .attempt({
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
  }

  private def unsafeRunSync[T](task: Task[T]): Exit[Throwable, T] =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(task)
    }

  private def unsafeToFuture[T](task: Task[T]): CancelableFuture[T] =
    Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(task))
}
