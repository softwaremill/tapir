# Running as an http4s server using ZIO

The `tapir-zio` module defines type aliases and extension methods which make it more ergonomic to work with 
[ZIO](https://zio.dev) and tapir. Moreover, the `tapir-zio-http4s-server` contains an interpreter useful when
exposing the endpoints using the [http4s](https://http4s.org) server.

You'll need the following dependency for the `ZEndpoint`, `ZServerEndpoint` type aliases and helper classes:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio" % "0.19.0-M8"
```

or just add the zio-http4s integration which already depends on `tapir-zio`:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio-http4s-server" % "0.19.0-M8"
```

Next, instead of the usual `import sttp.tapir._`, you should import (or extend the `ZTapir` trait, see [MyTapir](../mytapir.md)):

```scala
import sttp.tapir.ztapir._
```

This brings into scope all of the [basic](../endpoint/basics.md) input/output descriptions, which can be used to define an endpoint. 
Additionally, it defines the `ZEndpoint` type alias, which should be used instead of `Endpoint`.

```eval_rst
.. note::

  You should have only one of these imports in your source file. Otherwise, you'll get naming conflicts. The
  ``import sttp.tapir.ztapir._`` import is meant as a complete replacement of ``import sttp.tapir._``.
```

## Server logic

When defining the business logic for an endpoint, the following methods are available, which replace the 
[standard ones](logic.md):

* `def zServerLogic(logic: I => ZIO[R, E, O]): ZServerEndpoint[R, I, E, O]`
* `def zServerLogicPart(logicPart: T => ZIO[R, E, U])`
* `def zServerLogicForCurrent(logicPart: I => ZIO[R, E, U])`

The first defines complete server logic, while the second and third allow defining server logic in parts.

## Exposing endpoints using the http4s server

To interpret a `ZServerEndpoint` as a http4s server, use the following interpreter:

```scala
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
```

To help with type-inference, you first need to call `ZHttp4sServerInterpreter().from()` providing:

* an endpoint and logic: `def from[I, E, O](e: ZEndpoint[I, E, O])(logic: I => ZIO[R, E, O])`
* a single server endpoint: `def from[I, E, O](se: ZServerEndpoint[R, I, E, O])`
* multiple server endpoints: `def from(serverEndpoints: List[ZServerEndpoint[R, _, _, _]])`

Then, call `.toRoutes` to obtain the http4s `HttpRoutes` instance. 

Note that the resulting `HttpRoutes` always require `Clock` and `Blocking` in the environment.

If you have multiple endpoints with different environmental requirements, the environment must be first widened
so that it is uniform across all endpoints, using the `.widen` method:

```scala
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

val serverEndpoint1: ZServerEndpoint[Service1, Unit, Unit, Unit] = ???                                                            
val serverEndpoint2: ZServerEndpoint[Service2, Unit, Unit, Unit] = ???

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

```scala
import sttp.model.sse.ServerSentEvent
import sttp.tapir.server.http4s.ztapir.{ZHttp4sServerInterpreter, serverSentEventsBody}
import sttp.tapir.ztapir._
import org.http4s.HttpRoutes
import zio.{UIO, RIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.stream.Stream

val sseEndpoint: ZEndpoint[Unit, Unit, Stream[Throwable, ServerSentEvent]] = endpoint.get.out(serverSentEventsBody)

val routes: HttpRoutes[RIO[Clock with Blocking, *]] = ZHttp4sServerInterpreter()
  .from(sseEndpoint.zServerLogic(_ => UIO(Stream(ServerSentEvent(Some("data"), None, None, None)))))
  .toRoutes
```

## Examples

Three examples of using the ZIO integration are available. The first two showcase basic functionality, while the third shows how to use partial server logic methods:

* [ZIO basic example](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/ZioExampleHttp4sServer.scala)
* [ZIO environment example](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/ZioEnvExampleHttp4sServer.scala)
* [ZIO partial server logic example](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/ZioPartialServerLogicHttp4s.scala)