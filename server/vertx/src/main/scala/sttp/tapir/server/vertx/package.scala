package sttp.tapir.server

import sttp.tapir.server.vertx.interpreters.{VertxCatsServerInterpreter, VertxFutureServerInterpreter, VertxZioServerInterpreter}

package object vertx {

  object VertxZioServerInterpreter extends VertxZioServerInterpreter

  object VertxCatsServerInterpreter extends VertxCatsServerInterpreter

  object VertxFutureServerInterpreter extends VertxFutureServerInterpreter
}
