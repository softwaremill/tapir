package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.TapirFile
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.internal.reactivestreams.NettyRequestBody
import sttp.tapir.ztapir.RIOMonadError
import zio.interop.reactivestreams._
import zio.stream.{ZStream, _}
import zio.{Chunk, RIO}

private[netty] class NettyZioRequestBody[Env](val createFile: ServerRequest => RIO[Env, TapirFile])
    extends NettyRequestBody[RIO[Env, *], ZioStreams] {

  override val streams: ZioStreams = ZioStreams

  override implicit val monad: MonadError[RIO[Env, *]] = new RIOMonadError[Env]

  override def publisherToBytes(publisher: Publisher[HttpContent], maxBytes: Option[Long]): RIO[Env, Array[Byte]] =
    publisherToStream(publisher, maxBytes).run(ZSink.collectAll[Byte]).map(_.toArray)

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): RIO[Env, Unit] =
    toStream(serverRequest, maxBytes).run(ZSink.fromFile(file)).map(_ => ())

  override def publisherToStream(publisher: Publisher[HttpContent], maxBytes: Option[Long]): streams.BinaryStream = {
    val stream =
      Adapters
        .publisherToStream(publisher, 16)
        .flatMap(httpContent => ZStream.fromChunk(Chunk.fromByteBuffer(httpContent.content.nioBuffer())))
    maxBytes.map(ZioStreams.limitBytes(stream, _)).getOrElse(stream)
  }

  override def failedStream(e: => Throwable): streams.BinaryStream =
    ZStream.fail(e)

  override def emptyStream: streams.BinaryStream =
    ZStream.empty
}
