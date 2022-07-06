package sttp.tapir.server.armeria

import _root_.zio._
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.ZServerRoutes

package object zio {
  type ArmeriaZioServerRoutes[R] = ZServerRoutes[R, TapirService[ZioStreams, RIO[R, *]]]
}
