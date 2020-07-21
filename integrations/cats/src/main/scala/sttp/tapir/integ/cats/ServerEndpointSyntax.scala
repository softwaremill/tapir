package sttp.tapir.integ.cats

import cats.~>
import sttp.tapir.server.ServerEndpoint

trait ServerEndpointSyntax {
  implicit class ServerEndpointImapK[I, E, O, S, F[_]](endpoint: ServerEndpoint[I, E, O, S, F]) {

    import MonadErrorSyntax._

    def imapK[G[_]](fk: F ~> G)(gK: G ~> F): ServerEndpoint[I, E, O, S, G] =
      endpoint.copy(logic = monadError => i => fk(endpoint.logic(monadError.imapK(gK)(fk))(i)))
  }
}
