# Running as an http4s server using ZIO

The `tapir-zio` module defines type aliases and extension methods which make it more ergonomic to work with 
[ZIO](https://zio.dev) and tapir. Moreover, `tapir-zio-http4s-server` contains an interpreter useful when
exposing the endpoints using the [http4s](https://http4s.org) server.

You'll need the following dependency for the `ZServerEndpoint` type alias and helper classes:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio" % "@VERSION@"
```

or just add the zio-http4s integration which already depends on `tapir-zio`:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio-http4s-server" % "@VERSION@"
```

Next, instead of the usual `import sttp.tapir._`, you should import (or extend the `ZTapir` trait, see [MyTapir](../mytapir.md)):

```scala mdoc:compile-only
import sttp.tapir.ztapir._
```

This brings into scope all of the [basic](../endpoint/basics.md) input/output descriptions, which can be used to define an endpoint.

```eval_rst
.. note::

  You should have only one of these imports in your source file. Otherwise, you'll get naming conflicts. The
  ``import sttp.tapir.ztapir._`` import is meant as a complete replacement of ``import sttp.tapir._``.
```

## Server logic

When defining the business logic for an endpoint, the following methods are available, which replace the 
[standard ones](logic.md):

* `def zServerLogic[R](logic: I => ZIO[R, E, O]): ZServerEndpoint[R, C]` for public endpoints
* `def zServerSecurityLogic[R, U](f: A => ZIO[R, E, U]): ZPartialServerEndpoint[R, A, U, I, E, O, C]` for secure endpoints

The first defines complete server logic, while the second allows defining first the security server logic, and then the 
rest.

## Exposing endpoints using the http4s server

To interpret a `ZServerEndpoint` as an http4s server, use the following interpreter:

```scala mdoc:compile-only
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
```

To help with type-inference, you first need to call `ZHttp4sServerInterpreter().from()` providing:

* a single server endpoint: `def from[I, E, O, C](se: ZServerEndpoint[R, I, E, O, C])`
* multiple server endpoints: `def from[C](serverEndpoints: List[ZServerEndpoint[R, _, _, _, C]])`

Then, call `.toRoutes` to obtain the http4s `HttpRoutes` instance. 

Note that the resulting `HttpRoutes` always require `Clock` and `Blocking` in the environment.

If you have multiple endpoints with different environmental requirements, the environment must be first widened
so that it is uniform across all endpoints, using the `.widen` method:

```scala mdoc:compile-only
import org.http4s.HttpRoutes
import sttp.tapir.ztapir._
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import zio.{Has, RIO, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._

trait Component1
trait Component2
type Service1 = Has[Component1]
type Service2 = Has[Component2]

val serverEndpoint1: ZServerEndpoint[Service1, Any] = ???                              
val serverEndpoint2: ZServerEndpoint[Service2, Any] = ???

type Env = Service1 with Service2
val routes: HttpRoutes[RIO[Env with Clock with Blocking, *]] = 
  ZHttp4sServerInterpreter().from(List(
    serverEndpoint1.widen[Env], 
    serverEndpoint2.widen[Env]
  )).toRoutes // this is where zio-cats interop is needed
```

## Streaming

The http4s interpreter accepts streaming bodies of type `zio.stream.Stream[Throwable, Byte]`, as described by the `ZioStreams`
capability. Both response bodies and request bodies can be streamed. Usage: `streamBody(ZioStreams)(schema, format)`.

The capability can be added to the classpath independently of the interpreter through the
`"com.softwaremill.sttp.shared" %% "zio"` or `tapir-zio` dependency.

## Web sockets

The interpreter supports web sockets, with pipes of type `zio.stream.Stream[Throwable, REQ] => zio.stream.Stream[Throwable, RESP]`. 
See [web sockets](../endpoint/websockets.md) for more details.

## Server Sent Events

The interpreter supports [SSE (Server Sent Events)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events).

For example, to define an endpoint that returns event stream:

```scala mdoc:compile-only
import sttp.capabilities.zio.ZioStreams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.server.http4s.ztapir.{ZHttp4sServerInterpreter, serverSentEventsBody}
import sttp.tapir.PublicEndpoint
import sttp.tapir.ztapir._
import org.http4s.HttpRoutes
import org.http4s.server.websocket.WebSocketBuilder2
import zio.{UIO, RIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.stream.Stream

val sseEndpoint: PublicEndpoint[Unit, Unit, Stream[Throwable, ServerSentEvent], ZioStreams] = 
  endpoint.get.out(serverSentEventsBody)

val routes: WebSocketBuilder2[RIO[Clock with Blocking, *]] => HttpRoutes[RIO[Clock with Blocking, *]] =
  ZHttp4sServerInterpreter()
    .from(sseEndpoint.zServerLogic(_ => UIO(Stream(ServerSentEvent(Some("data"), None, None, None)))))
    .toWebSocketRoutes
```

## Examples

There's a couple of [examples](../examples.md) of using the ZIO integration available.\