package sttp.tapir.server.vertx.zio

import _root_.zio._
import _root_.zio.stream.{Stream, ZStream}
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.server.vertx.streams.ReadStreamState.{WrappedBuffer, WrappedEvent}
import sttp.tapir.server.vertx.streams._
import sttp.tapir.server.vertx.streams.websocket._
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.WebSocketFrame

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
      mapToReadStream[Chunk[Byte], Buffer](stream.chunks, chunk => Buffer.buffer(chunk.toArray))

    private def mapToReadStream[I, O](stream: Stream[Throwable, I], fn: I => O): ReadStream[O] =
      unsafeRunSync(for {
        promise <- Promise.make[Nothing, Unit]
        state <- Ref.make(StreamState.empty[UIO, O](promise))
        _ <- stream.runForeach { chunk =>
          val buffer = fn(chunk)
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
      } yield new ReadStream[O] { self =>
        override def handler(handler: Handler[O]): ReadStream[O] =
          unsafeRunSync(state.update(_.copy(handler = handler))).toEither
            .fold(throw _, _ => self)

        override def exceptionHandler(handler: Handler[Throwable]): ReadStream[O] =
          unsafeRunSync(state.update(_.copy(errorHandler = handler))).toEither
            .fold(throw _, _ => self)

        override def endHandler(handler: Handler[Void]): ReadStream[O] =
          unsafeRunSync(state.update(_.copy(endHandler = handler))).toEither
            .fold(throw _, _ => self)

        override def pause(): ReadStream[O] =
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

        override def resume(): ReadStream[O] =
          unsafeRunSync(for {
            oldState <- state.getAndUpdate(_.copy(paused = None))
            _ <- oldState.paused.fold[UIO[Any]](ZIO.unit)(_.complete(()))
          } yield self).toEither
            .fold(throw _, identity)

        override def fetch(x: Long): ReadStream[O] =
          self

      }).toEither
        .fold(throw _, identity)

    override def fromReadStream(readStream: ReadStream[Buffer], maxBytes: Option[Long]): Stream[Throwable, Byte] = {
      val stream = fromReadStreamInternal(readStream).mapConcatChunk(buffer => Chunk.fromArray(buffer.getBytes))
      maxBytes.map(ZioStreams.limitBytes(stream, _)).getOrElse(stream)
    }

    private def fromReadStreamInternal[T](readStream: ReadStream[T]): Stream[Throwable, T] =
      unsafeRunSync(for {
        stateRef <- Ref.make(ReadStreamState[UIO, T](Queued(SQueue.empty), Queued(SQueue.empty)))
        stream = ZStream.unfoldZIO(()) { _ =>
          for {
            dfd <- Promise.make[Nothing, WrappedBuffer[T]]
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
            .fold(c => throw c, identity): Unit
        }
        readStream.exceptionHandler { cause =>
          unsafeRunSync(stateRef.modify(_.halt(Some(cause)).swap).flatMap(ZIO.foreachDiscard(_)(identity)))
            .fold(c => throw c, identity): Unit
        }
        readStream.handler { buffer =>
          val maxSize = opts.maxQueueSizeForReadStream
          unsafeRunSync(stateRef.modify(_.enqueue(buffer, maxSize).swap).flatMap(ZIO.foreachDiscard(_)(identity)))
            .fold(c => throw c, identity): Unit
        }

        stream
      }).toEither
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
          ZStream.tick(Duration.fromScala(interval)).as(frame)
        case None =>
          ZStream.empty
      }

      val stream3 = stream2
        .mapZIO { frame =>
          o.requests.decode(frame) match {
            case failure: DecodeResult.Failure =>
              ZIO.fail(new WebSocketFrameDecodeFailure(frame, failure))
            case DecodeResult.Value(v) =>
              ZIO.succeed(v)
          }
        }

      val stream4 = pipe(stream3)
        .map(o.responses.encode)
        .mergeHaltLeft(autoPings)
        .concat(ZStream.from(WebSocketFrame.close))

      mapToReadStream[WebSocketFrame, WebSocketFrame](stream4, identity)
    }

    def unsafeRunSync[T](task: Task[T]): Exit[Throwable, T] =
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.run(task)
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

    private def optionallyIgnorePong(s: Stream[Throwable, WebSocketFrame], ignore: Boolean): Stream[Throwable, WebSocketFrame] =
      if (ignore) s.filterNot(isPong) else s
  }
}
