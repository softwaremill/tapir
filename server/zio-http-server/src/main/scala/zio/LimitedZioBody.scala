package zio

import sttp.capabilities
import sttp.capabilities.zio.ZioStreams

import zio.http.Body
import zio.Trace
import zio.http.MediaType
import zio.stream.ZStream
import zio.http.Boundary

class LimitedZioBody(delegate: Body, maxBytes: Long) extends Body {

  override def asArray(implicit trace: Trace): Task[Array[Byte]] = {
    println(s">>>>>>>>>>>>>>>>>>>> Delegating asArray to ${delegate.getClass().getName}")
    delegate.asArray
  }

  override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] = {
    println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> asStream?")
    ZioStreams.limitBytes(delegate.asStream, maxBytes)
  }

  override def mediaType: Option[MediaType] = delegate.mediaType

  override def isEmpty: Boolean = delegate.isEmpty

  override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = delegate.asChunk

  override def contentType(newMediaType: MediaType, newBoundary: Boundary): Body = delegate.contentType(newMediaType, newBoundary)

  override def contentType(newMediaType: MediaType): Body = delegate.contentType(newMediaType)

  override def isComplete: Boolean = delegate.isComplete

  override def boundary: Option[Boundary] = delegate.boundary

}
