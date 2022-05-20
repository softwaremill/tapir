package sttp.tapir.server.netty

sealed trait NettyServerType

object NettyServerType {
  case class TCP() extends NettyServerType
  case class DomainSocket() extends NettyServerType
}
