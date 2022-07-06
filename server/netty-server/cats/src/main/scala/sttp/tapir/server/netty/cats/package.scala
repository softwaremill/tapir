package sttp.tapir.server.netty

import sttp.tapir.server.ServerRoutes

package object cats {
  type NettyServerRoutes[F[_]] = ServerRoutes[F, Route[F]]
}
