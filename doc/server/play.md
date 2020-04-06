# Running as a Play server

To expose endpoint as a [play-server](https://www.playframework.com/) first add the following dependencies:
```scala
"com.softwaremill.sttp.tapir" %% "tapir-play-server" % "0.13.1"
```
and 
```scala
"com.typesafe.play" %% "play-akka-http-server" % "2.8.1"
```

or

```scala
"com.typesafe.play" %% "play-netty-http-server" % "2.8.1"
```

depending on whether you want to use netty or akka based http-server under the hood.


Then import the package:

```scala
import sttp.tapir.server.play._
```

This adds two extension methods to the `Endpoint` type: `toRoutes` and `toRoutesRecoverErrors`. This first requires the 
logic of the endpoint to be given as a function of type:

```scala
I => Future[Either[E, O]]
```

The second recovers errors from failed effects, and hence requires that `E` is 
a subclass of `Throwable` (an exception); it expects a function of type `I => Future[O]`. For example:

```scala
import sttp.tapir._
import sttp.tapir.server.play._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.routing.Router.Routes

def countCharacters(s: String): Future[Either[Unit, Int]] = 
  Future(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Nothing] = 
  endpoint.in(stringBody).out(plainBody[Int])
val countCharactersRoutes: Routes = 
  countCharactersEndpoint.toRoutes(countCharacters _)
```

Note that these functions take one argument, which is a tuple of type `I`. This means that functions which take multiple 
arguments need to be converted to a function using a single argument using `.tupled`:

```scala
def logic(s: String, i: Int): Future[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Nothing] = ??? 
val aRoute: Routes = anEndpoint.toRoute((logic _).tupled)
```

In practice, the routes are put in a class taking an `endpoints.play.server.PlayComponents` parameter. 
An HTTP server can then be started as in the following example:

```scala
object Main {
  // JVM entry point that starts the HTTP server
  def main(args: Array[String]): Unit = {
    val playConfig = ServerConfig(port =
      sys.props.get("http.port").map(_.toInt).orElse(Some(9000))
    )
    NettyServer.fromRouterWithComponents(playConfig) { components =>
      val playComponents = PlayComponents.fromBuiltInComponents(components)
      new CounterServer(playComponents).routes orElse new DocumentationServer(
        playComponents
      ).routes
    }
  }
}
```

## Configuration

The interpreter can be configured by providing an implicit `PlayServerOptions` value and status mappers, see
[server options](options.html) for details.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.html) for details.
