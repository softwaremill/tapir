package zio

import io.netty.channel._
import zio.http._
import zio.http.netty.server.NettyDriver

package object test {
  // NettyDriver is private[zio] so we need to fake it (will be solved in future zio-http versions)
  val driver: ZLayer[EventLoopGroup & ChannelFactory[ServerChannel] & ServerConfig, Nothing, Driver] =
    NettyDriver.manual
}
