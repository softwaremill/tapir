# Running as a Play server

To expose endpoint as a [play-server](https://www.playframework.com/) first add the following dependencies:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-play-server" % "0.18.1"
```

and (if you don't already depend on Play) 

```scala
"com.typesafe.play" %% "play-akka-http-server" % "2.8.7"
```

or

```scala
"com.typesafe.play" %% "play-netty-server" % "2.8.7"
```

depending on whether you want to use netty or akka based http-server under the hood.

Then import the object:

```scala
import sttp.tapir.server.play.PlayServerInterpreter
```

This object contains the `toRoute` and `toRoutesRecoverError` methods. This first requires the
logic of the endpoint to be given as a function of type:

```scala
I => Future[Either[E, O]]
```

The second recovers errors from failed effects, and hence requires that `E` is 
a subclass of `Throwable` (an exception); it expects a function of type `I => Future[O]`. For example:

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

val countCharactersEndpoint: Endpoint[String, Unit, Int, Any] = 
  endpoint.in(stringBody).out(plainBody[Int])
val countCharactersRoutes: Routes = 
  PlayServerInterpreter().toRoutes(countCharactersEndpoint)(countCharacters _)
```

Note that the second argument to `toRoutes` is a function with one argument, a tuple of type `I`. This means that 
functions which take multiple arguments need to be converted to a function using a single argument using `.tupled`:

```scala
import sttp.tapir._
import sttp.tapir.server.play._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import akka.stream.Materializer
import play.api.routing.Router.Routes

implicit val materializer: Materializer = ???

def logic(s: String, i: Int): Future[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ???  
val aRoute: Routes = PlayServerInterpreter().toRoutes(anEndpoint)((logic _).tupled)
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

## Configuration

The interpreter can be configured by providing a `PlayServerOptions` value, see
[server options](options.md) for details.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.
