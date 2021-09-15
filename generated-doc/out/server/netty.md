# Running as a Netty-based server

*Warning! This is an early development module, with important features missing.*

To expose an endpoint using a [Netty](https://netty.io)-based server, first add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "0.19.0-M9"
```

Then, use `NettyServer().addEndpoints` to expose `Future`-based server endpoints.

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

val binding: Future[NettyFutureServerBinding] = NettyFutureServer().addEndpoint(helloWorld).start()
```

## Configuration

The interpreter can be configured by providing an `NettyServerOptions` value, see [server options](options.md) for 
details.

Some of the options can be configured directly using a `NettyServer` instance, such as the host and port. Others
can be passed using the `NettyServer(options)` methods. Options may also be overriden when adding endpoints.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.
