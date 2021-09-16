# Running as a zio-http server

The `tapir-zio` module defines type aliases and extension methods which make it more ergonomic to work with
[ZIO](https://zio.dev) and tapir. Moreover, `tapir-zio-http-server` contains an interpreter useful when
exposing the endpoints using the [ZIO Http](https://github.com/dream11/zio-http) server.

You'll need the following dependency for the `ZServerEndpoint` type alias and helper classes:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio" % "@VERSION@"
```

or just add the zio-http integration which already depends on `tapir-zio`:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio-http" % "@VERSION@"
```

Next, instead of the usual `import sttp.tapir._`, you should import (or extend the `ZTapir` trait, see [MyTapir](../mytapir.md)):

```scala mdoc:compile-only
import sttp.tapir.ztapir._
```

This brings into scope all of the [basic](../endpoint/basics.md) input/output descriptions, which can be used to define an endpoint.
Additionally, it defines the `ZEndpoint` type alias, which should be used instead of `Endpoint`.

```eval_rst
.. note::

  You should have only one of these imports in your source file. Otherwise, you'll get naming conflicts. The
  ``import sttp.tapir.ztapir._`` import is meant as a complete replacement of ``import sttp.tapir._``.
```

## Exposing endpoints

```scala mdoc:compile-only
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
```

The `ZioHttpInterpreter` objects contains methods: `toHttp` and `toHttpRecoverErrors`.

The `toHttp` method requires a `ZServerEndpoint` (see below), or that the logic of the endpoint is given as a function 
of type:

```scala
I => ZIO[R, E, O]
```

For example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zhttp.http.{Http, Request, Response}
import zio._

def countCharacters(s: String): ZIO[Any, Nothing, Int] =
  ZIO.succeed(s.length)

val countCharactersEndpoint: Endpoint[String, Unit, Int, Any] =
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersHttp: Http[Any, Throwable, Request, Response[Any, Throwable]]  =
  ZioHttpInterpreter().toHttp(countCharactersEndpoint)(countCharacters)
```

Note that the second argument to `toHttp` is a function with one argument, a tuple of type `I`. This means that
functions which take multiple arguments need to be converted to a function using a single argument using `.tupled`:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zhttp.http.{Http, Request, Response}
import zio._

def logic(s: String, i: Int): ZIO[Any, Nothing, String] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ???
val anHttp: Http[Any, Throwable, Request, Response[Any, Throwable]] = 
    ZioHttpInterpreter().toHttp(anEndpoint)((logic _).tupled)
```

## Server logic

When defining the business logic for an endpoint, the following methods are available, which replace the
[standard ones](logic.md):

* `def zServerLogic(logic: I => ZIO[R, E, O]): ZServerEndpoint[R, I, E, O, C]`
* `def zServerLogicPart(logicPart: T => ZIO[R, E, U])`
* `def zServerLogicForCurrent(logicPart: I => ZIO[R, E, U])`

The first defines complete server logic, while the second and third allow defining server logic in parts.

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
