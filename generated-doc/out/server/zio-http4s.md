# Running as an http4s server using ZIO

The `tapir-zio` module defines type aliases and extension methods which make it more ergonomic to work with 
[ZIO](https://zio.dev) and tapir. Moreover, `tapir-zio-http4s-server` contains an interpreter useful when
exposing the endpoints using the [http4s](https://http4s.org) server.

The `*-zio` modules depend on ZIO 2.x.
You'll need the following dependency for the `ZServerEndpoint` type alias and helper classes:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio" % "1.11.8"
```

or just add the zio-http4s integration which already depends on `tapir-zio`:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-http4s-server-zio" % "1.11.8"
```

Next, instead of the usual `import sttp.tapir.*`, you should import (or extend the `ZTapir` trait, see [MyTapir](../other/mytapir.md)):

```scala
import sttp.tapir.ztapir.*
```

This brings into scope all of the [basic](../endpoint/basics.md) input/output descriptions, which can be used to define an endpoint.

```{note}
You should have only one of these imports in your source file. Otherwise, you'll get naming conflicts. The
`import sttp.tapir.ztapir.*` import is meant as a complete replacement of `import sttp.tapir.*`.
```

## Server logic

When defining the business logic for an endpoint, the following methods are available, which replace the 
[standard ones](logic.md):

* `def zServerLogic[R](logic: I => ZIO[R, E, O]): ZServerEndpoint[R, C]` for public endpoints
* `def zServerSecurityLogic[R, U](f: A => ZIO[R, E, U]): ZPartialServerEndpoint[R, A, U, I, E, O, C]` for secure endpoints

The first defines complete server logic, while the second allows defining first the security server logic, and then the 
rest.

```{note}
When using Scala 3, it's best to provide the type of the environment explicitly to avoid type inferencing issues.
E.g.: `myEndpoint.zServerLogic[Any](...)`.
```

## Exposing endpoints using the http4s server

To interpret a `ZServerEndpoint` as an http4s server, use the following interpreter:

```scala
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
```

To help with type-inference, you first need to call `ZHttp4sServerInterpreter().from()` providing:

* a single server endpoint: `def from[I, E, O, C](se: ZServerEndpoint[R, I, E, O, C])`
* multiple server endpoints: `def from[C](serverEndpoints: List[ZServerEndpoint[R, _, _, _, C]])`

Then, call `.toRoutes` to obtain the http4s `HttpRoutes` instance. 

Note that the resulting `HttpRoutes` always requires `Clock` in the environment.

If you have multiple endpoints with different environmental requirements, the environment must be first widened
so that it is uniform across all endpoints, using the `.widen` method:

```scala
import org.http4s.HttpRoutes
import sttp.tapir.ztapir.*
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import zio.RIO

trait Component1
trait Component2
type Service1 = Component1
type Service2 = Component2

val serverEndpoint1: ZServerEndpoint[Service1, Any] = ???                              
val serverEndpoint2: ZServerEndpoint[Service2, Any] = ???

type Env = Service1 with Service2
val routes: HttpRoutes[RIO[Env, *]] =
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

## Http4s backends

Http4s integrates with a couple of [server backends](https://http4s.org/v1.0/integrations/), the most popular being
Blaze and Ember. In the [examples](../examples.md) and throughout the docs we use Blaze, but other backends can be used
as well. This means adding another dependency, such as:

```scala
"org.http4s" %% "http4s-blaze-server" % Http4sVersion
```

## Web sockets

The interpreter supports web sockets, with pipes of type `zio.stream.Stream[Throwable, REQ] => zio.stream.Stream[Throwable, RESP]`. 
See [web sockets](../endpoint/websockets.md) for more details.

However, endpoints which use web sockets need to be interpreted using the `ZHttp4sServerInterpreter.fromWebSocket`
method. This can then be added to a server builder using `withHttpWebSocketApp`, for example:

```scala
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.{CodecFormat, PublicEndpoint}
import sttp.tapir.ztapir.*
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.websocket.WebSocketBuilder2
import scala.concurrent.ExecutionContext
import zio.{Task, Runtime, ZIO}
import zio.interop.catz.*
import zio.stream.Stream

def runtime: Runtime[Any] = ??? // provided by ZIOAppDefault

given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

val wsEndpoint: PublicEndpoint[Unit, Unit, Stream[Throwable, String] => Stream[Throwable, String], ZioStreams with WebSockets] =
  endpoint.get.in("count").out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](ZioStreams))

val wsRoutes: WebSocketBuilder2[Task] => HttpRoutes[Task] =
  ZHttp4sServerInterpreter().fromWebSocket(wsEndpoint.zServerLogic(_ => ???)).toRoutes

val serve: Task[Unit] =
  ZIO.executor.flatMap(executor =>
    BlazeServerBuilder[Task]
      .withExecutionContext(executor.asExecutionContext)
      .bindHttp(8080, "localhost")
      .withHttpWebSocketApp(wsb => Router("/" -> wsRoutes(wsb)).orNotFound)
      .serve
      .compile
      .drain
  )
```

## Server Sent Events

The interpreter supports [SSE (Server Sent Events)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events).

For example, to define an endpoint that returns event stream:

```scala
import sttp.capabilities.zio.ZioStreams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.server.http4s.ztapir.{ZHttp4sServerInterpreter, serverSentEventsBody}
import sttp.tapir.PublicEndpoint
import sttp.tapir.ztapir.*
import org.http4s.HttpRoutes
import zio.{Task, ZIO}
import zio.stream.{Stream, ZStream}

val sseEndpoint: PublicEndpoint[Unit, Unit, Stream[Throwable, ServerSentEvent], ZioStreams] =
  endpoint.get.out(serverSentEventsBody)

val routes: HttpRoutes[Task] =
  ZHttp4sServerInterpreter()
    .from(sseEndpoint.zServerLogic(_ => ZIO.succeed(ZStream(ServerSentEvent(Some("data"), None, None, None)))))
    .toRoutes
```

## Examples

There's a couple of [examples](../examples.md) of using the ZIO integration available.\
