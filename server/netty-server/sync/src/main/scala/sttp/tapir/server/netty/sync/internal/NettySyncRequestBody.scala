package sttp.tapir.server.netty.sync.internal

import _root_.ox.Chunk
import _root_.ox.flow.Flow
import _root_.ox.flow.reactive.FlowReactiveStreams
import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.HttpContent
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir.TapirFile
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.internal.NettyRequestBody
import sttp.tapir.server.netty.internal.reactivestreams.{FileWriterSubscriber, SimpleSubscriber}
import sttp.tapir.server.netty.sync.*

private[sync] class NettySyncRequestBody(val createFile: ServerRequest => TapirFile) extends NettyRequestBody[Identity, OxStreams]:

  override given monad: MonadError[Identity] = IdentityMonad
  override val streams: OxStreams = OxStreams

  override def publisherToBytes(publisher: Publisher[HttpContent], contentLength: Option[Long], maxBytes: Option[Long]): Array[Byte] =
    SimpleSubscriber.processAllBlocking(publisher, contentLength, maxBytes)

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): Unit =
    serverRequest.underlying match
      case r: StreamedHttpRequest => FileWriterSubscriber.processAllBlocking(r, file.toPath, maxBytes)
      case _                      => () // Empty request

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): Flow[Chunk[Byte]] =
    serverRequest.underlying match
      case r: StreamedHttpRequest =>
        val rawChunkFlow = FlowReactiveStreams
          .fromPublisher(r)
          .map: httpContent =>
            val byteBuf = httpContent.content()
            val chunk = Chunk.fromArray(ByteBufUtil.getBytes(byteBuf))
            byteBuf.release()
            chunk

        maxBytes match
          case Some(max) =>
            rawChunkFlow.mapStateful(0): (bytesSoFar, chunk) =>
              val newBytesSoFar = bytesSoFar + chunk.size
              if (newBytesSoFar > max) then throw StreamMaxLengthExceededException(max)
              (newBytesSoFar, chunk)
          case None => rawChunkFlow

      case _ => Flow.empty // Empty request, return an empty stream
