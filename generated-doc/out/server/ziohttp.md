# Running as an zio-http server

To expose an endpoint as a [zio-http](https://github.com/dream11/zio-http) server, first add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio-http" % "0.18.1"
```

Now import the object:

```scala
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
```

The `ZioHttpInterpreter` objects contains methods: `toHttp` and `toHttpRecoverErrors`.

The `toHttp` method requires the logic of the endpoint to be given as a function of type:

```scala
I => RIO[R, Either[E, O]]
```

The `toHttpRecoverErrors` method recovers errors from failed effects, and hence requires that `E` is a subclass of
`Throwable` (an exception); it expects a function of type `I => RIO[R, O]`.

For example:

```scala
import sttp.tapir._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zhttp.http.{Http, Request, Response}
import zio._

def countCharacters(s: String): RIO[Any, Either[Unit, Int]] =
  ZIO.succeed(Right(s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Any] =
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersHttp: Http[Any, Throwable, Request, Response[Any, Throwable]]  =
  ZioHttpInterpreter().toHttp(countCharactersEndpoint)(countCharacters)
```

Note that the second argument to `toHttp` is a function with one argument, a tuple of type `I`. This means that
functions which take multiple arguments need to be converted to a function using a single argument using `.tupled`:

```scala
import sttp.tapir._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zhttp.http.{Http, Request, Response}
import zio._

def logic(s: String, i: Int): RIO[Any, Either[Unit,String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ???
val anHttp: Http[Any, Throwable, Request, Response[Any, Throwable]] = 
    ZioHttpInterpreter().toHttp(anEndpoint)((logic _).tupled)
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
