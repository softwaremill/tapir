package sttp.tapir.server.netty.internal

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpContent
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.content._
import org.apache.http.entity.mime.{FormBodyPart, FormBodyPartBuilder, MultipartEntityBuilder}
import org.reactivestreams.Publisher
import sttp.capabilities
import sttp.model.{HasHeaders, Part}
import sttp.monad.MonadError
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent.{ByteBufNettyResponseContent, ReactivePublisherNettyResponseContent}
import sttp.tapir.server.netty.internal.NettyToResponseBody.DefaultChunkSize
import sttp.tapir.server.netty.internal.reactivestreams.{FileRangePublisher, InputStreamPublisher}
import sttp.tapir.{CodecFormat, FileRange, InputStreamRange, RawBodyType, RawPart, WebSocketBodyOutput}

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

/** Common logic for producing response body from responses in all Netty backends that don't support streaming. These backends use our
  * custom reactive Publishers to integrate responses like InputStreamBody, InputStreamRangeBody or FileBody with Netty reactive extensions.
  * Other kinds of raw responses like directly available String, ByteArray or ByteBuffer can be returned without wrapping into a Publisher.
  */
private[netty] class NettyToResponseBody[F[_]](runAsync: RunAsync[F])(implicit me: MonadError[F])
    extends ToResponseBody[NettyResponse, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): NettyResponse = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val bytes = v.asInstanceOf[String].getBytes(charset)
        (ctx: ChannelHandlerContext) => ByteBufNettyResponseContent(ctx.newPromise(), Unpooled.wrappedBuffer(bytes))

      case RawBodyType.ByteArrayBody =>
        val bytes = v.asInstanceOf[Array[Byte]]
        (ctx: ChannelHandlerContext) => ByteBufNettyResponseContent(ctx.newPromise(), Unpooled.wrappedBuffer(bytes))

      case RawBodyType.ByteBufferBody =>
        val byteBuffer = v.asInstanceOf[ByteBuffer]
        (ctx: ChannelHandlerContext) => ByteBufNettyResponseContent(ctx.newPromise(), Unpooled.wrappedBuffer(byteBuffer))

      case RawBodyType.InputStreamBody =>
        (ctx: ChannelHandlerContext) => ReactivePublisherNettyResponseContent(ctx.newPromise(), wrap(v))

      case RawBodyType.InputStreamRangeBody =>
        (ctx: ChannelHandlerContext) => ReactivePublisherNettyResponseContent(ctx.newPromise(), wrap(v))

      case RawBodyType.FileBody => (ctx: ChannelHandlerContext) =>
        ReactivePublisherNettyResponseContent(ctx.newPromise(), wrap(v))

      case m: RawBodyType.MultipartBody =>
        val entity = MultipartEntityBuilder.create()
        v.flatMap(rawPartToFormBodyPart(m, _)).foreach { (formBodyPart: FormBodyPart) => entity.addPart(formBodyPart) }
        val builtEntity = entity.build()

        (ctx: ChannelHandlerContext) => {
          // TODO: This call is blocking, we need to wrap it appropriately when we have non-blocking multipart implementations
          val inputStream = builtEntity.getContent
          ReactivePublisherNettyResponseContent(ctx.newPromise(), wrap(inputStream))
        }
    }
  }

  private def wrap(streamRange: InputStreamRange): Publisher[HttpContent] = {
    new InputStreamPublisher[F](streamRange, DefaultChunkSize, runAsync)
  }

  private def wrap(fileRange: FileRange): Publisher[HttpContent] = {
    new FileRangePublisher(fileRange, DefaultChunkSize)
  }

  private def wrap(content: InputStream): Publisher[HttpContent] = {
    wrap(InputStreamRange(() => content, range = None))
  }

  private def rawPartToFormBodyPart[R](m: RawBodyType.MultipartBody, part: Part[R]): Option[FormBodyPart] = {
    m.partType(part.name).map { partType =>
      val builder = FormBodyPartBuilder
        .create(
          part.name,
          rawValueToContentBody(partType.asInstanceOf[RawBodyType[R]], part)
        )

      part.headers.foreach(header => builder.addField(header.name, header.value))

      builder.build()
    }
  }

  private def rawValueToContentBody[R](bodyType: RawBodyType[R], part: Part[R]): ContentBody = {
    val contentType: String = part.header("content-type").getOrElse("text/plain")
    val r = part.body

    bodyType match {
      case RawBodyType.StringBody(_) =>
        new StringBody(r.toString, ContentType.parse(contentType))
      case RawBodyType.ByteArrayBody =>
        new ByteArrayBody(r, ContentType.create(contentType), part.fileName.get)
      case RawBodyType.ByteBufferBody =>
        val array: Array[Byte] = new Array[Byte](r.remaining)
        r.get(array)
        new ByteArrayBody(array, ContentType.create(contentType), part.fileName.get)
      case RawBodyType.FileBody =>
        part.fileName match {
          case Some(filename) => new FileBody(r.file, ContentType.create(contentType), filename)
          case None           => new FileBody(r.file, ContentType.create(contentType))
        }
      case RawBodyType.InputStreamRangeBody =>
        new InputStreamBody(r.inputStream(), ContentType.create(contentType), part.fileName.get)
      case RawBodyType.InputStreamBody =>
        new InputStreamBody(r, ContentType.create(contentType), part.fileName.get)
      case _: RawBodyType.MultipartBody =>
        throw new UnsupportedOperationException("Nested multipart messages are not supported.")
    }
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): NettyResponse = throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
  ): NettyResponse = throw new UnsupportedOperationException
}

private[netty] object NettyToResponseBody {
  val DefaultChunkSize = 8192
}
