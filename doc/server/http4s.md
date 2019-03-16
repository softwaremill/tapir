# Running as an http4s server

To expose an endpoint as an [http4s](https://http4s.org) server, first add the following 
dependency:

```scala
"com.softwaremill.tapir" %% "tapir-akka-http4s" % "0.3"
```

and import the package:

```scala
import tapir.server.http4s._
```

This adds an extension method to the `Endpoint` type: `toRoutes`. It requires the logic of the endpoint to be given as 
a function of type:

```scala
[I as function arguments] => F[Either[E, O]]
```

where `F[_]` is the chosen effect type. Note that the function doesn't take the tuple `I` directly as input, but instead 
this is converted to a function of the appropriate arity. For example:

```scala
import tapir._
import tapir.server.http4s._
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
[common server configuration](common.html) for details.

The http4s options also includes configuration for the blocking execution context to use, and the io chunk size.