package sttp.tapir.server

import io.vertx.ext.web.{Route, Router}

import scala.concurrent.Future

package object vertx {
  type VertxFutureServerRoutes = ServerRoutes[Future, Router => Route]
}
