package sttp.tapir.server.netty.internal

import sttp.capabilities.zio.ZioStreams
import sttp.model.HasHeaders
import sttp.tapir.CodecFormat
import sttp.tapir.RawBodyType
import sttp.tapir.RawBodyType.StringBody
import sttp.tapir.RawBodyType.ByteArrayBody
import sttp.tapir.RawBodyType.ByteBufferBody
import sttp.tapir.RawBodyType.InputStreamBody
import sttp.tapir.RawBodyType.FileBody
import sttp.tapir.RawBodyType.InputStreamRangeBody
import sttp.tapir.RawBodyType.MultipartBody
import zio.stream.ZStream
import zio.interop.reactivestreams._
import org.reactivestreams.Publisher
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.buffer.Unpooled

class NettyZioToResponseBody[Env](delegate: NettyToResponseBody) extends ToResponseBody[NettyResponse, ZioStreams] {
  override val streams: ZioStreams = ZioStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): NettyResponse = {
    bodyType match
      case InputStreamBody =>
        val publisher = ZStream.fromInputStream(r, NettyToResponseBody.DefaultChunkSize).toPublishe

          ReactivePublisherNettyResponseContent(ctx.newPromise(), publisher)

      case FileBody =>
      case InputStreamRangeBody =>
      case MultipartBody(partTypes, defaultType) =>
        throw new UnsupportedOperationException
      case _ => delegate.fromRawValue(v, headers, format, bodyType)    
  }

  def atoPublisher(stream: streams.BinaryStream): Publisher[HttpContent] = {
    stream.chunks.map(chunk => Unpooled.wrappedBuffer(chunk.toArray)).map(new DefaultHttpContent(_))
    .toPublisher
  }
}
