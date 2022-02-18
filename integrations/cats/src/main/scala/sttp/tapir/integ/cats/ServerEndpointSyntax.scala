package sttp.tapir.integ.cats

import cats.~>
import sttp.tapir.server.ServerEndpoint

trait ServerEndpointSyntax {
  implicit class ServerEndpointImapK[R, F[_]](endpoint: ServerEndpoint[R, F]) {
    import MonadErrorSyntax._

    def imapK[G[_]](fk: F ~> G)(gk: G ~> F): ServerEndpoint[R, G] =
      ServerEndpoint(
        endpoint.endpoint,
        securityLogic = monadError => (a: endpoint.SECURITY_INPUT) => fk(endpoint.securityLogic(monadError.imapK(gk)(fk))(a)),
        logic = monadError => (u: endpoint.PRINCIPAL) => (i: endpoint.INPUT) => fk(endpoint.logic(monadError.imapK(gk)(fk))(u)(i))
      )
  }
}
