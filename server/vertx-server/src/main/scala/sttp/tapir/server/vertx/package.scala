package sttp.tapir.server

import io.vertx.ext.web.{Route, Router}

package object vertx {
  type VertxServerRoutes[F[_]] = ServerRoutes[F, Router => Route]
}
