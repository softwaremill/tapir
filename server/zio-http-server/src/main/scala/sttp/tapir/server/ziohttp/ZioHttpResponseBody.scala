package sttp.tapir.server.ziohttp

import zio.stream.ZStream
import zio.Chunk

private[ziohttp] sealed trait ZioHttpResponseBody {
  def contentLength: Option[Long]
}

private[ziohttp] case class ZioStreamHttpResponseBody(stream: ZStream[Any, Throwable, Byte], val contentLength: Option[Long])
    extends ZioHttpResponseBody

private[ziohttp] case class ZioRawHttpResponseBody(bytes: Chunk[Byte], val contentLength: Option[Long]) extends ZioHttpResponseBody
