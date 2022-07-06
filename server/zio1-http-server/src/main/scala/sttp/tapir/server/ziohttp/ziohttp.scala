package sttp.tapir.server

import sttp.tapir.ztapir.ZServerRoutes
import zhttp.http.{Http, Request, Response}
import zio.stream.ZStream

package object ziohttp {

  type ZioHttpServerRoutes[R] =
    ZServerRoutes[R, Http[R, Throwable, Request, Response]]

  // a stream with optional length (if known)
  private[ziohttp] type ZioHttpResponseBody = (ZStream[Any, Throwable, Byte], Option[Long])
}
