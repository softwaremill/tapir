# Running as an akka-http server

To expose an endpoint as an [akka-http](https://doc.akka.io/docs/akka-http/current/) server, first add the following 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "0.17.12"
```

This will transitively pull some Akka modules in version 2.6. If you want to force
your own Akka version (for example 2.5), use sbt exclusion. Mind the Scala version in artifact name:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "0.17.12" exclude("com.typesafe.akka", "akka-stream_2.12")
```

Now import the object:

```scala
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
```

The `AkkaHttpServerInterpreter` objects contains methods such as: `toRoute`, `toRouteRecoverErrors` and `toDirective`.

## Using `toRoute` and `toRouteRecoverErrors`

The `toRoute` method requires the logic of the endpoint to be given as a function of type:

```scala
I => Future[Either[E, O]]
```

The `toRouteRecoverErrors` method recovers errors from failed futures, and hence requires that `E` is a subclass of
`Throwable` (an exception); it expects a function of type `I => Future[O]`.

For example:

```scala
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

def countCharacters(s: String): Future[Either[Unit, Int]] = 
  Future.successful(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Any] = 
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersRoute: Route = AkkaHttpServerInterpreter.toRoute(countCharactersEndpoint)(countCharacters)
```

Note that the second argument to `toRoute` is a function with one argument, a tuple of type `I`. This means that 
functions which take multiple arguments need to be converted to a function using a single argument using `.tupled`:

```scala
import sttp.tapir._
import sttp.tapir.server.akkahttp._
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

def logic(s: String, i: Int): Future[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ???  
val aRoute: Route = AkkaHttpServerInterpreter.toRoute(anEndpoint)((logic _).tupled)
```

## Using `toDirective`

The `toDirective` method splits parsing the input and encoding the output. The directive provides the
input parameters, type `I`, and a function that can be used to encode the output.

For example:

```scala
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

def countCharacters(s: String): Future[Either[Unit, Int]] = 
  Future.successful(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Any] = 
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersRoute: Route = AkkaHttpServerInterpreter.toDirective(countCharactersEndpoint).tapply { 
  case (input, completion) => completion(countCharacters(input))
}
```

## Combining directives

The tapir-generated `Route`/`Directive` captures from the request only what is described by the endpoint. Combine
with other akka-http directives to add additional behavior, or get more information from the request.

For example, wrap the tapir-generated route in a metrics route, or nest a security directive in the
tapir-generated directive.

Edge-case endpoints, which require special logic not expressible using tapir, can be implemented directly
using akka-http. For example:

```scala
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import akka.http.scaladsl.server._

case class User(email: String)
def metricsDirective: Directive0 = ???
def securityDirective: Directive1[User] = ???
val tapirEndpoint: Endpoint[String, Unit, Unit, Any] = endpoint.in(path[String]("input"))

val myRoute: Route = metricsDirective {
  securityDirective { user =>
    AkkaHttpServerInterpreter.toRoute(tapirEndpoint) { input => 
      ??? 
      /* here we can use both `user` and `input` values */
    }
  }
}
```

Note that `Route`s can only be nested within other directives. `Directive`s can nest in other directives
and can also contain nested directives. For example:

```scala
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives.Authenticator
import akka.http.scaladsl.server.Directives.authenticateBasic
import akka.http.scaladsl.server.Route
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.Future

def countCharacters(s: String): Future[Either[Unit, Int]] =
  Future.successful(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Any] =
  endpoint.in(stringBody).out(plainBody[Int])

case class User(email: String)
val authenticator: Authenticator[User] = ???
def authorizationDirective(user: User, input: String): Directive0 = ???

val countCharactersRoute: Route =
  authenticateBasic("realm", authenticator) { user =>
    AkkaHttpServerInterpreter.toDirective(countCharactersEndpoint).tapply { 
      case (input, completion) =>
        authorizationDirective(user, input) {
          completion(countCharacters(input))
        }
    }
  }
```

## Streaming

The akka-http interpreter accepts streaming bodies of type `Source[ByteString, Any]`, as described by the `AkkaStreams`
capability. Both response bodies and request bodies can be streamed. Usage: `streamBody(AkkaStreams)(schema, format)`.

The capability can be added to the classpath independently of the interpreter through the 
`"com.softwaremill.sttp.shared" %% "akka"` dependency.

## Web sockets

The interpreter supports web sockets, with pipes of type `Flow[REQ, RESP, Any]`. See [web sockets](../endpoint/websockets.md) 
for more details.

akka-http does not expose control frames (`Ping`, `Pong` and `Close`), so any setting regarding them are discarded, and
ping/pong frames which are sent explicitly are ignored. [Automatic pings](https://doc.akka.io/docs/akka-http/current/server-side/websocket-support.html#automatic-keep-alive-ping-support) 
can be instead enabled through configuration.

## Configuration

The interpreter can be configured by providing an implicit `AkkaHttpServerOptions` value and status mappers, see
[server options](options.md) for details.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.
