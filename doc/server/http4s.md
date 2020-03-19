# Running as an http4s server

To expose an endpoint as an [http4s](https://http4s.org) server, first add the following 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "0.12.25"
```

and import the package:

```scala
import sttp.tapir.server.http4s._
```

This adds two extension methods to the `Endpoint` type: `toRoutes` and `toRoutesRecoverErrors`. This first requires the 
logic of the endpoint to be given as a function of type:

```scala
I => F[Either[E, O]]
```

where `F[_]` is the chosen effect type. The second recovers errors from failed effects, and hence requires that `E` is 
a subclass of `Throwable` (an exception); it expects a function of type `I => F[O]`. For example:

```scala
import sttp.tapir._
import sttp.tapir.server.http4s._
import cats.effect.IO
import org.http4s.HttpRoutes
import cats.effect.ContextShift

// will probably come from somewhere else
implicit val cs: ContextShift[IO] = 
  IO.contextShift(scala.concurrent.ExecutionContext.global)

def countCharacters(s: String): IO[Either[Unit, Int]] = 
  IO.pure(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Nothing] = 
  endpoint.in(stringBody).out(plainBody[Int])
val countCharactersRoutes: HttpRoutes[IO] = 
  countCharactersEndpoint.toRoutes(countCharacters _)
```

Note that these functions take one argument, which is a tuple of type `I`. This means that functions which take multiple 
arguments need to be converted to a function using a single argument using `.tupled`:

```scala
def logic(s: String, i: Int): IO[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Nothing] = ??? 
val aRoute: Route = anEndpoint.toRoute((logic _).tupled)
```

The created `HttpRoutes` are the usual http4s `Kleisli`-based transformation of a `Request` to a `Response`, and can 
be further composed using http4s middlewares or request-transforming functions. The tapir-generated `HttpRoutes`
captures from the request only what is described by the endpoint.

It's completely feasible that some part of the input is read using a http4s wrapper function, which is then composed
with the tapir endpoint descriptions. Moreover, "edge-case endpoints", which require some special logic not expressible 
using tapir, can be always implemented directly using http4s.

## Streaming

The http4s interpreter accepts streaming bodies of type `Stream[F, Byte]`, which can be used both for sending
response bodies and reading request bodies. Usage: `streamBody[Stream[F, Byte]](schema, mediaType)`.

## Configuration

The interpreter can be configured by providing an implicit `Http4sServerOptions` value and status mappers, see
[server options](options.html) for details.

The http4s options also includes configuration for the blocking execution context to use, and the io chunk size.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.html) for details.
