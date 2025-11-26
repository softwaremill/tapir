package sttp.tapir.server.netty

import ox.*
import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer
import scala.sys.process.*

@main def test() = {
  supervised {
    val _ = fork {
      NettySyncServer()
        .addEndpoint(
          endpoint.get.in("hello").out(stringBody).handleSuccess(_ => "ok")
        )
        .startAndWait()
    }

    // Give the server time to start
    Thread.sleep(1000)

    // Run curl and print the result
    val result = "curl http://localhost:8080".!!
    println(s"Curl result: $result")
  }
}
