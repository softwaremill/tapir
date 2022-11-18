package sttp.tapir.server.vertx.zio

import _root_.zio._
import _root_.zio.stream.{Stream, ZStream}
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.vertx.streams.ReadStreamState.{WrappedBuffer, WrappedEvent}
import sttp.tapir.server.vertx.streams.{Queue, _}

import scala.collection.immutable.{Queue => SQueue}
import scala.language.postfixOps

package object streams {

  implicit class DeferredOps[A](dfd: Promise[Nothing, A]) extends DeferredLike[UIO, A] {
    override def complete(a: A): UIO[Unit] =
      dfd.done(Exit.Success(a)).unit

    override def get: UIO[A] =
      dfd.await
  }

  def zioReadStreamCompatible[F[_]](opts: VertxZioServerOptions[F])(implicit
      runtime: Runtime[Any]
  ): ReadStreamCompatible[ZioStreams] = new ReadStreamCompatible[ZioStreams] {
    override val streams: ZioStreams = ZioStreams

    override def asReadStream(stream: Stream[Throwable, Byte]): ReadStream[Buffer] =
      unsafeRunSync(for {
        promise <- Promise.make[Nothing, Unit]
        state <- Ref.make(StreamState.empty(promise))
        _ <- stream.runForeachChunk { chunk =>
          val buffer = Buffer.buffer(chunk.toArray)
          state.get.flatMap {
            case StreamState(None, handler, _, _) =>
              ZIO.attempt(handler.handle(buffer))
            case StreamState(Some(promise), _, _, _) =>
              for {
                _ <- promise.get
                // Handler in state may be updated since the moment when we wait
                // promise so let's get more recent version.
                updatedState <- state.get
              } yield updatedState.handler.handle(buffer)
          }
        } onExit {
          case Exit.Success(()) =>
            state.get.flatMap { state =>
              ZIO
                .attempt(state.endHandler.handle(null))
                .catchAll(cause2 => ZIO.attempt(state.errorHandler.handle(cause2)).either)
            }
          case Exit.Failure(cause) =>
            state.get.flatMap { state =>
              ZIO
                .attempt(state.errorHandler.handle(cause.squash))
                .catchAll(cause2 => ZIO.attempt(state.errorHandler.handle(cause2)).either)
            }
        } forkDaemon
      } yield new ReadStream[Buffer] { self =>
        override def handler(handler: Handler[Buffer]): ReadStream[Buffer] =
          unsafeRunSync(state.update(_.copy(handler = handler))).toEither
            .fold(throw _, _ => self)

        override def exceptionHandler(handler: Handler[Throwable]): ReadStream[Buffer] =
          unsafeRunSync(state.update(_.copy(errorHandler = handler))).toEither
            .fold(throw _, _ => self)

        override def endHandler(handler: Handler[Void]): ReadStream[Buffer] =
          unsafeRunSync(state.update(_.copy(endHandler = handler))).toEither
            .fold(throw _, _ => self)

        override def pause(): ReadStream[Buffer] =
          unsafeRunSync(for {
            promise <- Promise.make[Nothing, Unit]
            _ <- state.update {
              case cur @ StreamState(Some(_), _, _, _) =>
                cur
              case cur @ StreamState(None, _, _, _) =>
                cur.copy(paused = Some(promise))
            }
          } yield self).toEither
            .fold(throw _, identity)

        override def resume(): ReadStream[Buffer] =
          unsafeRunSync(for {
            oldState <- state.getAndUpdate(_.copy(paused = None))
            _ <- oldState.paused.fold[UIO[Any]](ZIO.unit)(_.complete(()))
          } yield self).toEither
            .fold(throw _, identity)

        override def fetch(x: Long): ReadStream[Buffer] =
          self

      }).toEither
        .fold(throw _, identity)

    override def fromReadStream(readStream: ReadStream[Buffer]): Stream[Throwable, Byte] =
      unsafeRunSync(for {
        stateRef <- Ref.make(ReadStreamState[UIO, Chunk[Byte]](Queued(SQueue.empty), Queued(SQueue.empty)))
        stream = ZStream.unfoldChunkZIO(()) { _ =>
          for {
            dfd <- Promise.make[Nothing, WrappedBuffer[Chunk[Byte]]]
            tuple <- stateRef.modify(_.dequeueBuffer(dfd).swap)
            (mbBuffer, mbAction) = tuple
            _ <- ZIO.foreachDiscard(mbAction)(identity)
            wrappedBuffer <- mbBuffer match {
              case Left(deferred) =>
                deferred.get
              case Right(buffer) =>
                ZIO.succeed(buffer)
            }
            result <- wrappedBuffer match {
              case Right(buffer)     => ZIO.some((buffer, ()))
              case Left(None)        => ZIO.none
              case Left(Some(cause)) => ZIO.fail(cause)
            }
          } yield result
        }
        _ <- ZStream
          .unfoldZIO(())({ _ =>
            for {
              dfd <- Promise.make[Nothing, WrappedEvent]
              mbEvent <- stateRef.modify(_.dequeueActivationEvent(dfd).swap)
              result <- mbEvent match {
                case Left(deferred) =>
                  deferred.get
                case Right(event) =>
                  ZIO.succeed(event)
              }
            } yield result.map((_, ()))
          })
          .mapZIO({
            case Pause =>
              ZIO.attempt(readStream.pause())
            case Resume =>
              ZIO.attempt(readStream.resume())
          })
          .runDrain
          .forkDaemon
      } yield {
        readStream.endHandler { _ =>
          unsafeRunSync(stateRef.modify(_.halt(None).swap).flatMap(ZIO.foreachDiscard(_)(identity)))
            .fold(c => throw c, identity)
        }
        readStream.exceptionHandler { cause =>
          unsafeRunSync(stateRef.modify(_.halt(Some(cause)).swap).flatMap(ZIO.foreachDiscard(_)(identity)))
            .fold(c => throw c, identity)
        }
        readStream.handler { buffer =>
          val chunk = Chunk.fromArray(buffer.getBytes)
          val maxSize = opts.maxQueueSizeForReadStream
          unsafeRunSync(stateRef.modify(_.enqueue(chunk, maxSize).swap).flatMap(ZIO.foreachDiscard(_)(identity)))
            .fold(c => throw c, identity)
        }

        stream
      }).toEither
        .fold(throw _, identity)

    def unsafeRunSync[T](task: Task[T]): Exit[Throwable, T] =
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.run(task)
      }
  }

}
