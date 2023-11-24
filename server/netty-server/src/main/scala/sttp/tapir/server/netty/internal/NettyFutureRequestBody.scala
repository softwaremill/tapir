package sttp.tapir.server.netty.internal

import io.netty.buffer.{ByteBufInputStream, ByteBufUtil}
import io.netty.handler.codec.http.FullHttpRequest
import sttp.capabilities
import sttp.monad.MonadError
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.monad.syntax._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.{RawValue, RequestBody}

import java.nio.ByteBuffer
import java.nio.file.Files
import io.netty.buffer.ByteBuf
import sttp.tapir.DecodeResult
import sttp.capabilities.StreamMaxLengthExceededException
import org.playframework.netty.http.StreamedHttpRequest
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import reactivestreams._
import java.io.ByteArrayInputStream

class NettyFutureRequestBody(createFile: ServerRequest => Future[TapirFile])(implicit ec: ExecutionContext) extends RequestBody[Future, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW], maxBytes: Option[Long]): Future[RawValue[RAW]] = {

    def byteBuf: Future[ByteBuffer] = {
      val subscriber = new SimpleSubscriber() // TODO add limiting bytes
      nettyRequest(serverRequest).subscribe(subscriber)
      subscriber.future 
    }

    def requestContentAsByteArray: Future[Array[Byte]] = byteBuf.map(_.array)

    bodyType match {
      case RawBodyType.StringBody(charset) => requestContentAsByteArray.map(ba => RawValue(new String(ba, charset)))
      case RawBodyType.ByteArrayBody       => requestContentAsByteArray.map(ba => RawValue(ba))
      case RawBodyType.ByteBufferBody      => byteBuf.map(buf => RawValue(buf))
      // InputStreamBody and InputStreamRangeBody can be further optimized to avoid loading all data in memory
      case RawBodyType.InputStreamBody     => requestContentAsByteArray.map(ba => RawValue(new ByteArrayInputStream(ba))) 
      case RawBodyType.InputStreamRangeBody =>
        requestContentAsByteArray.map(ba => RawValue(InputStreamRange(() => new ByteArrayInputStream(ba))))
      case RawBodyType.FileBody =>
          createFile(serverRequest)
            .flatMap(file => // TODO wrap with limiting of the stream 
              FileWriterSubscriber.writeAll(nettyRequest(serverRequest), file.toPath).map(
                _ => RawValue(FileRange(file), Seq(FileRange(file)))
                )              
            )        
      case _: RawBodyType.MultipartBody => ???
    }
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException()

  private def nettyRequest(serverRequest: ServerRequest): StreamedHttpRequest = serverRequest.underlying.asInstanceOf[StreamedHttpRequest]
}

