package sttp.tapir.integ.cats

import cats.~>
import sttp.tapir.server.ServerEndpoint

trait ServerEndpointSyntax {
  implicit class ServerEndpointImapK[A, U, I, E, O, S, F[_]](endpoint: ServerEndpoint[A, U, I, E, O, S, F]) {
    import MonadErrorSyntax._

    def imapK[G[_]](fk: F ~> G)(gk: G ~> F): ServerEndpoint[A, U, I, E, O, S, G] =
      endpoint.copy(
        securityLogic = monadError => a => fk(endpoint.securityLogic(monadError.imapK(gk)(fk))(a)),
        logic = monadError => u => i => fk(endpoint.logic(monadError.imapK(gk)(fk))(u)(i))
      )
  }
}
