# Running as a Netty-based server

To expose an endpoint using a [Netty](https://netty.io)-based server, first add the following dependency:

```scala
// if you are using Future or just exploring:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "@VERSION@"

// if you want to use Java 21 Loom virtual threads in direct style:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-loom" % "@VERSION@"

// if you are using cats-effect:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-cats" % "@VERSION@"

// if you are using zio:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-zio" % "@VERSION@"
```

Then, use:

- `NettyFutureServer().addEndpoints` to expose `Future`-based server endpoints.
- `NettySyncServer().addEndpoints` to expose `Loom`-based server endpoints.
- `NettyCatsServer().addEndpoints` to expose `F`-based server endpoints, where `F` is any cats-effect supported effect. [Streaming](../endpoint/streaming.md) request and response bodies is supported with fs2.
- `NettyZioServer().addEndpoints` to expose `ZIO`-based server endpoints, where `R` represents ZIO requirements supported effect. Streaming is supported with ZIO Streams.

These methods require a single, or a list of `ServerEndpoint`s, which can be created by adding [server logic](logic.md)
to an endpoint.

For example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

val helloWorld = endpoint
  .get
  .in("hello").in(query[String]("name"))
  .out(stringBody)
  .serverLogic(name => Future.successful[Either[Unit, String]](Right(s"Hello, $name!")))

val binding: Future[NettyFutureServerBinding] =
  NettyFutureServer().addEndpoint(helloWorld).start()
```

The `tapir-netty-server-loom` server uses `Id[T]` as its wrapper effect for compatibility, while `Id[A]` means in fact just `A`, representing direct style. It is 
available only for Scala 3.

```scala
import sttp.tapir.*
import sttp.tapir.server.netty.loom.{Id, NettySyncServer}

val helloWorld = endpoint
  .get
  .in("hello").in(query[String]("name"))
  .out(stringBody)
  .serverLogicSuccess[Id](name => s"Hello, $name!")

  NettySyncServer().addEndpoint(helloWorld).startAndWait()
```

If you need manual control of the structured concurrency scope, server lifecycle, or just metadata from `NettySyncServerBinding` (like port number), use `start()`:

```scala
import ox.*
import sttp.tapir.*
import sttp.tapir.server.netty.loom.{Id, NettySyncServer, NettySyncServerBinding}

val helloWorld = endpoint
  .get
  .in("hello").in(query[String]("name"))
  .out(stringBody)
  .serverLogicSuccess[Id](name => s"Hello, $name!")

  supervised {
    val serverBinding = useInScope(NettySyncServer().addEndpoint(helloWorld).start())(_.stop())
    println(s"Tapir is running on port ${serverBiding.port}")
    never 
  }
```

To learn more about handling concurrency with Ox, see the [documentation](https://ox.softwaremill.com/).


## Configuration

The interpreter can be configured by providing an `NettyFutureServerOptions` value, see [server options](options.md) for
details.

Some options can be configured directly using a `NettyFutureServer` instance, such as the host and port. Others
can be passed using the `NettyFutureServer(options)` methods. Options may also be overridden when adding endpoints.
For example:

```scala mdoc:compile-only
import sttp.tapir.server.netty.{NettyConfig, NettyFutureServer, NettyFutureServerOptions}
import scala.concurrent.ExecutionContext.Implicits.global

// customising the port
NettyFutureServer().port(9090).addEndpoints(???)

// customising the interceptors
NettyFutureServer(NettyFutureServerOptions.customiseInterceptors.serverLog(None).options)

// customise Netty config
NettyFutureServer(NettyConfig.default.socketBacklog(256))
```

## Web sockets


### tapir-netty-server-cats

The Cats Effects interpreter supports web sockets, with pipes of type `fs2.Pipe[F, REQ, RESP]`. See [web sockets](../endpoint/websockets.md) 
for more details.

To create a web socket endpoint, use Tapir's `out(webSocketBody)` output type:

```scala mdoc:compile-only
import cats.effect.kernel.Resource
import cats.effect.{IO, ResourceApp}
import cats.syntax.all._
import fs2.Pipe
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir.server.netty.cats.NettyCatsServer
import sttp.ws.WebSocketFrame

import scala.concurrent.duration._

object WebSocketsNettyCatsServer extends ResourceApp.Forever {

  // Web socket endpoint
  val wsEndpoint =
    endpoint.get
      .in("ws")
      .out(
        webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](Fs2Streams[IO])
          .concatenateFragmentedFrames(false) // All these options are supported by tapir-netty
          .ignorePong(true)
          .autoPongOnPing(true)
          .decodeCloseRequests(false)
          .decodeCloseResponses(false)
          .autoPing(Some((10.seconds, WebSocketFrame.Ping("ping-content".getBytes))))
      )

  // Your processor transforming a stream of requests into a stream of responses
  val pipe: Pipe[IO, String, String] = requestStream => requestStream.evalMap(str => IO.pure(str.toUpperCase))
  // Alternatively, requests can be ignored and the backend can be turned into a stream emitting frames to the client:
  // val pipe: Pipe[IO, String, String] = requestStream => someDataEmittingStream.concurrently(requestStream.as(()))

  val wsServerEndpoint = wsEndpoint.serverLogicSuccess(_ => IO.pure(pipe))

  // A regular /GET endpoint
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  val helloWorldServerEndpoint = helloWorldEndpoint
    .serverLogicSuccess(name => IO.pure(s"Hello, $name!"))

  override def run(args: List[String]) = NettyCatsServer
    .io()
    .flatMap { server =>
      Resource
        .make(
          server
            .port(8080)
            .host("localhost")
            .addEndpoints(List(wsServerEndpoint, helloWorldServerEndpoint))
            .start()
        )(_.stop())
        .as(())
    }
}
```

### tapir-netty-server-loom

In the Loom-based backend, Tapir uses [Ox](https://ox.softwaremill.com) to manage concurrency, and your transformation pipeline should be represented as `Ox ?=> Source[A] => Source[B]`. Any forks started within this function will be run under a safely isolated internal scope.

```scala
import ox.*
import ox.channels.*
import sttp.tapir.*
import sttp.tapir.server.netty.loom.Id
import sttp.tapir.server.netty.loom.OxStreams
import sttp.tapir.server.netty.loom.OxStreams.Pipe // alias for Ox ?=> Source[A] => Source[B]
import sttp.tapir.server.netty.loom.NettySyncServer
import sttp.ws.WebSocketFrame

