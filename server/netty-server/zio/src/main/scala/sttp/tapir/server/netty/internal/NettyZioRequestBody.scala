package sttp.tapir.server.netty.internal

import sttp.capabilities.zio.ZioStreams
import zio.RIO
import sttp.tapir.server.interpreter.RequestBody
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.model.ServerRequest
import sttp.tapir.RawBodyType
import sttp.tapir.RawBodyType._
import sttp.tapir.TapirFile
import zio.interop.reactivestreams._
import com.typesafe.netty.http.StreamedHttpRequest
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.buffer.ByteBufUtil
import zio.ZIO
import zio.stream._
import io.netty.buffer.ByteBufUtil
import scala.sys.process.ProcessBuilder.Sink
import zio.stream.ZStream
import zio.Chunk

import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, TapirFile}
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

private[netty] class NettyZioRequestBody[Env](createFile: ServerRequest => RIO[Env, TapirFile])
    extends RequestBody[RIO[Env, *], ZioStreams] {

  override val streams: ZioStreams = ZioStreams

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R]): RIO[Env, RawValue[R]] = {
    bodyType match
      case StringBody(charset) => nettyRequestBytes(serverRequest).map(bs => RawValue(new String(bs, charset)))

      case ByteArrayBody =>
        nettyRequestBytes(serverRequest).map(RawValue(_))
      case ByteBufferBody =>
        nettyRequestBytes(serverRequest).map(bs => RawValue(ByteBuffer.wrap(bs)))
      case InputStreamBody =>
        nettyRequestBytes(serverRequest).map(bs => RawValue(new ByteArrayInputStream(bs)))
      case InputStreamRangeBody =>
        nettyRequestBytes(serverRequest).map(bs => RawValue(InputStreamRange(() => new ByteArrayInputStream(bs))))
      case FileBody =>
        throw new java.lang.UnsupportedOperationException("TODO")
      case MultipartBody(partTypes, defaultType) =>
        throw new java.lang.UnsupportedOperationException("TODO")

  }

  override def toStream(serverRequest: ServerRequest): streams.BinaryStream = {

    serverRequest.underlying
      .asInstanceOf[StreamedHttpRequest]
      .toZIOStream()
      .flatMap(httpContent => ZStream.fromChunk(Chunk.fromByteBuffer(httpContent.content.nioBuffer())))
  }

  private def nettyRequestBytes(serverRequest: ServerRequest): RIO[Env, Array[Byte]] = serverRequest.underlying match {
    case req: FullHttpRequest   => ZIO.succeed(ByteBufUtil.getBytes(req.content()))
    case _: StreamedHttpRequest => toStream(serverRequest).run(ZSink.collectAll[Byte]).map(_.toArray)
    case other => ZIO.fail(new UnsupportedOperationException(s"Unexpected Netty request of type ${other.getClass().getName()}"))
  }

}
