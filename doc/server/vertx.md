# Running as a Vert.X server

Endpoints can be mounted as Vert.x `Route`s on top of a Vert.x `Router`.

Use the following dependency
```scala
"com.softwaremill.sttp.tapir" %% "tapir-vertx-server" % "@VERSION@"
```

Then import the object:

```scala mdoc:compile-only
import sttp.tapir.server.vertx.VertxServerInterpreter
```

This object contains the following methods: 

* `route(e: Endpoint[I, E, O, Any], logic: I => Future[Either[E, O]])`: returns a function `Router => Route` that will create a route matching the endpoint definition, and attach your `logic` as an handler. Errors will be recovered automatically (but generically)
* `routeRecoverErrors(e: Endpoint[I, E, O, Any], logic: I => Future[O])`: returns a function `Router => Route` that will create a route matching the endpoint definition, and attach your `logic` as an handler. You're providing your own way to deal with errors happening in the `logic` function.
* `blockingRoute(e: Endpoint[I, E, O, Any], logic: I => Future[Either[E, O]])`: returns a function `Router => Route` that will create a route matching the endpoint definition, and attach your `logic` as an blocking handler. Errors will be recovered automatically (but generically)
* `blockingRouteRecoverErrors(e: Endpoint[I, E, O, Any], logic: I => Future[O])`: returns a function `Router => Route` that will create a route matching the endpoint definition, and attach your `logic` as a blocking handler. You're providing your own way to deal with errors happening in the `logic` function.

The methods recovering errors from failed effects, require `E` to be a subclass of `Throwable` (an exception); and expect a function of type `I => Future[O]`. For example:

Note that the second argument to `route` etc. is a function with one argument, a tuple of type `I`. This means that 
functions which take multiple arguments need to be converted to a function using a single argument using `.tupled`:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.vertx.{ VertxServerInterpreter, VertxEndpointOptions }
import io.vertx.scala.ext.web._
import scala.concurrent.Future

implicit val options: VertxEndpointOptions = ???
def logic(s: String, i: Int): Future[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ??? 
val aRoute: Router => Route = VertxServerInterpreter.route(anEndpoint)((logic _).tupled)
```

In practice, routes will be mounted on a router, this router can then be used as a request handler for your http server. 
An HTTP server can then be started as in the following example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.vertx.{ VertxServerInterpreter, VertxEndpointOptions }
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web._
import scala.concurrent.Future

object Main {
  // JVM entry point that starts the HTTP server
  def main(args: Array[String]): Unit = {
    implicit val options: VertxEndpointOptions = ???
    val vertx = Vertx.vertx()
    val server = vertx.createHttpServer()
    val router = Router.router(vertx)
    val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ??? // your definition here
    def logic(s: String, i: Int): Future[Either[Unit, String]] = ??? // your logic here 
    val attach = VertxServerInterpreter.route(anEndpoint)((logic _).tupled)
    attach(router) // your endpoint is now attached to the router, and the route has been created
    server.requestHandler(router).listenFuture(9000)
  }
}
```

## Configuration

Every endpoint can be configured by providing an implicit `VertxEndpointOptions`, see [server options](options.md) for details.
You can also provide your own `ExecutionContext` to execute the logic.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.
