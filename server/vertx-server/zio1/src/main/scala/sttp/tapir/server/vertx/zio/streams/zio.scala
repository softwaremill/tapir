package sttp.tapir.server.vertx.zio

import _root_.zio._
import _root_.zio.stream.{Stream, ZStream}
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.server.vertx.streams.ReadStreamState._
import sttp.tapir.server.vertx.streams._
import sttp.tapir.server.vertx.streams.websocket._
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.WebSocketFrame
import zio.clock.Clock

import scala.collection.immutable.{Queue => SQueue}
import scala.language.postfixOps

package object streams {

  implicit class DeferredOps[A](dfd: Promise[Nothing, A]) extends DeferredLike[UIO, A] {
    override def complete(a: A): UIO[Unit] =
      dfd.done(Exit.Success(a)).unit

    override def get: UIO[A] =
      dfd.await
  }

  def zioReadStreamCompatible[R](opts: VertxZioServerOptions[R])(implicit
      runtime: Runtime[Any]
  ): ReadStreamCompatible[ZioStreams] = new ReadStreamCompatible[ZioStreams] {

    override val streams: ZioStreams = ZioStreams

    override def asReadStream(stream: Stream[Throwable, Byte]): ReadStream[Buffer] =
      mapToReadStream[Chunk[Byte], Buffer](
        stream.mapChunks(chunk => Chunk.single(chunk)),
        chunk => Buffer.buffer(chunk.toArray)
      )

    private def mapToReadStream[I, O](stream: Stream[Throwable, I], fn: I => O): ReadStream[O] =
      runtime
        .unsafeRunSync(for {
          promise <- Promise.make[Nothing, Unit]
          state <- Ref.make(StreamState.empty[UIO, O](promise))
          _ <- stream.foreach { chunk =>
            val buffer = fn(chunk)
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
        } yield new ReadStream[O] { self =>
          override def handler(handler: Handler[O]): ReadStream[O] =
            runtime
              .unsafeRunSync(state.update(_.copy(handler = handler)))
              .toEither
              .fold(throw _, _ => self)

          override def exceptionHandler(handler: Handler[Throwable]): ReadStream[O] =
            runtime
              .unsafeRunSync(state.update(_.copy(errorHandler = handler)))
              .toEither
              .fold(throw _, _ => self)

          override def endHandler(handler: Handler[Void]): ReadStream[O] =
            runtime
              .unsafeRunSync(state.update(_.copy(endHandler = handler)))
              .toEither
              .fold(throw _, _ => self)

          override def pause(): ReadStream[O] =
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

          override def resume(): ReadStream[O] =
            runtime
              .unsafeRunSync(for {
                oldState <- state.getAndUpdate(_.copy(paused = None))
                _ <- oldState.paused.fold[UIO[Any]](UIO.unit)(_.complete(()))
              } yield self)
              .toEither
              .fold(throw _, identity)

          override def fetch(x: Long): ReadStream[O] =
            self
        })
        .toEither
        .fold(throw _, identity)

    override def fromReadStream(readStream: ReadStream[Buffer], maxBytes: Option[Long]): Stream[Throwable, Byte] = {
      fromReadStreamInternal(readStream).mapConcatChunk(buffer => Chunk.fromArray(buffer.getBytes))
    }

    private def fromReadStreamInternal[T](readStream: ReadStream[T]): Stream[Throwable, T] =
      runtime
        .unsafeRunSync(for {
          stateRef <- Ref.make(ReadStreamState[UIO, T](Queued(SQueue.empty), Queued(SQueue.empty)))
          stream = ZStream.unfoldM(()) { _ =>
            for {
              dfd <- Promise.make[Nothing, WrappedBuffer[T]]
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
            val maxSize = opts.maxQueueSizeForReadStream
            runtime
              .unsafeRunSync(stateRef.modify(_.enqueue(buffer, maxSize).swap).flatMap(ZIO.foreach_(_)(identity)))
              .fold(c => throw c.squash, identity)
          }

          stream
        })
        .toEither
        .fold(throw _, identity)

    override def webSocketPipe[REQ, RESP](
        readStream: ReadStream[WebSocketFrame],
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]
    ): ReadStream[WebSocketFrame] = {
      val stream0 = fromReadStreamInternal(readStream)
      val stream1 = optionallyContatenateFrames(stream0, o.concatenateFragmentedFrames)
      val stream2 = optionallyIgnorePong(stream1, o.ignorePong)
      val autoPings = o.autoPing match {
        case Some((interval, frame)) =>
          ZStream.tick(duration.Duration.fromScala(interval)).as(frame).provideLayer(Clock.live)
        case None =>
          ZStream.empty
      }

      val stream3 = stream2
        .mapM { frame =>
          o.requests.decode(frame) match {
            case failure: DecodeResult.Failure =>
              ZIO.fail(new WebSocketFrameDecodeFailure(frame, failure))
            case DecodeResult.Value(v) =>
              ZIO.succeed(v)
          }
        }

      val stream4 = pipe(stream3)
        .map(o.responses.encode)
        .mergeTerminateLeft(autoPings)
        .concat(ZStream(WebSocketFrame.close))

      mapToReadStream[WebSocketFrame, WebSocketFrame](stream4, identity)
    }

    private def optionallyContatenateFrames(
        s: Stream[Throwable, WebSocketFrame],
        doConcatenate: Boolean
    ): Stream[Throwable, WebSocketFrame] =
      if (doConcatenate) {
        s.mapAccum(None: Accumulator)(concatenateFrames).collect { case Some(f) => f }
      } else {
        s
      }

    private def optionallyIgnorePong(
        s: Stream[Throwable, WebSocketFrame],
        ignore: Boolean
    ): Stream[Throwable, WebSocketFrame] =
      if (ignore) s.filterNot(isPong) else s
  }
}
