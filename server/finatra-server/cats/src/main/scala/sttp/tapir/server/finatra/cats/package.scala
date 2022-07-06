package sttp.tapir.server.finatra

import sttp.tapir.server.ServerRoutes

package object cats {
  type FinatraCatsServerRoutes[F[_]] = ServerRoutes[F, FinatraRoute]
}
