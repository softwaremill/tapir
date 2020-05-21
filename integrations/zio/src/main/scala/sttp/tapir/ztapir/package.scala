package sttp.tapir

import sttp.tapir.server.ServerEndpoint
import zio.{RIO, ZIO}

package object ztapir extends Tapir {
  type ZEndpoint[I, E, O] = Endpoint[I, E, O, Nothing]
  type ZServerEndpoint[R, I, E, O] = ServerEndpoint[I, E, O, Nothing, RIO[R, *]]

  implicit class RichZEndpoint[I, E, O](e: ZEndpoint[I, E, O]) {
    def zServerLogic[R](logic: I => ZIO[R, E, O]): ZServerEndpoint[R, I, E, O] = ServerEndpoint(e, _ => logic(_).either)
  }
}
