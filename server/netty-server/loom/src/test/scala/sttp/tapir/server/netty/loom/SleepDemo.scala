package sttp.tapir.server.netty.loom

import sttp.tapir._

object SleepDemo extends App {
  val e = endpoint.get.in("hello").out(stringBody).serverLogicSuccess[Id] { _ =>
    Thread.sleep(1000)
    "Hello, world!"
  }
  NettyIdServer().addEndpoint(e).start()
}