import scala.concurrent.duration.*

object WebSocketsNettySyncServer:
  // Web socket endpoint
  val wsEndpoint =
    endpoint.get
      .in("ws") 
      .out(
        webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](OxStreams)
          .concatenateFragmentedFrames(false) // All these options are supported by tapir-netty
          .ignorePong(true)
          .autoPongOnPing(true)
          .decodeCloseRequests(false)
          .decodeCloseResponses(false)
          .autoPing(Some((10.seconds, WebSocketFrame.Ping("ping-content".getBytes))))
      )

  // Your processor transforming a stream of requests into a stream of responses
  val wsPipe: Pipe[String, String] = requestStream => requestStream.map(_.toUpperCase)
  // Alternatively, requests and responses can be treated separately, for example to emit frames to the client from another source: 
  val wsPipe2: Pipe[String, String] = { in =>
    fork {
      in.drain() // read and ignore requests
    }
    // emit periodic responses
    Source.tick(1.second).map(_ => System.currentTimeMillis()).map(_.toString)
  }

  // The WebSocket endpoint, builds the pipeline in serverLogicSuccess
  val wsServerEndpoint = wsEndpoint.serverLogicSuccess[Id](_ => wsPipe)

  // A regular /GET endpoint
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  val helloWorldServerEndpoint = helloWorldEndpoint
    .serverLogicSuccess(name => s"Hello, $name!")

  def main(args: Array[String]): Unit = 
    NettySyncServer()
      .host("0.0.0.0")
      .port(8080)
      .addEndpoints(List(wsServerEndpoint, helloWorldServerEndpoint))
      .startAndWait()
```

## Graceful shutdown

A Netty server can be gracefully closed using the function `NettyFutureServerBinding.stop()` (and analogous functions available in Cats and ZIO bindings). This function ensures that the server will wait at most 10 seconds for in-flight requests to complete, while rejecting all new requests with 503 during this period. Afterwards, it closes all server resources.
You can customize this behavior in `NettyConfig`:

```scala mdoc:compile-only
import sttp.tapir.server.netty.NettyConfig
import scala.concurrent.duration._

// adjust the waiting time to your needs
val config = NettyConfig.default.withGracefulShutdownTimeout(5.seconds)
// or if you don't want the server to wait for in-flight requests
val config2 = NettyConfig.default.noGracefulShutdown
```

## Domain socket support

There is possibility to use Domain socket instead of TCP for handling traffic.

```scala mdoc:compile-only
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureDomainSocketBinding}
import sttp.tapir.{endpoint, query, stringBody}

import java.nio.file.Paths
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.netty.channel.unix.DomainSocketAddress

val serverBinding: Future[NettyFutureDomainSocketBinding] =
  NettyFutureServer().addEndpoint(
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody).serverLogic(name =>
      Future.successful[Either[Unit, String]](Right(s"Hello, $name!")))
  )
  .startUsingDomainSocket(Paths.get(System.getProperty("java.io.tmpdir"), "hello"))
```

## Logging

By default, [logging](debugging.md) of handled requests and exceptions is enabled, and uses an slf4j logger.
