package sttp.tapir.server.netty.loom.internal

import io.netty.handler.codec.http.HttpContent
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.capabilities
import sttp.monad.MonadError
import sttp.tapir.TapirFile
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.internal.NettyRequestBody
import sttp.tapir.server.netty.internal.reactivestreams.{FileWriterSubscriber, SimpleSubscriber}
import sttp.tapir.server.netty.loom.*

private[loom] class NettyIdRequestBody(val createFile: ServerRequest => TapirFile) extends NettyRequestBody[Id, OxStreams] {

  override implicit val monad: MonadError[Id] = idMonad
  override val streams: capabilities.Streams[OxStreams] = OxStreams

  override def publisherToBytes(publisher: Publisher[HttpContent], contentLength: Option[Long], maxBytes: Option[Long]): Array[Byte] =
    SimpleSubscriber.processAllBlocking(publisher, contentLength, maxBytes)

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): Unit =
    serverRequest.underlying match {
      case r: StreamedHttpRequest => FileWriterSubscriber.processAllBlocking(r, file.toPath, maxBytes)
      case _                      => () // Empty request
    }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException()
}
