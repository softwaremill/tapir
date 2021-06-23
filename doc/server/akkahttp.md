# Running as an akka-http server

To expose an endpoint as an [akka-http](https://doc.akka.io/docs/akka-http/current/) server, first add the following 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "@VERSION@"
```

This will transitively pull some Akka modules in version 2.6. If you want to force
your own Akka version (for example 2.5), use sbt exclusion. Mind the Scala version in artifact name:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "@VERSION@" exclude("com.typesafe.akka", "akka-stream_2.12")
```

Now import the object:

```scala mdoc:compile-only
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

Note that the second argument to `toRoute` is a function with one argument, a tuple of type `I`. This means that 
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

## Combining directives

The tapir-generated `Route` captures from the request only what is described by the endpoint. Combine
with other akka-http directives to add additional behavior, or get more information from the request.

For example, wrap the tapir-generated route in a metrics route, or nest a security directive in the
tapir-generated directive.

Edge-case endpoints, which require special logic not expressible using tapir, can be implemented directly
using akka-http. For example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import akka.http.scaladsl.server._

case class User(email: String)
def metricsDirective: Directive0 = ???
def securityDirective: Directive1[User] = ???
val tapirEndpoint: Endpoint[String, Unit, Unit, Any] = endpoint.in(path[String]("input"))

val myRoute: Route = metricsDirective {
  securityDirective { user =>
    AkkaHttpServerInterpreter().toRoute(tapirEndpoint) { input => 
      ??? 
      /* here we can use both `user` and `input` values */
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

## Server Sent Events

The interpreter supports [SSE (Server Sent Events)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events). 

For example, to define an endpoint that returns event stream:

```scala mdoc:compile-only
import akka.stream.scaladsl.Source
import sttp.model.sse.ServerSentEvent
import sttp.tapir._
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, serverSentEventsBody}

import scala.concurrent.Future

val sseEndpoint = endpoint.get.out(serverSentEventsBody)

val routes = AkkaHttpServerInterpreter().toRoute(sseEndpoint)(_ =>
  Future.successful(Right(Source.single(ServerSentEvent(Some("data"), None, None, None))))
)
```

## Configuration

The interpreter can be configured by providing an `AkkaHttpServerOptions` value, see
[server options](options.md) for details.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.
