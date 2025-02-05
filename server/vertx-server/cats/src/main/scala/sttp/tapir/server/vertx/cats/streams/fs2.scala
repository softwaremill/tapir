package sttp.tapir.server.vertx.cats.streams

import _root_.fs2.{Chunk, Stream}
import _root_.fs2.concurrent.Channel
import cats.effect.kernel.Resource.ExitCase.{Canceled, Errored, Succeeded}
import cats.effect.{Deferred, Ref, Sync, Async, GenSpawn}
import cats.syntax.all._
import cats.effect.implicits._
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

  implicit def fs2ReadStreamCompatible[F[_]: Async](
      opts: VertxCatsServerOptions[F]
  ): ReadStreamCompatible[Fs2Streams[F]] = {
    new ReadStreamCompatible[Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]

      override def asReadStream(stream: Stream[F, Byte]): ReadStream[Buffer] =
        mapToReadStream[Chunk[Byte], Buffer](stream.chunks, chunk => Buffer.buffer(chunk.toArray))

      private def mapToReadStream[I, O](stream: Stream[F, I], fn: I => O): ReadStream[O] =
        opts.dispatcher.unsafeRunSync {
          for {
            promise <- Deferred[F, Unit]
            state <- Ref.of(StreamState.empty[F, O](promise))
            _ <- GenSpawn[F].start(
              stream
                .evalMap({ chunk =>
                  state.get.flatMap {
                    case StreamState(None, handler, _, _) =>
                      Sync[F].delay(handler.handle(fn(chunk)))
                    case StreamState(Some(promise), _, _, _) =>
                      for {
                        _ <- promise.get
                        // Handler in state may be updated since the moment when we wait
                        // promise so let's get more recent version.
                        updatedState <- state.get
                        _ <- Sync[F].delay(updatedState.handler.handle(fn(chunk)))
                      } yield ()
                  }
                })
                .onFinalizeCase({
                  case Succeeded =>
                    state.get.flatMap { state =>
                      Sync[F].delay(state.endHandler.handle(null))
                    }
                  case Canceled =>
                    state.get.flatMap { state =>
                      Sync[F].delay(state.errorHandler.handle(new Exception("Cancelled!")))
                    }
                  case Errored(cause) =>
                    state.get.flatMap { state =>
                      Sync[F].delay(state.errorHandler.handle(cause))
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

            _ <- GenSpawn[F].start(
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
                  case Pause  => Sync[F].delay(readStream.pause())
                  case Resume => Sync[F].delay(readStream.resume())
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

      private def decodeFrame[REQ, RESP](
          frame: WebSocketFrame,
          o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Fs2Streams[F]]
      ): REQ = {
        o.requests.decode(frame) match {
          case DecodeResult.Value(v)         => v
          case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(frame, failure)
        }
      }

      private def fastPath[REQ, RESP](
          in: Stream[F, WebSocketFrame],
          pipe: streams.Pipe[REQ, RESP],
          o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Fs2Streams[F]]
      ): ReadStream[WebSocketFrame] = {
        val contatenatedFrames = optionallyContatenateFrames(in, o.concatenateFragmentedFrames)
        val ignorePongs = optionallyIgnorePong(contatenatedFrames, o.ignorePong)

        val stream = ignorePongs
          .map(decodeFrame(_, o))
          .through(pipe)
          .map(o.responses.encode)
          .append(Stream(WebSocketFrame.close))

        mapToReadStream[WebSocketFrame, WebSocketFrame](stream, identity)
      }

      private def standardPath[REQ, RESP](
          in: Stream[F, WebSocketFrame],
          pipe: streams.Pipe[REQ, RESP],
          o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Fs2Streams[F]]
      ): ReadStream[WebSocketFrame] = {
        val mergedStream = Channel.bounded[F, Chunk[WebSocketFrame]](64).map { c =>
          val contatenatedFrames = optionallyContatenateFrames(in, o.concatenateFragmentedFrames)
          val ignoredPongs = optionallyIgnorePong(contatenatedFrames, o.ignorePong)

          val autoPings = o.autoPing match {
            case Some((interval, frame)) => Stream.awakeEvery(interval).as(frame)
            case None                    => Stream.empty
          }

          val outputProducer = ignoredPongs
            .map(decodeFrame(_, o))
            .through(pipe)
            .chunks
            .foreach(chunk => c.send(chunk.map(r => o.responses.encode(r))).void)
            .compile
            .drain

          val outcomes = (outputProducer.guarantee(c.close.void), autoPings.compile.drain).parTupled.void

          Stream.bracket(outcomes.start)(f => f.cancel >> f.joinWithUnit) >>
            c.stream.append(Stream(Chunk.singleton(WebSocketFrame.close))).unchunks
        }

        mapToReadStream[WebSocketFrame, WebSocketFrame](Stream.force(mergedStream), identity)
      }

      override def webSocketPipe[REQ, RESP](
          readStream: ReadStream[WebSocketFrame],
          pipe: streams.Pipe[REQ, RESP],
          o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Fs2Streams[F]]
      ): ReadStream[WebSocketFrame] = {
        val stream = fromReadStreamInternal(readStream)

        if ((!o.autoPongOnPing) && o.autoPing.isEmpty) {
          fastPath(stream, pipe, o)
        } else {
          standardPath(stream, pipe, o)
        }
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
