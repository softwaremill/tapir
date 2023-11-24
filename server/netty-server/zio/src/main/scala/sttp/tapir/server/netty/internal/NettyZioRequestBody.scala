package sttp.tapir.server.netty.internal

import org.playframework.netty.http.StreamedHttpRequest
import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.FullHttpRequest
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.RawBodyType._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, TapirFile}
import zio.interop.reactivestreams._
import zio.stream.{ZStream, _}
import zio.{Chunk, RIO, ZIO}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import sttp.tapir.DecodeResult
import sttp.capabilities.StreamMaxLengthExceededException

private[netty] class NettyZioRequestBody[Env](createFile: ServerRequest => RIO[Env, TapirFile])
    extends RequestBody[RIO[Env, *], ZioStreams] {

  override val streams: ZioStreams = ZioStreams

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): RIO[Env, RawValue[R]] = {

    def nettyRequestBytes: RIO[Env, Array[Byte]] = serverRequest.underlying match {
      case req: FullHttpRequest =>
        val buf = req.content()
        maxBytes
          .map(max =>
            if (buf.readableBytes() > max)
              ZIO.fail(StreamMaxLengthExceededException(max))
            else
              ZIO.succeed(ByteBufUtil.getBytes(buf))
          )
          .getOrElse(ZIO.succeed(ByteBufUtil.getBytes(buf)))

      case _: StreamedHttpRequest => toStream(serverRequest, maxBytes).run(ZSink.collectAll[Byte]).map(_.toArray)
      case other => ZIO.fail(new UnsupportedOperationException(s"Unexpected Netty request of type ${other.getClass().getName()}"))
    }
    bodyType match {
      case StringBody(charset) => nettyRequestBytes.map(bs => RawValue(new String(bs, charset)))

      case ByteArrayBody =>
        nettyRequestBytes.map(RawValue(_))
      case ByteBufferBody =>
        nettyRequestBytes.map(bs => RawValue(ByteBuffer.wrap(bs)))
      case InputStreamBody =>
        nettyRequestBytes.map(bs => RawValue(new ByteArrayInputStream(bs)))
      case InputStreamRangeBody =>
        nettyRequestBytes.map(bs => RawValue(InputStreamRange(() => new ByteArrayInputStream(bs))))
      case FileBody =>
        createFile(serverRequest)
          .flatMap(tapirFile => {
            toStream(serverRequest, maxBytes)
              .run(ZSink.fromFile(tapirFile))
              .map(_ => RawValue(FileRange(tapirFile), Seq(FileRange(tapirFile))))
          })
      case MultipartBody(partTypes, defaultType) =>
        throw new java.lang.UnsupportedOperationException()
    }
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = {
    val stream = serverRequest.underlying
      .asInstanceOf[StreamedHttpRequest]
      .toZIOStream()
      .flatMap(httpContent => ZStream.fromChunk(Chunk.fromByteBuffer(httpContent.content.nioBuffer())))
    maxBytes.map(ZioStreams.limitBytes(stream, _)).getOrElse(stream)
  }
}
