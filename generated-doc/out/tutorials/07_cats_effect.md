# 7. Integration with cats-effect & http4s

```{note}
The tutorial is also available [as a video](https://www.youtube.com/watch?v=M6ZHXM8_kaU).
```

[cats-effect](https://github.com/typelevel/cats-effect) is one of the most popular functional effect systems for Scala 
(probably also one of the top ones when it comes to pure functional programming in general). So far we've used Tapir in 
combination with so-called "direct style", where the server logic is expressed using synchronous, blocking code.

However, one of Tapir's main strengths is that it integrates with virtually every Scala stack out there. This includes,
first and foremost, cats-effect. Let's see how we can combine the two libraries together.

## Describing an endpoint

We'll assume that you are familiar with what's described in the previous tutorials, especially [](01_hello_world.md).
The good news is that most of what's described there applies 1-1 to our scenario, when we want to use cats-effect. The
process of describing endpoints is identical, regardless of what programming style, Scala stack of effect library you
use.

Hence, we'll start with the same basic endpoint description. We'll be editing the `cats-effect.scala` file:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8

import sttp.tapir.*

@main def helloWorldTapir(): Unit =
  val helloWorldEndpoint = endpoint
    .get
    .in("hello" / "world")
    .in(query[String]("name"))
    .out(stringBody)
```

```{note}
As a side note, while our previous examples required Java 21+, as they leveraged virtual threads under the hood, the 
cats-effect version will work with Java 11+.
```

## Server-side logic

The crucial difference when using Tapir+cats-effect, as compared to the "direct" version is in the way the server
logic is provided. The server logic does, most probably, involve side effects. Typically, this might be querying the 
database, writing to Kafka, or invoking other endpoints (though in our example, here we'll constrain ourselves to good 
old `println`s). Hence, any operations that the server logic performs should be captured using the `IO` monad. 

That is, given an endpoint with inputs of type `I` and error/success outputs of type `E` and `O`, the shape of the 
server logic function should be `I => IO[Either[E, O]]`. In other words: given the input parameters `I`, extracted from 
the request, the server logic should return a description of a computation, yielding either error `E` or success  `O` 
outputs, which will then be mapped to the HTTP response.

To combine an endpoint description with the server logic, we need to use the `.serverLogic` method. While not always 
required, as the type parameter is usually inferred correctly, it's nevertheless beneficial to provide the effect type 
parameter explicitly, using `.serverLogic[IO]` in our case:

{emphasize-lines="2, 4, 12-14"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-cats:1.11.8

import cats.effect.IO
import sttp.tapir.*

@main def helloWorldTapir(): Unit =
  val helloWorldEndpoint = endpoint.get
    .in("hello" / "world")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogic[IO](name => IO
      .println(s"Saying hello to: $name")
      .flatMap(_ => IO.pure(Right(s"Hello, $name!"))))
```

The server-side logic consists of printing a message to the server's logs (`IO.println(...)`), followed by returning
a pure value - successful result. The `s"Hello, $name"` string that will be mapped to the response needs to be first
wrapped with a `Right` (as we want to use the successful outputs), and then lifted to an `IO` computation description
using `IO.pure`. That way, we obtain a value of type `IO[Either[Unit, String]]`, as required by the endpoint 
description.

## http4s integration

So far we've described the shape of the endpoint, and coupled it with a function implementing the server logic, with
a matching signature. What we still need to do is to expose the endpoint using a server.

The server must be "cats-effect-aware" - that is, it must need to know how to deal with server-side logic, which is 
expressed in terms of `IO` computation descriptions. So far we've been using the `NettySyncServer`, however here it 
won't be useful. Attempting to use it with our endpoint description won't compile, as there would be a mismatch on the 
type used to represent effects (`Identity` vs `IO`).

Instead, we need to use a different server. Tapir provides a couple of choices (which might be useful depending on what
you're already using in your project), but the most popular option in case of cats-effect is the 
[http4s](https://http4s.org) server. That's what we're going to do as well: through a Tapir-http4s integration, called
a server interpreter.

We've already introduced interpreters in the tutorial [](02_openapi_docs.md). In this particular case, the http4s 
server interpreter will convert our endpoint description+server logic into a `HttpRoutes[IO]` type. This type is
the representation of HTTP routes that is understandable by the http4s API, and which can be used to expose a server
to the outside world.

The conversion process is an almost-one-liner (if it wasn't for line length limit!):

{emphasize-lines="2, 5, 7, 18-19"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.8

import cats.effect.IO
import org.http4s.HttpRoutes
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

@main def helloWorldTapir(): Unit =
  val helloWorldEndpoint = endpoint.get
    .in("hello" / "world")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogic[IO](name => IO
      .println(s"Saying hello to: $name")
      .flatMap(_ => IO.pure(Right(s"Hello, $name!"))))

  val helloWorldRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]()
    .toRoutes(helloWorldEndpoint)
```

## Exposing the server

As a final step, we need to expose the routes to the outside world. If you've ever used http4s, the following is fairly
standard code to start a server and handle requests until the application is interrupted or killed:

{emphasize-lines="3, 5, 7, 8, 12, 24-30"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.8
//> using dep org.http4s::http4s-blaze-server:0.23.16

import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

object HelloWorldTapir extends IOApp:
  val helloWorldEndpoint = endpoint.get
    .in("hello" / "world")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogic[IO](name => IO
      .println(s"Saying hello to: $name")
      .flatMap(_ => IO.pure(Right(s"Hello, $name!"))))

  val helloWorldRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]()
    .toRoutes(helloWorldEndpoint)

  override def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> helloWorldRoutes).orNotFound)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
```

First of all, you might notice that instead of the `@main` method, we are extending the `IOApp` trait. This is needed,
because not only our endpoint's server logic is expressed using `IO`, but the entire process of starting the server
and handling requests is described as an `IO` computation. Hence, we need to start the application in an `IO`-aware way:
the `IOApp` will handle evaluating the `IO` description and actually running the code.

Secondly, with http4s we need to use a specific server implementation (http4s itself is only an API to define endpoints - 
kind of a middle-man between Tapir and low-level networking code). We can choose from `blaze` and `ember` servers, here 
we're using the `blaze` one, which is reflected in the additional dependency and the server configuration constructor: 
`BlazeServerBuilder`.

Finally, we've got the `run` method implementation, which attaches our interpreted route to the root context `/` and
exposes the server on `localhost:8080`.

```{note}
Note that you could also attach other, non-Tapir-managed routes to the same http4s application. Tapir-interpreted
`HttpRoutes[IO]` can co-exist with routes defined in any other way.
```

## Adding documentation

As a final touch, let's expose documentation using the Swagger UI, just as we did before using the Netty server.
The base process is the same: we first need to call the `SwaggerInterpreter` providing the list of endpoints, for which
documentation should be generated. However, this time we'll provide the `IO` type constructor as the type parameter.

That way, the server logic implementing the behavior of the swagger endpoints (such as reading the .js/.css/.html
resources) will be expressed in terms of `IO`, and we'll be able to convert them later to http4s routes. And that's
the second step that we need to perform:

{emphasize-lines="3, 7, 13, 27-32, 37"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8
//> using dep org.http4s::http4s-blaze-server:0.23.16

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

object HelloWorldTapir extends IOApp:
  val helloWorldEndpoint = endpoint.get
    .in("hello" / "world")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogic[IO](name => IO
      .println(s"Saying hello to: $name")
      .flatMap(_ => IO.pure(Right(s"Hello, $name!"))))

  val helloWorldRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]()
    .toRoutes(helloWorldEndpoint)

  val swaggerEndpoints = SwaggerInterpreter()
    .fromServerEndpoints[IO](List(helloWorldEndpoint), "My App", "1.0")

  val swaggerRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(swaggerEndpoints)

  val allRoutes: HttpRoutes[IO] = helloWorldRoutes <+> swaggerRoutes

  override def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> allRoutes).orNotFound)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
```

Hence, we first generate endpoint descriptions, which correspond to exposing the Swagger UI (containing the generated
OpenAPI yaml for our `/hello/world` endpoint), which use `IO` to express their server logic. Then, we interpret those
endpoints as `HttpRoutes[IO]`, which we can expose using http4's blaze server.

## Other concepts covered so far

We can use JSON integration, error outputs, status codes, and any other Tapir features in the same way as we did so 
far with the "synchronous" server! The endpoints are described in the same way, the only thing that changes is how the 
server logic is provided.

## Further reading

* [Netty-cats interpreter](../server/netty.md)
* [Armeria-cats interpreter](../server/armeria.md)
* [Integration with cats datatypes](../endpoint/customtypes.md)
