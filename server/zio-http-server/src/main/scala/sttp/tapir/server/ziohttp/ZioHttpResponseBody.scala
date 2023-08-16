package sttp.tapir.server.ziohttp

import zio.stream.ZStream
import zio.Chunk

sealed trait ZioHttpResponseBody {
  def contentLength: Option[Long]
}

case class ZioStreamHttpResponseBody(stream: ZStream[Any, Throwable, Byte], contentLength: Option[Long])
    extends ZioHttpResponseBody

case class ZioRawHttpResponseBody(bytes: Chunk[Byte], contentLength: Option[Long]) extends ZioHttpResponseBody
