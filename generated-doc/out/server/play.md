# Running as a Play server

To expose endpoint as a [play-server](https://www.playframework.com/) first add the following dependencies:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-play-server" % "1.2.9"
```

and (if you don't already depend on Play) 

```scala
"com.typesafe.play" %% "play-akka-http-server" % "2.8.19"
```

or

```scala
"com.typesafe.play" %% "play-netty-server" % "2.8.19"
```

depending on whether you want to use netty or akka based http-server under the hood.

Then import the object:

```scala
import sttp.tapir.server.play.PlayServerInterpreter
```

The `toRoutes` method requires a single, or a list of `ServerEndpoint`s, which can be created by adding
[server logic](logic.md) to an endpoint. For example:

```scala
import sttp.tapir._
import sttp.tapir.server.play.PlayServerInterpreter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import akka.stream.Materializer
import play.api.routing.Router.Routes

implicit val materializer: Materializer = ???

def countCharacters(s: String): Future[Either[Unit, Int]] = 
  Future(Right[Unit, Int](s.length))

val countCharactersEndpoint: PublicEndpoint[String, Unit, Int, Any] = 
  endpoint.in(stringBody).out(plainBody[Int])
val countCharactersRoutes: Routes = 
  PlayServerInterpreter().toRoutes(countCharactersEndpoint.serverLogic(countCharacters _))
```

```eval_rst
.. note::

  A single Play application can contain both tapir-managed andPlay-managed routes. However, because of the 
  routing implementation in Play, the shape of the paths that tapir/Play-native handlers serve should not 
  overlap. The shape of the path includes exact path segments, single- and multi-wildcards. Otherwise, request handling 
  will throw an exception. We don't expect users to encounter this as a problem, however the implementation here 
  diverges a bit comparing to other interpreters.
```

## Bind the routes

### Creating the HTTP server manually

An HTTP server can then be started as in the following example:

```scala
import play.core.server._
import play.api.routing.Router.Routes

val aRoute: Routes = ???

object Main {
  // JVM entry point that starts the HTTP server
  def main(args: Array[String]): Unit = {
    val playConfig = ServerConfig(port =
      sys.props.get("http.port").map(_.toInt).orElse(Some(9000))
    )
    NettyServer.fromRouterWithComponents(playConfig) { components =>
      aRoute
    }
  }
}
```

### As part of an existing Play application

Or, if you already have an existing Play application, you can create a `Router` class and bind it to the application.

First, add a line like following in the `routes` files:
```
->      /api                        api.ApiRouter
```
Then create a class like this:
```scala
class ApiRouter @Inject() () extends SimpleRouter {
  override def routes: Routes = {
    anotherRoutes.orElse(tapirGeneratedRoutes)
  }
}
```

Find more details about how to bind a `Router` to your application in the [Play framework documentation](https://www.playframework.com/documentation/2.8.x/ScalaSirdRouter#Binding-sird-Router).

## Web sockets

The interpreter supports web sockets, with pipes of type `Flow[REQ, RESP, Any]`. See [web sockets](../endpoint/websockets.md)
for more details.

The interpreter does not expose control frames (`Ping`, `Pong` and `Close`), so any setting regarding them are discarded, however those that are emitted are sent to the client.

## Configuration

The interpreter can be configured by providing a `PlayServerOptions` value, see
[server options](options.md) for details.

