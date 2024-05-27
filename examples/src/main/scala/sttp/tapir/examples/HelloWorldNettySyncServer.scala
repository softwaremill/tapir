package sttp.tapir.examples

import ox.*
import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer

object HelloWorldNettySyncServer:
  val helloWorld = endpoint.get
    .in("hello")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogicSuccess[Id](name => s"Hello, $name!")

  NettySyncServer().addEndpoint(helloWorld).startAndWait()

// Alternatively, if you need manual control of the structured concurrency scope, server lifecycle,
// or just metadata from `NettySyncServerBinding` (like port number), use `start()`:
object HelloWorldNettySyncServer2:
  val helloWorld = endpoint.get
    .in("hello")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogicSuccess[Id](name => s"Hello, $name!")

  supervised {
    val serverBinding = useInScope(NettySyncServer().addEndpoint(helloWorld).start())(_.stop())
    println(s"Tapir is running on port ${serverBinding.port}")
    never
  }
