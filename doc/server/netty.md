# Running as a Netty-based server

## Using `Future`

To expose an endpoint using a [Netty](https://netty.io)-based server, first add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "@VERSION@"
```

Then, use:

* `NettyFutureServer().addEndpoints` to expose `Future`-based server endpoints.
* `NettyCatsServer().addEndpoints` to expose `F`-based server endpoints, where `F` is any cats-effect supported effect.

These methods require a single, or a list of `ServerEndpoint`s, which can be created by adding [server logic](logic.md) 
to an endpoint.

For example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.netty.NettyServerType.TCP
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.net.InetSocketAddress

val helloWorld = endpoint
  .get
  .in("hello").in(query[String]("name"))
  .out(stringBody)
  .serverLogic(name => Future.successful[Either[Unit, String]](Right(s"Hello, $name!")))

val binding: Future[NettyFutureServerBinding[TCP]] = 
  NettyFutureServer().addEndpoint(helloWorld).start()
```

### Domain socket support
There is possibility to use Domain socket instead of TCP for handling traffic.


```scala mdoc:compile-only
import sttp.tapir.server.netty.NettyServerType.DomainSocket
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import sttp.tapir.{endpoint, query, stringBody}

import java.nio.file.Paths
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

val serverBinding: Future[NettyFutureServerBinding[DomainSocket]] =
  NettyFutureServer.domainSocket
    .path(Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString))
    .addEndpoint(
      endpoint.get.in("hello").in(query[String]("name")).out(stringBody).serverLogic(name =>
        Future.successful[Either[Unit, String]](Right(s"Hello, $name!")))
    )
  .start()

```


### Configuration

The interpreter can be configured by providing an `NettyFutureServerOptions` value, see [server options](options.md) for 
details.

Some options can be configured directly using a `NettyFutureServer` instance, such as the host and port. Others
can be passed using the `NettyFutureServer(options)` methods. Options may also be overridden when adding endpoints.

## Using cats-effect

Add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-netty-server-cats" % "@VERSION@"
```