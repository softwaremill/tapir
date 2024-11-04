# Running as a Netty-based server

To expose an endpoint using a [Netty](https://netty.io)-based server, first add the following dependency:

```scala
// if you are using Future or just exploring:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "1.11.8"

// if you want to use Java 21 Loom virtual threads in direct style:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % "1.11.8"

// if you are using cats-effect:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-cats" % "1.11.8"

// if you are using zio:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-zio" % "1.11.8"
```

Then, use:

- `NettyFutureServer().addEndpoints` to expose `Future`-based server endpoints.
- `NettySyncServer().addEndpoints` to expose `Loom`-based server endpoints.
- `NettyCatsServer().addEndpoints` to expose `F`-based server endpoints, where `F` is any cats-effect supported effect. [Streaming](../endpoint/streaming.md) request and response bodies is supported with fs2.
- `NettyZioServer().addEndpoints` to expose `ZIO`-based server endpoints, where `R` represents ZIO requirements supported effect. Streaming is supported with ZIO Streams.

These methods require a single, or a list of `ServerEndpoint`s, which can be created by adding [server logic](logic.md)
to an endpoint.

For example:

```scala
import sttp.tapir.*
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

## Direct-style

The `tapir-netty-server-sync` server uses `Identity[T]` as its wrapper effect for compatibility, while `Id[A]` means in 
fact just `A`, representing direct style. It is available only for Scala 3.

See [examples/HelloWorldNettySyncServer.scala](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/HelloWorldNettySyncServer.scala) for a full example.

To provide server logic for an endpoint when using the `-sync` server, you can use the dedicated `handle`
methods, and its variants. This provides better type inference.

To learn more about handling concurrency with Ox, see the [documentation](https://ox.softwaremill.com/).

## Configuration

The interpreter can be configured by providing an `NettyFutureServerOptions` value, see [server options](options.md) for
details.

Some options can be configured directly using a `NettyFutureServer` instance, such as the host and port. Others
can be passed using the `NettyFutureServer(options)` methods. Options may also be overridden when adding endpoints.
For example:

```scala
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

```scala
import cats.effect.kernel.Resource
import cats.effect.{IO, ResourceApp}
import cats.syntax.all.*
import fs2.Pipe
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*
import sttp.tapir.server.netty.cats.NettyCatsServer
import sttp.ws.WebSocketFrame

import scala.concurrent.duration.*

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

### tapir-netty-server-sync

In the Loom-based backend, Tapir uses [Ox](https://ox.softwaremill.com) to manage concurrency, and your transformation pipeline should be represented as `Ox ?=> Source[A] => Source[B]`. Any forks started within this function will be run under a safely isolated internal scope.
See [examples/websocket/WebSocketNettySyncServer.scala](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/websocket/WebSocketNettySyncServer.scala) for a full example.

```{note}
The pipeline transform a source of incoming web socket messages (received from the client), into a source of outgoing web socket messages (which will be sent to the client), within some concurrency scope. Once the incoming source is done, the client has closed the connection. In that case, remember to close the outgoing source as well: otherwise the scope will leak and won't be closed. An error will be logged if the outgoing channel is not closed within a timeout after a close frame is received.
```

## Graceful shutdown

A Netty server can be gracefully closed using the function `NettyFutureServerBinding.stop()` (and analogous functions available in Cats and ZIO bindings). This function ensures that the server will wait at most 10 seconds for in-flight requests to complete, while rejecting all new requests with 503 during this period. Afterwards, it closes all server resources.
You can customize this behavior in `NettyConfig`:

```scala
import sttp.tapir.server.netty.NettyConfig
import scala.concurrent.duration.*

// adjust the waiting time to your needs
val config = NettyConfig.default.withGracefulShutdownTimeout(5.seconds)
// or if you don't want the server to wait for in-flight requests
val config2 = NettyConfig.default.noGracefulShutdown
```

## Domain socket support

There is possibility to use Domain socket instead of TCP for handling traffic.

```scala
import sttp.tapir.*
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureDomainSocketBinding}

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
