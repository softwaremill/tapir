package sttp.tapir.server.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelPromise
import io.netty.handler.stream.{ChunkedFile, ChunkedStream}

sealed trait NettyResponseContent {
  def channelPromise: ChannelPromise
}

object NettyResponseContent {
  final case class ByteBufNettyResponseContent(channelPromise: ChannelPromise, byteBuf: ByteBuf) extends NettyResponseContent
  final case class ChunkedStreamNettyResponseContent(channelPromise: ChannelPromise, chunkedStream: ChunkedStream)
      extends NettyResponseContent
  final case class ChunkedFileNettyResponseContent(channelPromise: ChannelPromise, chunkedFile: ChunkedFile) extends NettyResponseContent
}
