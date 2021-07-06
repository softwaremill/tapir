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
I =>
```

The `toRouteRecoverErrors` method recovers errors from failed futures, and hence requires that `E` is a subclass of
`Throwable` (an exception); it expects a function of type `I => Future[O]`.

For example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

def countCharacters(s: String): Future[Either[Unit, Int]] = 
  Future.successful(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Any] = 
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersRoute: Route = 
  AkkaHttpServerInterpreter().toRoute(countCharactersEndpoint)(countCharacters)
```

Note that the second argument to `toRoutes` is a function with one argument, a tuple of type `I`. This means that
functions which take multiple arguments need to be converted to a function using a single argument using `.tupled`:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.akkahttp._
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

def logic(s: String, i: Int): Future[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ??? 
val aRoute: Route = AkkaHttpServerInterpreter().toRoute(anEndpoint)((logic _).tupled)
```

## Streaming

The zio-http interpreter accepts streaming bodies of type `Stream[Throwable, Byte]`, as described by the `ZioStreams`
capability. Both response bodies and request bodies can be streamed. Usage: `streamBody(ZioStreams)(schema, format)`.

The capability can be added to the classpath independently of the interpreter through the
`"com.softwaremill.sttp.shared" %% "akka"` dependency.

## Configuration

The interpreter can be configured by providing an `ZioHttpServerOptions` value, see
[server options](options.md) for details.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.
