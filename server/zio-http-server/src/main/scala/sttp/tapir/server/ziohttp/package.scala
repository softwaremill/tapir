package sttp.tapir.server

import zio.stream.ZStream

package object ziohttp {
  // a stream with optional length (if known)
  private[ziohttp] type ZioHttpResponseBody = (ZStream[Any, Throwable, Byte], Option[Long])
}
