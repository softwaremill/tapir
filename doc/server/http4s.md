# Running as an http4s server

To expose an endpoint as an [http4s](https://http4s.org) server, first add the following 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "@VERSION@"
```

and import the object:

```scala mdoc:compile-only
import sttp.tapir.server.http4s.Http4sServerInterpreter
```

This objects contains the `toRoutes` and `toRoutesRecoverErrors` methods. This first requires the 
logic of the endpoint to be given as a function of type:

```scala
I => F[Either[E, O]]
```

where `F[_]` is the chosen effect type. The second recovers errors from failed effects, and hence requires that `E` is 
a subclass of `Throwable` (an exception); it expects a function of type `I => F[O]`. For example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import cats.effect.IO
import org.http4s.HttpRoutes

def countCharacters(s: String): IO[Either[Unit, Int]] = 
  IO.pure(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Any] = 
  endpoint.in(stringBody).out(plainBody[Int])
val countCharactersRoutes: HttpRoutes[IO] = 
  Http4sServerInterpreter[IO]().toRoutes(countCharactersEndpoint)(countCharacters _)
```

Note that the second argument to `toRoute` is a function with one argument, a tuple of type `I`. This means that 
functions which take multiple arguments need to be converted to a function using a single argument using `.tupled`:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import cats.effect.IO
import org.http4s.HttpRoutes

def logic(s: String, i: Int): IO[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ??? 
val routes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(anEndpoint)((logic _).tupled)
```

The created `HttpRoutes` are the usual http4s `Kleisli`-based transformation of a `Request` to a `Response`, and can 
be further composed using http4s middlewares or request-transforming functions. The tapir-generated `HttpRoutes`
captures from the request only what is described by the endpoint.

It's completely feasible that some part of the input is read using a http4s wrapper function, which is then composed
with the tapir endpoint descriptions. Moreover, "edge-case endpoints", which require some special logic not expressible 
using tapir, can be always implemented directly using http4s.

## Streaming

The http4s interpreter accepts streaming bodies of type `Stream[F, Byte]`, as described by the `Fs2Streams`
capability. Both response bodies and request bodies can be streamed. Usage: `streamBody(Fs2Streams[F])(schema, format)`.

The capability can be added to the classpath independently of the interpreter through the 
`"com.softwaremill.sttp.shared" %% "http4s"` dependency.

## Web sockets

The interpreter supports web sockets, with pipes of type `Pipe[F, REQ, RESP]`. See [web sockets](../endpoint/websockets.md) 
for more details.

## Server Sent Events

The interpreter supports [SSE (Server Sent Events)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events).

For example, to define an endpoint that returns event stream:

```scala mdoc:compile-only
import cats.effect.IO
import sttp.model.sse.ServerSentEvent
import sttp.tapir._
import sttp.tapir.server.http4s.{Http4sServerInterpreter, serverSentEventsBody}

val sseEndpoint = endpoint.get.out(serverSentEventsBody[IO])

val routes = Http4sServerInterpreter[IO]().toRoutes(sseEndpoint)(_ =>
  IO(Right(fs2.Stream(ServerSentEvent(Some("data"), None, None, None))))
)
```

## Configuration

The interpreter can be configured by providing an `Http4sServerOptions` value, see
[server options](options.md) for details.

The http4s options also includes configuration for the blocking execution context to use, and the io chunk size.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.
