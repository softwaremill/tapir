package sttp.tapir.server.netty

import sttp.client3._
import sttp.model.Uri
import sttp.tapir._
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerOptions}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, blocking}
import scala.util.{Failure, Success}

object Main {

  private val helloWorldEndpoint = endpoint.get
    .in("hello")
    .out(stringBody)
    .serverLogicSuccess(_ =>
      Future {
        println("[Server] received the request.")
        blocking {
          Thread.sleep(3000)
          println("[Server] successfully processed the request.")
        }
        s"Hello World!"
      }
    )

  def main(args: Array[String]): Unit = {
    implicit val backend: SttpBackend[Future, Any] = HttpClientFutureBackend()

    // shutdown hook to print if a signal is received
    Runtime.getRuntime.addShutdownHook(new Thread((() => {
      println("[Application] shutdown signal received.")
    }): Runnable))

    // start server
    val serverOptions = NettyFutureServerOptions.default
    val bindingF = NettyFutureServer().port(8080).options(serverOptions).addEndpoint(helloWorldEndpoint).start()
    val binding = Await.result(bindingF, 10.seconds)
    println("[Server] started.")

    // call my endpoint and then kill me
    println(s"[Client] Sending request.")
    emptyRequest
      .get(Uri.parse("http://localhost:8080/hello").getOrElse(???))
      .send(backend)
      .onComplete {
        case Success(r)         => println(s"[Client] Response received: $r")
        case Failure(exception) => exception.printStackTrace()
      }
    // wait until the service receives the request
    Thread.sleep(1000L)

    // kill myself
    println(s"[Client] Stopping server.")
    Await.result(binding.stop(), 60.seconds)

    println(s"[Client] Stopped server.")
  }

}
