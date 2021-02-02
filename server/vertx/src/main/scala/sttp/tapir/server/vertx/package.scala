package sttp.tapir.server

import sttp.tapir.server.vertx.interpreters.VertxCatsServerInterpreter
import sttp.tapir.server.vertx.interpreters.VertxFutureServerInterpreter
import sttp.tapir.server.vertx.interpreters.VertxZioServerInterpreter

package object vertx {

  object VertxZioServerInterpreter extends VertxZioServerInterpreter

  object VertxCatsServerInterpreter extends VertxCatsServerInterpreter

  object VertxFutureServerInterpreter extends VertxFutureServerInterpreter
}
