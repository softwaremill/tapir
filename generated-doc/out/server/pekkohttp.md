# Running as a pekko-http server

To expose an endpoint as a [pekko-http](https://pekko.apache.org/docs/pekko-http/current/) server, first add the following 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-pekko-http-server" % "1.11.8"
```

This will transitively pull some Pekko modules. If you want to force
your own Pekko version, use sbt exclusion. Mind the Scala version in artifact name:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-pekko-http-server" % "1.11.8" exclude("org.apache.pekko", "pekko-stream_2.12")
```

Now import the object:

```scala
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
```

## Using `toRoute`

The `toRoute` method requires a single, or a list of `ServerEndpoint`s, which can be created by adding 
[server logic](logic.md) to an endpoint.

For example:

```scala
import sttp.tapir.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import scala.concurrent.Future
import org.apache.pekko.http.scaladsl.server.Route
import scala.concurrent.ExecutionContext.Implicits.global

def countCharacters(s: String): Future[Either[Unit, Int]] = 
  Future.successful(Right[Unit, Int](s.length))

val countCharactersEndpoint: PublicEndpoint[String, Unit, Int, Any] = 
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersRoute: Route = 
  PekkoHttpServerInterpreter().toRoute(countCharactersEndpoint.serverLogic(countCharacters))
```

## Combining directives

The tapir-generated `Route` captures from the request only what is described by the endpoint. Combine
with other pekko-http directives to add additional behavior, or get more information from the request.

For example, wrap the tapir-generated route in a metrics route, or nest a security directive in the
tapir-generated directive.

Edge-case endpoints, which require special logic not expressible using tapir, can be implemented directly
using pekko-http. For example:

```scala
import sttp.tapir.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import org.apache.pekko.http.scaladsl.server.*

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Special
def metricsDirective: Directive0 = ???
def specialDirective: Directive1[Special] = ???
val tapirEndpoint: PublicEndpoint[String, Unit, Unit, Any] = endpoint.in(path[String]("input"))

val myRoute: Route = metricsDirective {
  specialDirective { special =>
    PekkoHttpServerInterpreter().toRoute(tapirEndpoint.serverLogic[Future] { input => 
      ??? 
      /* here we can use both `special` and `input` values */
    })
  }
}
```

## Streaming

The pekko-http interpreter accepts streaming bodies of type `Source[ByteString, Any]`, as described by the `PekkoStreams`
capability. Both response bodies and request bodies can be streamed. Usage: `streamBody(PekkoStreams)(schema, format)`.

The capability can be added to the classpath independently of the interpreter through the 
`"com.softwaremill.sttp.shared" %% "pekko"` dependency.

## Web sockets

The interpreter supports web sockets, with pipes of type `Flow[REQ, RESP, Any]`. See [web sockets](../endpoint/websockets.md) 
for more details.

pekko-http does not expose control frames (`Ping`, `Pong` and `Close`), so any setting regarding them are discarded, and
ping/pong frames which are sent explicitly are ignored. [Automatic pings](https://pekko.apache.org/docs/pekko-http/current/server-side/websocket-support.html#automatic-keep-alive-ping-support) can be instead enabled through configuration.

## Server Sent Events

The interpreter supports [SSE (Server Sent Events)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events). 

For example, to define an endpoint that returns event stream:

```scala
import org.apache.pekko.stream.scaladsl.Source
import sttp.model.sse.ServerSentEvent
import sttp.tapir.*
import sttp.tapir.server.pekkohttp.{PekkoHttpServerInterpreter, serverSentEventsBody}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val sseEndpoint = endpoint.get.out(serverSentEventsBody)

val routes = PekkoHttpServerInterpreter().toRoute(sseEndpoint.serverLogicSuccess[Future](_ =>
  Future.successful(Source.single(ServerSentEvent(Some("data"), None, None, None)))
))
```

## Configuration

The interpreter can be configured by providing an `PekkoHttpServerOptions` value, see
[server options](options.md) for details.
