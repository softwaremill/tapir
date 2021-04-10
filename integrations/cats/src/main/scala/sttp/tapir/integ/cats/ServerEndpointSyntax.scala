package sttp.tapir.integ.cats

import cats.~>
import sttp.tapir.server.ServerEndpoint

trait ServerEndpointSyntax {
  implicit class ServerEndpointImapK[R, F[_]](endpoint: ServerEndpoint[R, F]) {

    import MonadErrorSyntax._

    def imapK[G[_]](fk: F ~> G)(gK: G ~> F): ServerEndpoint[R, G] =
      ServerEndpoint(endpoint.endpoint, monadError => (i: endpoint.I) => fk(endpoint.logic(monadError.imapK(gK)(fk))(i)))
  }
}
