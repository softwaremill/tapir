# Running as an akka-http server

To expose an endpoint as an [akka-http](https://doc.akka.io/docs/akka-http/current/) server, first add the following 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "0.12.25"
```

This will transitively pull some Akka modules in version 2.6. If you want to force
your own Akka version (for example 2.5), use sbt exclusion.  Mind the Scala version in artifact name:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "0.12.25" exclude("com.typesafe.akka", "akka-stream_2.12")
```


Now import the package:

```scala
import sttp.tapir.server.akkahttp._
```

This adds extension methods to the `Endpoint` type: `toDirective`, `toRoute` and `toRouteRecoverErrors`. The first two
require the logic of the endpoint to be given as a function of type:

```scala
I => Future[Either[E, O]]
```

The third recovers errors from failed futures, and hence requires that `E` is a subclass of `Throwable` (an exception);
it expects a function of type `I => Future[O]`.

For example:

```scala
import sttp.tapir._
import sttp.tapir.server.akkahttp._
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

def countCharacters(s: String): Future[Either[Unit, Int]] = 
  Future.successful(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Nothing] = 
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersRoute: Route = countCharactersEndpoint.toRoute(countCharacters)
```

Note that these functions take one argument, which is a tuple of type `I`. This means that functions which take multiple 
arguments need to be converted to a function using a single argument using `.tupled`:

```scala
def logic(s: String, i: Int): Future[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Nothing] = ??? 
val aRoute: Route = anEndpoint.toRoute((logic _).tupled)
```

The created `Route`/`Directive` can then be further combined with other akka-http directives, for example nested within
other routes. The tapir-generated `Route`/`Directive` captures from the request only what is described by the endpoint.

It's completely feasible that some part of the input is read using akka-http directives, and the rest 
using tapir endpoint descriptions; or, that the tapir-generated route is wrapped in e.g. a metrics route. Moreover, 
"edge-case endpoints", which require some special logic not expressible using tapir, can be always implemented directly 
using akka-http. For example:

```scala
val myRoute: Route = metricsDirective {
  securityDirective { user =>
    tapirEndpoint.toRoute(input => /* here we can use both `user` and `input` values */)
  }
}
```

## Streaming

The akka-http interpreter accepts streaming bodies of type `Source[ByteString, Any]`, which can be used both for sending
response bodies and reading request bodies. Usage: `streamBody[Source[ByteString, Any]](schema, mediaType)`.

## Configuration

The interpreter can be configured by providing an implicit `AkkaHttpServerOptions` value and status mappers, see
[server options](options.html) for details.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.html) for details.
