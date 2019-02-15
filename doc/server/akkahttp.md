# Running as an akka-http server

To expose an endpoint as an [akka-http](https://doc.akka.io/docs/akka-http/current/) server, first add the following 
dependency:

```scala
"com.softwaremill.tapir" %% "tapir-akka-http-server" % "0.0.11"
```

and import the package:

```scala
import tapir.server.akkahttp._
```

This adds two extension methods to the `Endpoint` type: `toDirective` and `toRoute`. Both require the logic of the 
endpoint to be given as a function of type:

```scala
[I as function arguments] => Future[Either[E, O]]
```

Note that the function doesn't take the tuple `I` directly as input, but instead this is converted to a function of the 
appropriate arity. For example:

```scala
import tapir._
import tapir.server.akkahttp._
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

def countCharacters(s: String): Future[Either[Unit, Int]] = 
  Future.successful(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Nothing] = 
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersRoute: Route = countCharactersEndpoint.toRoute(countCharacters _)
```

The created `Route`/`Directive` can then be further combined with other akka-http directives, for example nest within
other routes. The Tapir-generated `Route`/`Directive` captures from the request only what is described by the endpoint.

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
[common server configuration](common.html) for details.
