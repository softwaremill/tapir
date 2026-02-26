package sttp.tapir.server.netty

/** Configuration for HTTP response compression in Netty server.
  *
  * @param enabled
  *   Whether compression is enabled. When enabled, the server will compress responses based on the client's Accept-Encoding header.
  *   Netty uses a default compression level of 6 (balanced between speed and compression ratio).
  */
case class NettyCompressionConfig(
    enabled: Boolean = false
) {
  def withEnabled(value: Boolean): NettyCompressionConfig = copy(enabled = value)
}

object NettyCompressionConfig {
  /** Compression disabled (default) */
  val default: NettyCompressionConfig = NettyCompressionConfig()

  /** Compression enabled with Netty's default settings */
  val enabled: NettyCompressionConfig = NettyCompressionConfig(enabled = true)
}

