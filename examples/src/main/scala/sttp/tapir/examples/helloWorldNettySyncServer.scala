package sttp.tapir.examples

import ox.*
import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer

@main def helloWorldNettySyncServer(): Unit =
  val helloWorld = endpoint.get
    .in("hello")
    .in(query[String]("name"))
    .out(stringBody)
    .handleSuccess(name => s"Hello, $name!")

  NettySyncServer().addEndpoint(helloWorld).startAndWait()

// Alternatively, if you need manual control of the structured concurrency scope, server lifecycle,
// or just metadata from `NettySyncServerBinding` (like port number), use `start()`:
@main def helloWorldNettySyncServer2(): Unit =
  val helloWorld = endpoint.get
    .in("hello")
    .in(query[String]("name"))
    .out(stringBody)
    .handleSuccess(name => s"Hello, $name!")

  supervised {
    val serverBinding = useInScope(NettySyncServer().addEndpoint(helloWorld).start())(_.stop())
    println(s"Tapir is running on port ${serverBinding.port}")
    never
  }
