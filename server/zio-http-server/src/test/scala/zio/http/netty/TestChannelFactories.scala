package zio.http.netty

import io.netty.channel.{ChannelFactory, ServerChannel}
import zio.ZLayer

// Note: Workaround to access package private ChannelFactories
object TestChannelFactories {
  val config: ZLayer[ChannelType.Config, Nothing, ChannelFactory[ServerChannel]] = ChannelFactories.Server.fromConfig
}
