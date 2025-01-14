package sttp.tapir.server.vertx.streams

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.{ServerWebSocket, WebSocketFrame => VertxWebSocketFrame}
import io.vertx.core.streams.{ReadStream, WriteStream}
import sttp.ws.WebSocketFrame
import sttp.ws.WebSocketFrame._

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

object Pipe {
  private sealed trait Action
  private case object Skip extends Action
  private case object Stop extends Action

  private sealed trait Command extends Action
  private case object Pause extends Command
  private case object Resume extends Command

  @tailrec
  def modify[A, B](ref: AtomicReference[A], f: A => (A, B)): B = {
    val oldA = ref.get
    val (newA, b) = f(oldA)
    if (ref.compareAndSet(oldA, newA)) {
      b
    } else {
      modify(ref, f)
    }
  }

  // Resume and Pause actions can be received simultanuously. This class is used in order to
  // process these actions sequentially.
  private case class BackpressureState(
      // Shows whether Resume or Pause is processed right now.
      inProgress: Boolean,
      // Counts difference between received Resume and Pause events.
      // Sometimes two Resume events can come in row. In this case second event can be ignored.
      // For example, if active stream receives Resume and then Pause. Resume must be ignored
      // because stream is already active. But Pause event also must be ignored because it
      // compensates previous Resume event.
      // This means that we should resume stream only if count is zero and pause stream only
      // if count is one.
      count: Int,
      // Queue with unprocessed actions
      queue: List[Command]
  )

  @tailrec
  private def applyBackpressureCommands[T](ref: AtomicReference[BackpressureState], request: ReadStream[T]): Unit =
    modify[BackpressureState, Action](
      ref,
      {
        case state @ (BackpressureState(false, _, Nil) | BackpressureState(true, _, _)) =>
          (state, Stop)

        case BackpressureState(false, 0, Resume :: tail) =>
          (BackpressureState(inProgress = true, 1, tail), Resume)
        case BackpressureState(false, i, Resume :: tail) =>
          (BackpressureState(inProgress = true, i + 1, tail), Skip)

        case BackpressureState(false, 1, Pause :: tail) =>
          (BackpressureState(inProgress = true, 0, tail), Pause)
        case BackpressureState(false, i, Pause :: tail) =>
          (BackpressureState(inProgress = true, i - 1, tail), Skip)
      }
    ) match {
      case Stop =>
        ()
      case act =>
        if (act == Resume) request.resume() else if (act == Pause) request.pause() else ()
        ref updateAndGet {
          case BackpressureState(true, i, commands) => BackpressureState(inProgress = false, i, commands)
          case unexpected                           => throw new Exception(s"Unexpected state $unexpected")
        }
        applyBackpressureCommands(ref, request)
    }

  // End handler can be called before all buffers are sent through data handler.
  // If endHandler unconditionally ends writeStream then part of data can be lost.
  // AtomicReference with ProgressState allows to delay ending writeStream until
  // last buffer.
  private case class ProgressState(inProgress: Int, completed: Boolean)

  def apply(request: ReadStream[Buffer], writeStream: WriteStream[Buffer]): Unit = {
    val progress = new AtomicReference(ProgressState(0, completed = false))
    val backpressure = new AtomicReference(BackpressureState(inProgress = false, 1, Nil))

    writeStream.drainHandler { _ =>
      backpressure.updateAndGet(state => state.copy(queue = state.queue :+ Resume))
      applyBackpressureCommands(backpressure, request)
    }

    request.handler((data: Buffer) => {
      progress.getAndUpdate(s => s.copy(s.inProgress + 1))
      writeStream.write(
        data,
        _ => {
          val state = progress.updateAndGet(s => s.copy(s.inProgress - 1))
          if (state.inProgress == 0 && state.completed) writeStream.end()
          ()
        }
      )
      if (writeStream.writeQueueFull()) {
        backpressure.updateAndGet(state => state.copy(queue = state.queue :+ Pause))
        applyBackpressureCommands(backpressure, request)
      }
      ()
    })
    request.endHandler { _ =>
      val state = progress.updateAndGet(_.copy(completed = true))
      if (state.inProgress == 0) writeStream.end()
      ()
    }
    request.exceptionHandler { _ =>
      writeStream.end()
      ()
    }

    request.resume()
    ()
  }

  def apply(request: ReadStream[WebSocketFrame], socket: ServerWebSocket): Unit = {
    val progress = new AtomicReference(ProgressState(0, completed = false))
    val backpressure = new AtomicReference(BackpressureState(inProgress = false, 1, Nil))

    socket.drainHandler { _ =>
      backpressure.updateAndGet(state => state.copy(queue = state.queue :+ Resume))
      applyBackpressureCommands(backpressure, request)
    }

    def writeFrame(frame: VertxWebSocketFrame): Unit =
      socket.writeFrame(
        frame,
        _ => {
          val state = progress.updateAndGet(s => s.copy(s.inProgress - 1))
          if (state.inProgress == 0 && state.completed) socket.end()
          ()
        }
      ): Unit

    request.handler((sttpFrame: WebSocketFrame) => {
      progress.getAndUpdate(s => s.copy(s.inProgress + 1))

      sttpFrame match {
        case Text(payload, finalFragment, _) =>
          writeFrame(VertxWebSocketFrame.textFrame(payload, finalFragment))
        case Binary(payload, finalFragment, _) =>
          writeFrame(VertxWebSocketFrame.binaryFrame(Buffer.buffer(payload), finalFragment))
        case Ping(payload) =>
          writeFrame(VertxWebSocketFrame.pingFrame(Buffer.buffer(payload)))
        case Pong(payload) =>
          writeFrame(VertxWebSocketFrame.pongFrame(Buffer.buffer(payload)))
        case Close(statusCode, _) =>
          socket.close(statusCode.toShort, { _ => () })
      }

      if (!socket.isClosed && socket.writeQueueFull()) {
        backpressure.updateAndGet(state => state.copy(queue = state.queue :+ Pause))
        applyBackpressureCommands(backpressure, request)
      }
      ()
    })
    request.endHandler { _ =>
      val state = progress.updateAndGet(_.copy(completed = true))
      if (state.inProgress == 0) { val _ = socket.close(1011.toShort) }
      ()
    }
    request.exceptionHandler { _ =>
      socket.end()
      ()
    }

    request.resume()
    ()
  }
}
