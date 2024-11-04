# Running as a zio-http server

The `tapir-zio` module defines type aliases and extension methods which make it more ergonomic to work with
[ZIO](https://zio.dev) and tapir. Moreover, `tapir-zio-http-server` contains an interpreter useful when
exposing the endpoints using the [ZIO Http](https://github.com/dream11/zio-http) server.

The `*-zio` modules depend on ZIO 2.x.
You'll need the following dependency for the `ZServerEndpoint` type alias and helper classes:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio" % "1.11.8"
```

or just add the zio-http integration which already depends on `tapir-zio`:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio-http-server" % "1.11.8"
```

Next, instead of the usual `import sttp.tapir.*`, you should import (or extend the `ZTapir` trait, see [MyTapir](../other/mytapir.md)):

```scala
import sttp.tapir.ztapir.*
```

This brings into scope all the [basic](../endpoint/basics.md) input/output descriptions, which can be used to define an endpoint.

```{note}
You should have only one of these imports in your source file. Otherwise, you'll get naming conflicts. The
`import sttp.tapir.ztapir.*` import is meant as a complete replacement of `import sttp.tapir.*`.
```

## Exposing endpoints

```scala
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
```

The `ZioHttpInterpreter` objects contains the `toHttp` method, which requires a `ZServerEndpoint` (see below). For 
example:

```scala
import sttp.tapir.PublicEndpoint
import sttp.tapir.ztapir.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.http.{Request, Response, Routes}
import zio.*

def countCharacters(s: String): ZIO[Any, Nothing, Int] =
  ZIO.succeed(s.length)

val countCharactersEndpoint: PublicEndpoint[String, Unit, Int, Any] =
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersHttp: Routes[Any, Response] =
  ZioHttpInterpreter().toHttp(countCharactersEndpoint.zServerLogic(countCharacters))
```

```{note}
A single ZIO Http application can contain both Tapir-generated and ZIO-Http-native routes. However, because of the
routing implementation in ZIO Http, the shape of the paths that Tapir and other ZIO Http routes serve should not
overlap. The shape of the path includes exact path segments, single- and multi-wildcards. 

Such overlapping routes may cause incorrect 404 (Not Found) or 405 (Method Not Allowed) responses.
```

## Server logic

When defining the business logic for an endpoint, the following methods are available, which replace the
[standard ones](logic.md):

* `def zServerLogic[R](logic: I => ZIO[R, E, O]): ZServerEndpoint[R, C]` for public endpoints
* `def zServerSecurityLogic[R, U](f: A => ZIO[R, E, U]): ZPartialServerEndpoint[R, A, U, I, E, O, C]` for secure endpoints

The first defines complete server logic, while the second and third allow defining server logic in parts.

```{note}
When using Scala 3, it's best to provide the type of the environment explicitly to avoid type inferencing issues.
E.g.: `myEndpoint.zServerLogic[Any](...)`.
```

## Streaming

The zio-http interpreter accepts streaming bodies of type `Stream[Throwable, Byte]`, as described by the `ZioStreams`
capability. Both response bodies and request bodies can be streamed. Usage: `streamBody(ZioStreams)(schema, format)`.

The capability can be added to the classpath independently of the interpreter through the
`"com.softwaremill.sttp.shared" %% "zio"` dependency.

## Web sockets

The interpreter supports web sockets, with pipes of type `zio.stream.Stream[Throwable, REQ] => zio.stream.Stream[Throwable, RESP]`.
See [web sockets](../endpoint/websockets.md) for more details. It also supports auto-ping, auto-pong-on-ping, ignoring-pongs and handling 
of fragmented frames.

## Error handling

By default, any endpoints interpreted with the `ZioHttpInterpreter` will use tapir's built-in failed effect handling, 
which uses an interceptor. Errors can be sent in a custom format by [providing a custom `ErrorHandler`](errors.md).

If you'd prefer to use zio-http's error handling, you can disable tapir's exception interceptor by modifying the
[server options](options.md):

```scala
import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}

ZioHttpInterpreter(ZioHttpServerOptions.customiseInterceptors[Any].exceptionHandler(None).options)
```

## Configuration

The interpreter can be configured by providing an `ZioHttpServerOptions` value, see
[server options](options.md) for details.
