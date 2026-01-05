package sttp.tapir.server.netty.sync.internal

import _root_.ox.flow.Flow
import _root_.ox.flow.reactive.toReactiveStreamsPublisher
import _root_.ox.{Chunk, InScopeRunner, forkDiscard}
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.content.*
import org.apache.http.entity.mime.{FormBodyPart, FormBodyPartBuilder, MultipartEntityBuilder}
import org.reactivestreams.{Publisher, Subscriber}
import sttp.model.{HasHeaders, Part}
import sttp.monad.MonadError
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent.{
  ByteBufNettyResponseContent,
  ReactivePublisherNettyResponseContent,
  ReactiveWebSocketProcessorNettyResponseContent
}
import sttp.tapir.server.netty.internal.reactivestreams.{FileRangePublisher, InputStreamPublisher}
import sttp.tapir.server.netty.internal.RunAsync
import sttp.tapir.server.netty.sync.*
import sttp.tapir.server.netty.sync.internal.NettySyncToResponseBody.DefaultChunkSize

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

private[sync] class NettySyncToResponseBody(runAsync: RunAsync[Identity], inScopeRunner: InScopeRunner)(using
    me: MonadError[Identity]
) extends ToResponseBody[NettyResponse, OxStreams]:

  override val streams: OxStreams = OxStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): NettyResponse = {
    bodyType match
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

      case RawBodyType.FileBody => (ctx: ChannelHandlerContext) => ReactivePublisherNettyResponseContent(ctx.newPromise(), wrap(v))

      case m: RawBodyType.MultipartBody =>
        val entity = MultipartEntityBuilder.create()
        v.flatMap(rawPartToFormBodyPart(m, _)).foreach { (formBodyPart: FormBodyPart) => entity.addPart(formBodyPart) }
        val builtEntity = entity.build()

        (ctx: ChannelHandlerContext) => {
          val inputStream = builtEntity.getContent
          ReactivePublisherNettyResponseContent(ctx.newPromise(), wrap(inputStream))
        }
  }

  private def wrap(streamRange: InputStreamRange): Publisher[HttpContent] = {
    new InputStreamPublisher[Identity](streamRange, DefaultChunkSize, runAsync)
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

    bodyType match
      case RawBodyType.StringBody(_) =>
        new StringBody(r.toString, ContentType.parse(contentType))
      case RawBodyType.ByteArrayBody =>
        new ByteArrayBody(r, ContentType.create(contentType), part.fileName.get)
      case RawBodyType.ByteBufferBody =>
        val array: Array[Byte] = new Array[Byte](r.remaining)
        r.get(array)
        new ByteArrayBody(array, ContentType.create(contentType), part.fileName.get)
      case RawBodyType.FileBody =>
        part.fileName match
          case Some(filename) => new FileBody(r.file, ContentType.create(contentType), filename)
          case None           => new FileBody(r.file, ContentType.create(contentType))
      case RawBodyType.InputStreamRangeBody =>
        new InputStreamBody(r.inputStream(), ContentType.create(contentType), part.fileName.get)
      case RawBodyType.InputStreamBody =>
        new InputStreamBody(r, ContentType.create(contentType), part.fileName.get)
      case _: RawBodyType.MultipartBody =>
        throw new UnsupportedOperationException("Nested multipart messages are not supported.")
  }

  def fromStreamValue(v: Flow[Chunk[Byte]], headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): NettyResponse =
    (ctx: ChannelHandlerContext) =>
      new ReactivePublisherNettyResponseContent(
        ctx.newPromise(),
        // we can only create a publisher from `v` within a concurrency scope; using the main concurrency scope for
        // that, via `externalRunner`. As we need to return a `Publisher` immediately, deferring the flow-to-publisher
        // transformation until the subscriber is known.
        new Publisher[HttpContent]:
          override def subscribe(s: Subscriber[_ >: HttpContent]): Unit =
            inScopeRunner.async(
              forkDiscard(
                v.map(chunk => new DefaultHttpContent(Unpooled.wrappedBuffer(chunk.toArray)))
                  .toReactiveStreamsPublisher
                  .subscribe(s)
              )
            )
      )

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams]
  ): NettyResponse = (ctx: ChannelHandlerContext) =>
    val channelPromise = ctx.newPromise()
    new ReactiveWebSocketProcessorNettyResponseContent(
      channelPromise,
      ws.OxSourceWebSocketProcessor[REQ, RESP](
        inScopeRunner,
        pipe.asInstanceOf[OxStreams.Pipe[REQ, RESP]],
        o.asInstanceOf[WebSocketBodyOutput[OxStreams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams]],
        ctx
      ),
      ignorePong = o.ignorePong,
      autoPongOnPing = o.autoPongOnPing,
      decodeCloseRequests = o.decodeCloseRequests,
      autoPing = o.autoPing
    )

private[netty] object NettySyncToResponseBody:
  val DefaultChunkSize = 8192
