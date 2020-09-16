# Running as an akka-http server

To expose an endpoint as an [akka-http](https://doc.akka.io/docs/akka-http/current/) server, first add the following 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "@VERSION@"
```

This will transitively pull some Akka modules in version 2.6. If you want to force
your own Akka version (for example 2.5), use sbt exclusion.  Mind the Scala version in artifact name:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "@VERSION@" exclude("com.typesafe.akka", "akka-stream_2.12")
```

Now import the package:

```scala
import sttp.tapir.server.akkahttp._
```

This adds extension methods to the `Endpoint` type: `toRoute`, `toRouteRecoverErrors` and `toDirective`.

## using `toRoute` and `toRouteRecoverErrors`

Method `toRoute` requires the logic of the endpoint to be given as a function of type:

```scala
I => Future[Either[E, O]]
```

Method `toRouteRecoverErrors` recovers errors from failed futures, and hence requires that `E` is a subclass of
`Throwable` (an exception); it expects a function of type `I => Future[O]`.

For example:

```scala mdoc:compile-only
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

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.akkahttp._
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

def logic(s: String, i: Int): Future[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Nothing] = ??? 
val aRoute: Route = anEndpoint.toRoute((logic _).tupled)
```

## using `toDirective`

Method `toDirective` splits parsing the input and encoding the output. The directive provides the
input parameters, type `I`, and a function that can be used to encode the output.

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
  
val countCharactersRoute: Route = countCharactersEndpoint.toDirective { (input, completion) =>
  completion(countCharacters(input))
}
```

## Combining directives

The tapir-generated `Route`/`Directive` captures from the request only what is described by the endpoint. Combine
with other akka-http directives to add additional behavior, or get more information from the request.

For example, wrap the tapir-generated route in a metrics route, or nest a security directive in the
tapir-generated directive.

Edge-case endpoints, which require special logic not expressible using tapir, can be implemented directly
using akka-http. For example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.akkahttp._
import akka.http.scaladsl.server._

case class User(email: String)
def metricsDirective: Directive0 = ???
def securityDirective: Directive1[User] = ???
val tapirEndpoint: Endpoint[String, Unit, Unit, Nothing] = endpoint.in(path[String]("input"))

val myRoute: Route = metricsDirective {
  securityDirective { user =>
    tapirEndpoint.toRoute(input => ??? /* here we can use both `user` and `input` values */)
  }
}
```

Note that `Route`s can only be nested within other directives. `Directive`s can nest in other directives
and can also contain nested directives. For example:

```scala
val countCharactersRoute: Route =
  authenticateBasic("realm", authenticator) {
    countCharactersEndpoint.toDirective { (input, completion) =>
      authorizeUserFor(input) {
        completion(countCharacters(input))
      }
    }
  }
```

## Streaming

The akka-http interpreter accepts streaming bodies of type `Source[ByteString, Any]`, which can be used both for sending
response bodies and reading request bodies. Usage: `streamBody[Source[ByteString, Any]](schema, mediaType)`.

## Configuration

The interpreter can be configured by providing an implicit `AkkaHttpServerOptions` value and status mappers, see
[server options](options.md) for details.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.
