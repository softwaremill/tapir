package sttp.tapir.server.armeria

import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerRoutes

package object cats {
  type ArmeriaCatsServerRoutes[F[_]] = ServerRoutes[F, TapirService[Fs2Streams[F], F]]
}
