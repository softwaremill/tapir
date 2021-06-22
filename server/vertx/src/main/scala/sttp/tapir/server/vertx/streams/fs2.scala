package sttp.tapir.server.vertx.streams

import cats.effect.concurrent.Deferred
import cats.effect.concurrent.Ref
import cats.effect.syntax.concurrent._
import cats.effect.ConcurrentEffect
import cats.effect.ExitCase
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.traverse._
import _root_.fs2.Chunk
import _root_.fs2.Stream
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.core.Handler
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.vertx.streams.ReadStreamState._
import sttp.tapir.server.vertx.VertxCatsServerOptions

import scala.collection.immutable.{Queue => SQueue}

object fs2 {

  implicit class DeferredOps[F[_], A](dfd: Deferred[F, A]) extends DeferredLike[F, A] {
    override def complete(a: A): F[Unit] =
      dfd.complete(a)

    override def get: F[A] =
      dfd.get
  }

  implicit def fs2ReadStreamCompatible[F[_]](implicit
      opts: VertxCatsServerOptions[F],
      F: ConcurrentEffect[F]
  ): ReadStreamCompatible[Fs2Streams[F]] =
    new ReadStreamCompatible[Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]

      override def asReadStream(stream: Stream[F, Byte]): ReadStream[Buffer] =
        F.toIO(for {
          promise <- Deferred[F, Unit]
          state <- Ref.of(StreamState.empty[F](promise))
          _ <- stream.chunks
            .evalMap({ chunk =>
              val buffer = Buffer.buffer(chunk.toArray)
              state.get.flatMap {
                case StreamState(None, handler, _, _) =>
                  F.delay(handler.handle(buffer))
                case StreamState(Some(promise), _, _, _) =>
                  for {
                    _ <- promise.get
                    // Handler in state may be updated since the moment when we wait
                    // promise so let's get more recent version.
                    updatedState <- state.get
                  } yield updatedState.handler.handle(buffer)
              }
            })
            .onFinalizeCase({
              case ExitCase.Completed =>
                state.get.flatMap { state =>
                  F.delay(state.endHandler.handle(null))
                }
              case ExitCase.Canceled =>
                state.get.flatMap { state =>
                  F.delay(state.errorHandler.handle(new Exception("Cancelled!")))
                }
              case ExitCase.Error(cause) =>
                state.get.flatMap { state =>
                  F.delay(state.errorHandler.handle(cause))
                }
            })
            .compile
            .drain
            .start
        } yield new ReadStream[Buffer] { self =>
          override def handler(handler: Handler[Buffer]): ReadStream[Buffer] =
            F.toIO(state.update(_.copy(handler = handler)).as(self))
              .unsafeRunSync()

          override def endHandler(handler: Handler[Void]): ReadStream[Buffer] =
            F.toIO(state.update(_.copy(endHandler = handler)).as(self))
              .unsafeRunSync()

          override def exceptionHandler(handler: Handler[Throwable]): ReadStream[Buffer] =
            F.toIO(state.update(_.copy(errorHandler = handler)).as(self))
              .unsafeRunSync()

          override def pause(): ReadStream[Buffer] =
            F.toIO(for {
              deferred <- Deferred[F, Unit]
              _ <- state.update {
                case cur @ StreamState(Some(_), _, _, _) =>
                  cur
                case cur @ StreamState(None, _, _, _) =>
                  cur.copy(paused = Some(deferred))
              }
            } yield self)
              .unsafeRunSync()

          override def resume(): ReadStream[Buffer] =
            F.toIO(for {
              oldState <- state.getAndUpdate(_.copy(paused = None))
              _ <- oldState.paused.fold(F.unit)(_.complete(()))
            } yield self)
              .unsafeRunSync()

          override def fetch(n: Long): ReadStream[Buffer] =
            self
        }).unsafeRunSync()

      override def fromReadStream(readStream: ReadStream[Buffer]): Stream[F, Byte] =
        F.toIO(for {
          stateRef <- Ref.of(ReadStreamState[F, Chunk[Byte]](Queued(SQueue.empty), Queued(SQueue.empty)))
          stream = Stream.unfoldChunkEval[F, Unit, Byte](()) { _ =>
            for {
              dfd <- Deferred[F, WrappedBuffer[Chunk[Byte]]]
              tuple <- stateRef.modify(_.dequeueBuffer(dfd))
              (mbBuffer, mbAction) = tuple
              _ <- mbAction.traverse(identity)
              wrappedBuffer <- mbBuffer match {
                case Left(deferred) =>
                  deferred.get
                case Right(buffer) =>
                  buffer.pure[F]
              }
              result <- wrappedBuffer match {
                case Right(buffer)     => Some((buffer, ())).pure[F]
                case Left(None)        => None.pure[F]
                case Left(Some(cause)) => ConcurrentEffect[F].raiseError(cause)
              }
            } yield result
          }

          _ <- Stream
            .unfoldEval[F, Unit, ActivationEvent](())({ _ =>
              for {
                dfd <- Deferred[F, WrappedEvent]
                mbEvent <- stateRef.modify(_.dequeueActivationEvent(dfd))
                result <- mbEvent match {
                  case Left(deferred) =>
                    deferred.get
                  case Right(event) =>
                    event.pure[F]
                }
              } yield result.map((_, ()))
            })
            .evalMap({
              case Pause =>
                ConcurrentEffect[F].delay(readStream.pause())
              case Resume =>
                ConcurrentEffect[F].delay(readStream.resume())
            })
            .compile
            .drain
            .start
        } yield {
          readStream.endHandler { _ =>
            F.toIO(stateRef.modify(_.halt(None)).flatMap(_.sequence_)).unsafeRunSync()
          }
          readStream.exceptionHandler { cause =>
            F.toIO(stateRef.modify(_.halt(Some(cause))).flatMap(_.sequence_)).unsafeRunSync()
          }
          readStream.handler { buffer =>
            val chunk = Chunk.array(buffer.getBytes)
            val maxSize = opts.maxQueueSizeForReadStream
            F.toIO(stateRef.modify(_.enqueue(chunk, maxSize)).flatMap(_.sequence_)).unsafeRunSync()
          }

          stream
        }).unsafeRunSync()
    }
}
