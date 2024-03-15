# Running as a Netty-based server

To expose an endpoint using a [Netty](https://netty.io)-based server, first add the following dependency:

```scala
// if you are using Future or just exploring:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "1.10.0"

// if you want to use Java 21 Loom virtual threads in direct style:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-loom" % "1.10.0"

// if you are using cats-effect:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-cats" % "1.10.0"

// if you are using zio:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-zio" % "1.10.0"
```

Then, use:

- `NettyFutureServer().addEndpoints` to expose `Future`-based server endpoints.
- `NettyIdServer().addEndpoints` to expose `Loom`-based server endpoints.
- `NettyCatsServer().addEndpoints` to expose `F`-based server endpoints, where `F` is any cats-effect supported effect. [Streaming](../endpoint/streaming.md) request and response bodies is supported with fs2.
- `NettyZioServer().addEndpoints` to expose `ZIO`-based server endpoints, where `R` represents ZIO requirements supported effect. Streaming is supported with ZIO Streams.

These methods require a single, or a list of `ServerEndpoint`s, which can be created by adding [server logic](logic.md)
to an endpoint.

For example:

```scala
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

The `tapir-netty-server-loom` server uses `Id[T]` as its wrapper effect for compatibility, while `Id[A]` means in fact just `A`, representing direct style.

```scala
import sttp.tapir._
import sttp.tapir.server.netty.loom.{Id, NettyIdServer, NettyIdServerBinding}

val helloWorld = endpoint
  .get
  .in("hello").in(query[String]("name"))
  .out(stringBody)
  .serverLogicSuccess[Id](name => s"Hello, $name!")

val binding: NettyIdServerBinding =
  NettyIdServer().addEndpoint(helloWorld).start()
```

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

## Graceful shutdown

A Netty should can be gracefully closed using function `NettyFutureServerBinding.stop()` (and analogous functions available in Cats and ZIO bindings). This function ensures that the server will wait at most 10 seconds for in-flight requests to complete, while rejecting all new requests with 503 during this period. Afterwards, it closes all server resources.
You can customize this behavior in `NettyConfig`:

```scala
import sttp.tapir.server.netty.NettyConfig
import scala.concurrent.duration._

// adjust the waiting time to your needs
val config = NettyConfig.default.withGracefulShutdownTimeout(5.seconds)
// or if you don't want the server to wait for in-flight requests
val config2 = NettyConfig.default.noGracefulShutdown
```

## Domain socket support

There is possibility to use Domain socket instead of TCP for handling traffic.

```scala
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
