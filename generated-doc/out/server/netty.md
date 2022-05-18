# Running as a Netty-based server

*Warning! This is an early development module, with important features missing.*

To expose an endpoint using a [Netty](https://netty.io)-based server, first add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "0.20.2"
```

Then, use:

* `NettyFutureServer().addEndpoints` to expose `Future`-based server endpoints.
* `NettyCatsServer().addEndpoints` to expose `F`-based server endpoints, where `F` is any cats-effect supported effect.

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

## Configuration

The interpreter can be configured by providing an `NettyFutureServerOptions` value, see [server options](options.md) for 
details.

Some options can be configured directly using a `NettyFutureServer` instance, such as the host and port. Others
can be passed using the `NettyFutureServer(options)` methods. Options may also be overridden when adding endpoints.

