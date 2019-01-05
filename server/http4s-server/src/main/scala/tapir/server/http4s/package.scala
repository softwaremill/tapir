package tapir.server

import cats.data.StateT

package object http4s extends Http4sServer {
  private[http4s] type Error = String
  private[http4s] type ContextState[F[_]] = StateT[Either[Error, ?], Context[F], MatchResult[F]]
}
