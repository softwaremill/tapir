package sttp.tapir.server.vertx.cats.streams

import _root_.fs2.{Chunk, Stream}
import cats.effect.kernel.Async
import cats.effect.kernel.Resource.ExitCase.{Canceled, Errored, Succeeded}
import cats.effect.{Deferred, Ref}
import cats.syntax.all._
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.server.vertx.cats.VertxCatsServerOptions
import sttp.tapir.server.vertx.streams.ReadStreamState.{WrappedBuffer, WrappedEvent}
import sttp.tapir.server.vertx.streams.websocket._
import sttp.tapir.server.vertx.streams._
import sttp.ws.WebSocketFrame

import scala.collection.immutable.{Queue => SQueue}

object fs2 {

  implicit class DeferredOps[F[_]: Async, A](dfd: Deferred[F, A]) extends DeferredLike[F, A] {
    override def complete(a: A): F[Unit] =
      dfd.complete(a).void

    override def get: F[A] =
      dfd.get
  }

  implicit def fs2ReadStreamCompatible[F[_]](opts: VertxCatsServerOptions[F])(implicit F: Async[F]): ReadStreamCompatible[Fs2Streams[F]] = {
    new ReadStreamCompatible[Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]

      override def asReadStream(stream: Stream[F, Byte]): ReadStream[Buffer] =
        mapToReadStream[Chunk[Byte], Buffer](stream.chunks, chunk => Buffer.buffer(chunk.toArray))

      private def mapToReadStream[I, O](stream: Stream[F, I], fn: I => O): ReadStream[O] =
        opts.dispatcher.unsafeRunSync {
          for {
            promise <- Deferred[F, Unit]
            state <- Ref.of(StreamState.empty[F, O](promise))
            _ <- F.start(
              stream
                .evalMap({ chunk =>
                  val buffer = fn(chunk)
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
                  case Succeeded =>
                    state.get.flatMap { state =>
                      F.delay(state.endHandler.handle(null))
                    }
                  case Canceled =>
                    state.get.flatMap { state =>
                      F.delay(state.errorHandler.handle(new Exception("Cancelled!")))
                    }
                  case Errored(cause) =>
                    state.get.flatMap { state =>
                      F.delay(state.errorHandler.handle(cause))
                    }
                })
                .compile
                .drain
            )
          } yield new ReadStream[O] {
            self =>
            override def handler(handler: Handler[O]): ReadStream[O] =
              opts.dispatcher.unsafeRunSync(state.update(_.copy(handler = handler)).as(self))

            override def endHandler(handler: Handler[Void]): ReadStream[O] =
              opts.dispatcher.unsafeRunSync(state.update(_.copy(endHandler = handler)).as(self))

            override def exceptionHandler(handler: Handler[Throwable]): ReadStream[O] =
              opts.dispatcher.unsafeRunSync(state.update(_.copy(errorHandler = handler)).as(self))

            override def pause(): ReadStream[O] =
              opts.dispatcher.unsafeRunSync(for {
                deferred <- Deferred[F, Unit]
                _ <- state.update {
                  case cur @ StreamState(Some(_), _, _, _) =>
                    cur
                  case cur @ StreamState(None, _, _, _) =>
                    cur.copy(paused = Some(deferred))
                }
              } yield self)

            override def resume(): ReadStream[O] =
              opts.dispatcher.unsafeRunSync(for {
                oldState <- state.getAndUpdate(_.copy(paused = None))
                _ <- oldState.paused.fold(Async[F].unit)(_.complete(()))
              } yield self)

            override def fetch(n: Long): ReadStream[O] =
              self
          }
        }

      override def fromReadStream(readStream: ReadStream[Buffer], maxBytes: Option[Long]): Stream[F, Byte] = {
        val stream = fromReadStreamInternal(readStream).map(buffer => Chunk.array(buffer.getBytes)).unchunks
        maxBytes.map(Fs2Streams.limitBytes(stream, _)).getOrElse(stream)
      }

      private def fromReadStreamInternal[T](readStream: ReadStream[T]): Stream[F, T] =
        opts.dispatcher.unsafeRunSync {
          for {
            stateRef <- Ref.of(ReadStreamState[F, T](Queued(SQueue.empty), Queued(SQueue.empty)))
            stream = Stream.unfoldEval[F, Unit, T](()) { _ =>
              for {
                dfd <- Deferred[F, WrappedBuffer[T]]
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
                  case Left(Some(cause)) => Async[F].raiseError(cause)
                }
              } yield result
            }

            _ <- F.start(
              Stream
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
                  case Pause  => F.delay(readStream.pause())
                  case Resume => F.delay(readStream.resume())
                })
                .compile
                .drain
            )
          } yield {
            readStream.endHandler { _ =>
              opts.dispatcher.unsafeRunSync(stateRef.modify(_.halt(None)).flatMap(_.sequence_))
            }
            readStream.exceptionHandler { cause =>
              opts.dispatcher.unsafeRunSync(stateRef.modify(_.halt(Some(cause))).flatMap(_.sequence_))
            }
            readStream.handler { buffer =>
              val maxSize = opts.maxQueueSizeForReadStream
              opts.dispatcher.unsafeRunSync(stateRef.modify(_.enqueue(buffer, maxSize)).flatMap(_.sequence_))
            }

            stream
          }
        }

      override def webSocketPipe[REQ, RESP](
          readStream: ReadStream[WebSocketFrame],
          pipe: streams.Pipe[REQ, RESP],
          o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Fs2Streams[F]]
      ): ReadStream[WebSocketFrame] = {
        val stream0 = fromReadStreamInternal(readStream)
        val stream1 = optionallyContatenateFrames(stream0, o.concatenateFragmentedFrames)
        val stream2 = optionallyIgnorePong(stream1, o.ignorePong)
        val autoPings = o.autoPing match {
          case Some((interval, frame)) =>
            Stream.awakeEvery(interval).as(frame)
          case None =>
            Stream.empty
        }

        val stream3 = stream2
          .map { frame =>
            o.requests.decode(frame) match {
              case DecodeResult.Value(v) =>
                v
              case failure: DecodeResult.Failure =>
                throw new WebSocketFrameDecodeFailure(frame, failure)
            }
          }
          .through(pipe)
          .map(o.responses.encode)
          .mergeHaltL(autoPings)
          .append(Stream(WebSocketFrame.close))

        mapToReadStream[WebSocketFrame, WebSocketFrame](stream3, identity)
      }

      def optionallyContatenateFrames(s: Stream[F, WebSocketFrame], doConcatenate: Boolean): Stream[F, WebSocketFrame] =
        if (doConcatenate) {
          s.mapAccumulate(None: Accumulator)(concatenateFrames).collect { case (_, Some(f)) => f }
        } else {
          s
        }

      def optionallyIgnorePong(s: Stream[F, WebSocketFrame], ignore: Boolean): Stream[F, WebSocketFrame] =
        if (ignore) s.filterNot(isPong) else s
    }
  }
}
