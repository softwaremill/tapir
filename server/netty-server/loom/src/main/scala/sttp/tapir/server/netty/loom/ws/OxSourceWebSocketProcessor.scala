package sttp.tapir.server.netty.loom.ws

import org.reactivestreams.Processor
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import sttp.tapir.server.netty.loom.OxStreams
import sttp.tapir.WebSocketBodyOutput
import io.netty.channel.ChannelHandlerContext
import sttp.tapir.server.netty.internal.ws.WebSocketFrameConverters._
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import ox.*
import ox.channels.Channel
import ox.channels.Source
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.server.netty.loom.reactivestreams.OxProcessor

object OxSourceWebSocketProcessor {
  def apply[REQ, RESP](
      pipe: OxStreams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[OxStreams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams],
      ctx: ChannelHandlerContext
  )(using Ox): Processor[WebSocketFrame, WebSocketFrame] = {
    def frame2FramePipe: OxStreams.Pipe[WebSocketFrame, WebSocketFrame] = Ox ?=>
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
    new OxProcessor(frame2FramePipe)
  }
}
