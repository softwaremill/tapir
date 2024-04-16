package sttp.tapir.server.netty.loom.internal.ws

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import org.reactivestreams.Processor
import ox.*
import ox.channels.Source
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.server.netty.internal.ws.WebSocketFrameConverters._
import sttp.tapir.server.netty.loom.OxStreams
import sttp.tapir.server.netty.loom.internal.ox.OxDispatcher
import sttp.tapir.server.netty.loom.internal.reactivestreams.OxProcessor
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}

private[loom] object OxSourceWebSocketProcessor {
  def apply[REQ, RESP](
      oxDispatcher: ox.channels.ActorRef[OxDispatcher],
      pipe: OxStreams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[OxStreams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams],
      ctx: ChannelHandlerContext
  ): Processor[WebSocketFrame, WebSocketFrame] = {
    val frame2FramePipe: OxStreams.Pipe[WebSocketFrame, WebSocketFrame] = Ox ?=>
      (source: Source[WebSocketFrame]) => {
        pipe(
          source
            .map { f =>
              val sttpFrame = nettyFrameToFrame(f)
              f.release()
              sttpFrame
            } // TODO concatenate frames
            .map(f =>
              o.requests.decode(f) match {
                case x: DecodeResult.Value[REQ]    => x.v
                case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
              }
            )
        )
          .map(r => frameToNettyFrame(o.responses.encode(r)))
      }
    new OxProcessor(oxDispatcher, frame2FramePipe)
  }
}
