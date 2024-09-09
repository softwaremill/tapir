package zio

import io.netty.channel.{ChannelFactory, EventLoopGroup, ServerChannel}
import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver

package object test {
  val driver: ZLayer[EventLoopGroup & ChannelFactory[ServerChannel] & Server.Config, Nothing, Driver] =
    ZLayer.makeSome[EventLoopGroup & ChannelFactory[ServerChannel] & Server.Config, Driver](
      ZLayer.succeed(NettyConfig.default),
      NettyDriver.manual
    )

  // work-around for Scala 2.12
  val manual =  NettyDriver.manual
}
