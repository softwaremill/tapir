package sttp.tapir.server.ziohttp
import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.zio.ZioStreams.Pipe
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.{WebSocketFrame => SttpWebSocketFrame}
import zio.http.ChannelEvent.Read
import zio.http.{ChannelEvent, WebSocketChannel, WebSocketChannelEvent, WebSocketFrame => ZioWebSocketFrame}
import zio.stream.ZStream
import zio.{Chunk, Task, ZIO, stream}

object ZioWebSockets {
  private val NormalClosureStatusCode = 1000
  private val AbnormalClosureStatusCode = 1006

  def pipeToBody[REQ, RESP](pipe: Pipe[REQ, RESP], o: WebSocketBodyOutput[Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]): WebSocketHandler =
    channel => {
      val reqToRespPipeline = reqToResp(pipe, o)

      channel.receiveAll {
        case ChannelEvent.Read(ZioWebSocketFrame.Ping) if o.autoPongOnPing =>
          channel.send(Read(ZioWebSocketFrame.Pong))
        case ChannelEvent.Read(message) if message.isFinal =>
          processWebSocketFrame(channel, reqToRespPipeline, message)
        case ChannelEvent.Read(message) => // Fragmented message
          for {
            message <- accumulateFrames(channel, message)
            response <- processWebSocketFrame(channel, reqToRespPipeline, message)
          } yield response
        case ChannelEvent.Unregistered =>
          channel.send(Read(ZioWebSocketFrame.close(NormalClosureStatusCode)))
        case _ =>
          ZIO.unit
      }
    }

  private def reqToResp[REQ, RESP](
      pipe: Pipe[REQ, RESP],
      o: WebSocketBodyOutput[Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]
  ): stream.Stream[Throwable, ZioWebSocketFrame] => stream.Stream[Throwable, ZioWebSocketFrame] = {
    in: stream.Stream[Throwable, ZioWebSocketFrame] =>
      in
        .map(zFrameToFrame)
        .map {
          case SttpWebSocketFrame.Close(_, _) if !o.decodeCloseRequests => None
          case SttpWebSocketFrame.Pong(_) if o.ignorePong               => None
          case f: SttpWebSocketFrame =>
            o.requests.decode(f) match {
              case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
              case DecodeResult.Value(v)         => Some(v)
            }
        }
        .collectWhileSome
        .viaFunction(pipe)
        .map(o.responses.encode)
        .map(frameToZFrame)
  }

  private def processWebSocketFrame(
      channel: WebSocketChannel,
      body: stream.Stream[Throwable, ZioWebSocketFrame] => stream.Stream[Throwable, ZioWebSocketFrame],
      message: ZioWebSocketFrame
  ) = {
    ZStream
      .from(message)
      .viaFunction(body)
      .mapZIO(wsf => channel.send(Read(wsf)))
      .runDrain
  }

  private def accumulateFrames(channel: WebSocketChannel, webSocketFrame: ZioWebSocketFrame): Task[ZioWebSocketFrame] = {
    ZIO.iterate(webSocketFrame)(!_.isFinal) { wsf =>
      for {
        channelEvent <- channel.receive
        accumulatedWebSocketFrame <- handleChannelEvent(channel, channelEvent, wsf)
      } yield accumulatedWebSocketFrame
    }
  }

  private def handleChannelEvent(
      channel: WebSocketChannel,
      channelEvent: WebSocketChannelEvent,
      acc: ZioWebSocketFrame
  ): Task[ZioWebSocketFrame] = {
    channelEvent match {
      case ChannelEvent.ExceptionCaught(cause) =>
        channel.send(Read(ZioWebSocketFrame.close(AbnormalClosureStatusCode, Some(cause.getMessage)))) *>
          channel.shutdown.map(_ => acc)
      case Read(ZioWebSocketFrame.Continuation(newBuffer)) =>
        acc match {
          case b @ ZioWebSocketFrame.Binary(bytes) => ZIO.succeed(b.copy(bytes ++ newBuffer))
          case t @ ZioWebSocketFrame.Text(text)    => ZIO.succeed(t.copy(text + new String(newBuffer.toArray)))
          case ZioWebSocketFrame.Close(status, reason) =>
            ZIO.fail(new RuntimeException(s"Received unexpected close frame: $status, $reason"))
          case ZioWebSocketFrame.Continuation(buffer) =>
            channel.send(Read(ZioWebSocketFrame.Continuation(buffer))).map(_ => acc)
          case ZioWebSocketFrame.Ping => channel.send(Read(ZioWebSocketFrame.Pong)).map(_ => acc)
          case ZioWebSocketFrame.Pong => channel.send(Read(ZioWebSocketFrame.Ping)).map(_ => acc)
          case _                      => ZIO.succeed(acc)
        }
      case _ => ZIO.succeed(acc)
    }
  }
  private def zFrameToFrame(f: ZioWebSocketFrame): SttpWebSocketFrame =
    f match {
      case ZioWebSocketFrame.Text(text)            => SttpWebSocketFrame.Text(text, f.isFinal, rsv = None)
      case ZioWebSocketFrame.Binary(buffer)        => SttpWebSocketFrame.Binary(buffer.toArray, f.isFinal, rsv = None)
      case ZioWebSocketFrame.Continuation(buffer)  => SttpWebSocketFrame.Binary(buffer.toArray, f.isFinal, rsv = None)
      case ZioWebSocketFrame.Ping                  => SttpWebSocketFrame.ping
      case ZioWebSocketFrame.Pong                  => SttpWebSocketFrame.pong
      case ZioWebSocketFrame.Close(status, reason) => SttpWebSocketFrame.Close(status, reason.getOrElse(""))
      case _                                       => SttpWebSocketFrame.Binary(Array.empty[Byte], f.isFinal, rsv = None)
    }

  private def frameToZFrame(f: SttpWebSocketFrame): ZioWebSocketFrame =
    f match {
      case SttpWebSocketFrame.Text(p, finalFragment, _)   => ZioWebSocketFrame.Text(p, finalFragment)
      case SttpWebSocketFrame.Binary(p, finalFragment, _) => ZioWebSocketFrame.Binary(Chunk.fromArray(p), finalFragment)
      case SttpWebSocketFrame.Ping(_)                     => ZioWebSocketFrame.Ping
      case SttpWebSocketFrame.Pong(_)                     => ZioWebSocketFrame.Pong
      case SttpWebSocketFrame.Close(code, reason)         => ZioWebSocketFrame.Close(code, Some(reason))
    }
}
