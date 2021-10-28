# Running as a Netty-based server

*Warning! This is an early development module, with important features missing.*

To expose an endpoint using a [Netty](https://netty.io)-based server, first add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "@VERSION@"
```

Then, use:

* `NettyFutureServer().addEndpoints` to expose `Future`-based server endpoints.
* `NettyCatsServer().addEndpoints` to expose `F`-based server endpoints, where `F` is any cats-effect supported effect.

For example:

```scala mdoc:compile-only
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

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.
