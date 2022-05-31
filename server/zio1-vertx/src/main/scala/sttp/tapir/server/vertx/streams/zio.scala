package sttp.tapir.server.vertx.streams

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.core.Handler
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.vertx.streams.ReadStreamState._
import sttp.tapir.server.vertx.VertxZioServerOptions
import _root_.zio._
import _root_.zio.stream.{Stream, ZStream}

import scala.collection.immutable.{Queue => SQueue}
import scala.language.postfixOps

object zio {

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
      runtime
        .unsafeRunSync(for {
          promise <- Promise.make[Nothing, Unit]
          state <- Ref.make(StreamState.empty(promise))
          _ <- stream.foreachChunk { chunk =>
            val buffer = Buffer.buffer(chunk.toArray)
            state.get.flatMap {
              case StreamState(None, handler, _, _) =>
                ZIO.effect(handler.handle(buffer))
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
                  .effect(state.endHandler.handle(null))
                  .catchAll(cause2 => ZIO.effect(state.errorHandler.handle(cause2)).either)
              }
            case Exit.Failure(cause) =>
              state.get.flatMap { state =>
                ZIO
                  .effect(state.errorHandler.handle(cause.squash))
                  .catchAll(cause2 => ZIO.effect(state.errorHandler.handle(cause2)).either)
              }
          } forkDaemon
        } yield new ReadStream[Buffer] { self =>
          override def handler(handler: Handler[Buffer]): ReadStream[Buffer] =
            runtime
              .unsafeRunSync(state.update(_.copy(handler = handler)))
              .toEither
              .fold(throw _, _ => self)

          override def exceptionHandler(handler: Handler[Throwable]): ReadStream[Buffer] =
            runtime
              .unsafeRunSync(state.update(_.copy(errorHandler = handler)))
              .toEither
              .fold(throw _, _ => self)

          override def endHandler(handler: Handler[Void]): ReadStream[Buffer] =
            runtime
              .unsafeRunSync(state.update(_.copy(endHandler = handler)))
              .toEither
              .fold(throw _, _ => self)

          override def pause(): ReadStream[Buffer] =
            runtime
              .unsafeRunSync(for {
                promise <- Promise.make[Nothing, Unit]
                _ <- state.update {
                  case cur @ StreamState(Some(_), _, _, _) =>
                    cur
                  case cur @ StreamState(None, _, _, _) =>
                    cur.copy(paused = Some(promise))
                }
              } yield self)
              .toEither
              .fold(throw _, identity)

          override def resume(): ReadStream[Buffer] =
            runtime
              .unsafeRunSync(for {
                oldState <- state.getAndUpdate(_.copy(paused = None))
                _ <- oldState.paused.fold[UIO[Any]](UIO.unit)(_.complete(()))
              } yield self)
              .toEither
              .fold(throw _, identity)

          override def fetch(x: Long): ReadStream[Buffer] =
            self

        })
        .toEither
        .fold(throw _, identity)

    override def fromReadStream(readStream: ReadStream[Buffer]): Stream[Throwable, Byte] =
      runtime
        .unsafeRunSync(for {
          stateRef <- Ref.make(ReadStreamState[UIO, Chunk[Byte]](Queued(SQueue.empty), Queued(SQueue.empty)))
          stream = ZStream.unfoldChunkM(()) { _ =>
            for {
              dfd <- Promise.make[Nothing, WrappedBuffer[Chunk[Byte]]]
              tuple <- stateRef.modify(_.dequeueBuffer(dfd).swap)
              (mbBuffer, mbAction) = tuple
              _ <- ZIO.foreach(mbAction)(identity)
              wrappedBuffer <- mbBuffer match {
                case Left(deferred) =>
                  deferred.get
                case Right(buffer) =>
                  UIO.succeed(buffer)
              }
              result <- wrappedBuffer match {
                case Right(buffer)     => UIO.some((buffer, ()))
                case Left(None)        => UIO.none
                case Left(Some(cause)) => IO.fail(cause)
              }
            } yield result
          }
          _ <- ZStream
            .unfoldM(())({ _ =>
              for {
                dfd <- Promise.make[Nothing, WrappedEvent]
                mbEvent <- stateRef.modify(_.dequeueActivationEvent(dfd).swap)
                result <- mbEvent match {
                  case Left(deferred) =>
                    deferred.get
                  case Right(event) =>
                    UIO.succeed(event)
                }
              } yield result.map((_, ()))
            })
            .mapM({
              case Pause =>
                IO.effect(readStream.pause())
              case Resume =>
                IO.effect(readStream.resume())
            })
            .runDrain
            .forkDaemon
        } yield {
          readStream.endHandler { _ =>
            runtime
              .unsafeRunSync(stateRef.modify(_.halt(None).swap).flatMap(ZIO.foreach_(_)(identity)))
              .fold(c => throw c.squash, identity)
          }
          readStream.exceptionHandler { cause =>
            runtime
              .unsafeRunSync(stateRef.modify(_.halt(Some(cause)).swap).flatMap(ZIO.foreach_(_)(identity)))
              .fold(c => throw c.squash, identity)
          }
          readStream.handler { buffer =>
            val chunk = Chunk.fromArray(buffer.getBytes)
            val maxSize = opts.maxQueueSizeForReadStream
            runtime
              .unsafeRunSync(stateRef.modify(_.enqueue(chunk, maxSize).swap).flatMap(ZIO.foreach_(_)(identity)))
              .fold(c => throw c.squash, identity)
          }

          stream
        })
        .toEither
        .fold(throw _, identity)
  }
}
