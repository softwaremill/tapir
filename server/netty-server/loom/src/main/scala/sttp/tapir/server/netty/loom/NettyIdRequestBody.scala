package sttp.tapir.server.netty.loom

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.capabilities
import sttp.monad.MonadError
import sttp.tapir.TapirFile
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.internal.reactivestreams.NettyRequestBody

class NettyIdRequestBody(val createFile: ServerRequest => TapirFile) extends NettyRequestBody[Id, NoStreams] {

  override implicit val monad: MonadError[Id] = idMonad
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def publisherToBytes(publisher: Publisher[HttpContent], maxBytes: Option[Long]): Array[Byte] =
    ??? // TODO
    // SimpleSubscriber.processAll(publisher, maxBytes) returns Future

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): Unit =
    ??? // TODO

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException()
}
