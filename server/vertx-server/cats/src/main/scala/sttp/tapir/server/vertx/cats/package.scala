package sttp.tapir.server.vertx

import io.vertx.ext.web.{Route, Router}
import sttp.tapir.server.ServerRoutes

package object cats {
  type VertxPlayServerRoutes[F[_]] = ServerRoutes[F, Router => Route]
}
