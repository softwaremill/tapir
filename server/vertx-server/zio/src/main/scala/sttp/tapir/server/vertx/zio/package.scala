package sttp.tapir.server.vertx

import io.vertx.ext.web.{Route, Router}
import sttp.tapir.ztapir.ZServerRoutes

package object zio {
  type VertxZioServerRoutes[R] = ZServerRoutes[R, Router => Route]
}
