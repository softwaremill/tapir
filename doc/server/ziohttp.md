# Running as an zio-http server

To expose an endpoint as an [zio-htpp](https://github.com/dream11/zio-http) server, first add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio-http" % "@VERSION@"
```

Now import the object:

```scala mdoc:compile-only
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
```

The `ZioHttpInterpreter` objects contains methods: `toRoutes` and `toRouteRecoverErrors`.

## Using `toRoutes` and `toRouteRecoverErrors`

The `toRoutes` method requires the logic of the endpoint to be given as a function of type:

```scala
I => RIO[Either[E, O]]
```

The `toRouteRecoverErrors` method recovers errors from failed effects, and hence requires that `E` is a subclass of
`Throwable` (an exception); it expects a function of type `I => RIO[O]`.

For example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zhttp.http.{Http, Request, Response}
import zio._
import zio.blocking.Blocking

def countCharacters(s: String): RIO[Blocking,Either[Unit,Int]] =
  ZIO.succeed(Right(s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Any] =
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersRoute: Http[Blocking, Throwable, Request, Response[Blocking, Throwable]]  =
  ZioHttpInterpreter().toRoutes(countCharactersEndpoint)(countCharacters)
```

Note that the second argument to `toRoutes` is a function with one argument, a tuple of type `I`. This means that
functions which take multiple arguments need to be converted to a function using a single argument using `.tupled`:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zhttp.http.{Http, Request, Response}
import zio._
import zio.blocking.Blocking

def logic(s: String, i: Int): RIO[Blocking,Either[Unit,String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ???
val aRoute: Http[Blocking, Throwable, Request, Response[Blocking, Throwable]] = ZioHttpInterpreter().toRoutes(anEndpoint)((logic _).tupled)
```

## Streaming

The zio-http interpreter accepts streaming bodies of type `Stream[Throwable, Byte]`, as described by the `ZioStreams`
capability. Both response bodies and request bodies can be streamed. Usage: `streamBody(ZioStreams)(schema, format)`.

The capability can be added to the classpath independently of the interpreter through the
`"com.softwaremill.sttp.shared" %% "zio"` dependency.

## Configuration

The interpreter can be configured by providing an `ZioHttpServerOptions` value, see
[server options](options.md) for details.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.
