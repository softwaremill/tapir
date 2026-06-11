package sttp.tapir.server.netty.internal
import _root_.ox.Chunk
import _root_.ox.flow.Flow
import _root_.ox.flow.reactive.FlowReactiveStreams
import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.multipart.{HttpDataFactory, HttpPostMultipartRequestDecoder, InterfaceHttpData}
import org.playframework.netty.http.StreamedHttpRequest
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.monad.MonadError
import sttp.monad.syntax.*
import sttp.tapir.{RawBodyType, RawPart}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue

object NettyOsxHelper:

  def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): Flow[Chunk[Byte]] =
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
  end toStream

  def publishToMultipartF[F[_]](
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  )(
      httpDataFactory: HttpDataFactory,
      toRawPart: (ServerRequest, InterfaceHttpData, RawBodyType[?]) => F[RawPart],
      toParts: Seq[F[RawPart]] => F[Seq[RawPart]]
  )(using MonadError[F]): F[RawValue[Seq[RawPart]]] =
    val decoder = new HttpPostMultipartRequestDecoder(httpDataFactory, nettyRequest)
    val rawParts =
      try
        val requestFlow = FlowReactiveStreams.fromPublisher(nettyRequest)
        (maxBytes match
          case Some(value) =>
            requestFlow.mapStatefulConcat(0): (bytesSoFar, httpContent) =>
              val newBytesSoFar = bytesSoFar + httpContent.content().readableBytes()
              if (newBytesSoFar > value) throw StreamMaxLengthExceededException(value)
              (newBytesSoFar, decoder.decodeChunk(httpContent))
          case None => requestFlow.mapConcat(decoder.decodeChunk)
        ).mapConcat: httpData =>
          m.partType(httpData.getName).map(partType => toRawPart(serverRequest, httpData, partType))
        .runToList()
      catch
        case t: Throwable =>
          decoder.destroy()
          throw t
    val parts: F[Seq[RawPart]] = toParts(rawParts)
    parts.map(p => RawValue.fromParts(p).copy(cleanup = Some(() => decoder.destroy())))
  end publishToMultipartF

extension (decoder: HttpPostMultipartRequestDecoder)
  private def decodeChunk(httpContent: HttpContent): Seq[InterfaceHttpData] = {
    decoder.offer(httpContent)
    Iterator.continually(maybeNext()).takeWhile(_.nonEmpty).flatten.toSeq
  }

  private def maybeNext(): Option[InterfaceHttpData] = Option.when(decoder.hasNext)(decoder.next())
