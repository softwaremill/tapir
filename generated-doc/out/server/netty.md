# Running as a Netty-based server

To expose an endpoint using a [Netty](https://netty.io)-based server, first add the following dependency:

```scala
// if you are using Future or just exploring
"com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "1.2.9"

// if you are using cats-effect:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-cats" % "1.2.9"

// if you are using zio:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-zio" % "1.2.9"

// if you are using zio1:
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-zio1" % "1.2.9"
```

Then, use:

* `NettyFutureServer().addEndpoints` to expose `Future`-based server endpoints.
* `NettyCatsServer().addEndpoints` to expose `F`-based server endpoints, where `F` is any cats-effect supported effect.
* `NettyZioServer().addEndpoints` to expose `ZIO`-based server endpoints, where `R` represents ZIO requirements supported effect.

These methods require a single, or a list of `ServerEndpoint`s, which can be created by adding [server logic](logic.md) 
to an endpoint.

For example:

```scala
import sttp.tapir._
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.net.InetSocketAddress

val helloWorld = endpoint
  .get
  .in("hello").in(query[String]("name"))
  .out(stringBody)
  .serverLogic(name => Future.successful[Either[Unit, String]](Right(s"Hello, $name!")))

val binding: Future[NettyFutureServerBinding[InetSocketAddress]] = 
  NettyFutureServer().addEndpoint(helloWorld).start()
```

## Configuration

The interpreter can be configured by providing an `NettyFutureServerOptions` value, see [server options](options.md) for
details.

Some options can be configured directly using a `NettyFutureServer` instance, such as the host and port. Others
can be passed using the `NettyFutureServer(options)` methods. Options may also be overridden when adding endpoints.
For example:

```scala
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerOptions}
import scala.concurrent.ExecutionContext.Implicits.global

// customising the port
NettyFutureServer().port(9090).addEndpoints(???)

// customising the interceptors
NettyFutureServer(NettyFutureServerOptions.customiseInterceptors.serverLog(None).options)

// customise Netty options
NettyFutureServer(NettyFutureServerOptions.default.nettyOptions(???))
```

## Domain socket support

There is possibility to use Domain socket instead of TCP for handling traffic.

```scala
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding, NettyFutureServerOptions, NettyOptions}
import sttp.tapir.{endpoint, query, stringBody}

import java.nio.file.Paths
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.netty.channel.unix.DomainSocketAddress

val serverBinding: Future[NettyFutureServerBinding[DomainSocketAddress]] =
  NettyFutureServer(
    NettyFutureServerOptions.default.nettyOptions(
        NettyOptions.defaultDomainSocket.domainSocketPath(
          Paths.get(System.getProperty("java.io.tmpdir"), "hello")
        )
    )    
  )
  .addEndpoint(
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody).serverLogic(name =>
      Future.successful[Either[Unit, String]](Right(s"Hello, $name!")))
  )
  .start()
```