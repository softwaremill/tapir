// {cat=Hello, World!; effects=Direct; server=Netty}: Exposing an endpoint using the Netty server (Direct-style variant)

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.1
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.1
//> using dep com.softwaremill.sttp.client3::core:3.9.7

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
