package zio

import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver
import zio.http.netty.server.ServerEventLoopGroups
import io.netty.channel.ChannelFactory
import io.netty.channel.ServerChannel

package object test {
  val driver: ZLayer[ServerEventLoopGroups & ChannelFactory[ServerChannel] & Server.Config, Nothing, Driver] =
    ZLayer.makeSome[ServerEventLoopGroups & ChannelFactory[ServerChannel] & Server.Config, Driver](
      ZLayer.succeed(NettyConfig.default),
      NettyDriver.manual
    )
}
