package sttp.tapir.server.netty.sync.internal

import io.netty.handler.codec.http.HttpContent
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.capabilities
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir.TapirFile
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.internal.NettyRequestBody
import sttp.tapir.server.netty.internal.reactivestreams.{FileWriterSubscriber, SimpleSubscriber}
import sttp.tapir.server.netty.sync.*
import sttp.tapir.RawBodyType
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.RawPart
import java.io.File

private[sync] class NettySyncRequestBody(val createFile: ServerRequest => TapirFile) extends NettyRequestBody[Identity, OxStreams]:

  override given monad: MonadError[Identity] = IdentityMonad
  override val streams: capabilities.Streams[OxStreams] = OxStreams

  override def publisherToBytes(publisher: Publisher[HttpContent], contentLength: Option[Long], maxBytes: Option[Long]): Array[Byte] =
    SimpleSubscriber.processAllBlocking(publisher, contentLength, maxBytes)

  override def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): RawValue[Seq[RawPart]] = throw new UnsupportedOperationException("Multipart requests not supported.")
  override def writeBytesToFile(bytes: Array[Byte], file: File) = throw new UnsupportedOperationException()

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): Unit =
    serverRequest.underlying match
      case r: StreamedHttpRequest => FileWriterSubscriber.processAllBlocking(r, file.toPath, maxBytes)
      case _                      => () // Empty request

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException()
