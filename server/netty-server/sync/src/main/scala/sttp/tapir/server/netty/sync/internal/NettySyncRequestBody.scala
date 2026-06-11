package sttp.tapir.server.netty.sync.internal

import _root_.ox.Chunk
import _root_.ox.flow.Flow
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.multipart.HttpDataFactory
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir.{RawBodyType, RawPart, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.server.netty.NettyStreams
import sttp.tapir.server.netty.internal.{NettyHelper, NettyOsxHelper, NettyRequestBody}
import sttp.tapir.server.netty.internal.reactivestreams.{FileWriterSubscriber, SimpleSubscriber}

import java.nio.file.Files

private[sync] class NettySyncRequestBody(
    val createFile: ServerRequest => TapirFile,
    val multipartTempDirectory: Option[TapirFile],
    val multipartMinSizeForDisk: Option[Long]
) extends NettyRequestBody[Identity, NettyStreams]:

  private val httpDataFactory: HttpDataFactory = NettyHelper.createHttpDataFactory(multipartMinSizeForDisk, multipartTempDirectory)


  override given monad: MonadError[Identity] = IdentityMonad
  override val streams: NettyStreams = NettyStreams

  override def publisherToBytes(publisher: Publisher[HttpContent], contentLength: Option[Long], maxBytes: Option[Long]): Array[Byte] =
    SimpleSubscriber.processAllBlocking(publisher, contentLength, maxBytes)

  override def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): RawValue[Seq[RawPart]] =
    NettyOsxHelper.publishToMultipartF(nettyRequest, serverRequest,m, maxBytes)(httpDataFactory, toRawPart, identity)


  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): Unit =
    serverRequest.underlying match
      case r: StreamedHttpRequest => FileWriterSubscriber.processAllBlocking(r, file.toPath, maxBytes)
      case _                      => () // Empty request

  override def writeBytesToFile(bytes: Array[Byte], file: TapirFile): Unit = Files.write(file.toPath, bytes): Unit

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): Flow[Chunk[Byte]] =
    NettyOsxHelper.toStream(serverRequest, maxBytes)

