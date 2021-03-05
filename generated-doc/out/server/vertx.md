# Running as a Vert.X server

Endpoints can be mounted as Vert.x `Route`s on top of a Vert.x `Router`.

Vert.x interpreter can be used with different effect systems (cats-effect, ZIO) as well as Scala's standard `Future`.

## Scala's standard `Future`

Add the following dependency
```scala
"com.softwaremill.sttp.tapir" %% "tapir-vertx-server" % "0.17.14"
```
to use this interpreter with `Future`.

Then import the object:
```scala
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._
```

This object contains the following methods:

* `route(e: Endpoint[I, E, O, Any])(logic: I => Future[Either[E, O]])`: returns a function `Router => Route` that will create a route matching the endpoint definition, and attach your `logic` as an handler. Errors will be recovered automatically (but generically)
* `routeRecoverErrors(e: Endpoint[I, E, O, Any])(logic: I => Future[O])`: returns a function `Router => Route` that will create a route matching the endpoint definition, and attach your `logic` as an handler. You're providing your own way to deal with errors happening in the `logic` function.
* `blockingRoute(e: Endpoint[I, E, O, Any])(logic: I => Future[Either[E, O]])`: returns a function `Router => Route` that will create a route matching the endpoint definition, and attach your `logic` as an blocking handler. Errors will be recovered automatically (but generically)
* `blockingRouteRecoverErrors(e: Endpoint[I, E, O, Any])(logic: I => Future[O])`: returns a function `Router => Route` that will create a route matching the endpoint definition, and attach your `logic` as a blocking handler. You're providing your own way to deal with errors happening in the `logic` function.

The methods recovering errors from failed effects, require `E` to be a subclass of `Throwable` (an exception); and expect a function of type `I => Future[O]`.

Note that the second argument to `route` etc. is a function with one argument, a tuple of type `I`. This means that 
functions which take multiple arguments need to be converted to a function using a single argument using `.tupled`:

```scala
import sttp.tapir._
import sttp.tapir.server.vertx.{ VertxFutureEndpointOptions, VertxFutureServerInterpreter }
import io.vertx.ext.web._
import scala.concurrent.Future

implicit val options: VertxFutureEndpointOptions = ???
def logic(s: String, i: Int): Future[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ???  
val aRoute: Router => Route = VertxFutureServerInterpreter.route(anEndpoint)((logic _).tupled)
```

In practice, routes will be mounted on a router, this router can then be used as a request handler for your http server. 
An HTTP server can then be started as in the following example:

```scala
import sttp.tapir._
import sttp.tapir.server.vertx.VertxFutureEndpointOptions
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._
import io.vertx.core.Vertx
import io.vertx.ext.web._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Main {
  // JVM entry point that starts the HTTP server
  def main(args: Array[String]): Unit = {
    implicit val options: VertxFutureEndpointOptions = ???
    val vertx = Vertx.vertx()
    val server = vertx.createHttpServer()
    val router = Router.router(vertx)
    val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ??? // your definition here
    def logic(s: String, i: Int): Future[Either[Unit, String]] = ??? // your logic here
    val attach = route(anEndpoint)((logic _).tupled)
    attach(router) // your endpoint is now attached to the router, and the route has been created
    Await.result(server.requestHandler(router).listen(9000).asScala, Duration.Inf)
  }
}
```

## Configuration

Every endpoint can be configured by providing an implicit `VertxFutureEndpointOptions`, see [server options](options.md) for details.
You can also provide your own `ExecutionContext` to execute the logic.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.

## Cats Effect typeclasses

Add the following dependency
```scala
"com.softwaremill.sttp.tapir" %% "tapir-vertx-server" % "0.17.14"
"com.softwaremill.sttp.shared" %% "fs2" % "LatestVersion"
```
to use this interpreter with Cats Effect typeclasses.

Then import the object:
```scala
import sttp.tapir.server.vertx.VertxCatsServerInterpreter._
```

This object contains the following methods:

* `route[F[_], I, E, O](e: Endpoint[I, E, O, Fs2Streams[F]])(logic: I => F[Either[E, O]])` returns a function `Router => Route` that will create a route matching the endpoint definition, and attach your logic as an handler. Errors will be recovered automatically.
* `routeRecoverErrors[F[_], I, E, O](e: Endpoint[I, E, O, Fs2Streams[F]])(logic: I => F[O])` returns a function `Router => Route` that will create a route matching the endpoint definition, and attach your `logic` as an handler. You're providing your own way to deal with errors happening in the `logic` function.

Here is simple example which starts HTTP server with one route:
```scala
import cats.effect._
import cats.syntax.flatMap._
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import sttp.tapir._
import sttp.tapir.server.vertx.VertxCatsServerInterpreter._
import sttp.tapir.server.vertx.VertxEffectfulEndpointOptions

object App extends IOApp {

  implicit val opts = VertxEffectfulEndpointOptions()

  val responseEndpoint =
    endpoint
      .in("response")
      .in(query[String]("key"))
      .out(plainBody[String])

  def handler(req: String): IO[Either[Unit, String]] =
    IO.pure(Right(req))

  val attach = route(responseEndpoint)(handler)

  override def run(args: List[String]): IO[ExitCode] =
    Resource.make(IO.delay{
      val vertx = Vertx.vertx()
      val server = vertx.createHttpServer()
      val router = Router.router(vertx)
      attach(router)
      server.requestHandler(router).listen(8080)
    } >>= (_.liftF[IO]))({ server =>
      IO.delay(server.close) >>= (_.liftF[IO].void)
    }).use(_ => IO.never)
}
```

This interpreter also supports streaming using FS2 streams:
```scala
import cats.effect._
import fs2._
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir.server.vertx.VertxCatsServerInterpreter._
import sttp.tapir.server.vertx.VertxEffectfulEndpointOptions

implicit val effect: ConcurrentEffect[IO] = ???

implicit val opts = VertxEffectfulEndpointOptions()

val streamedResponse =
  endpoint
    .in("stream")
    .in(query[Int]("key"))
    .out(streamBody(Fs2Streams[IO])(Schema.string, CodecFormat.TextPlain()))

val attach = route(streamedResponse) { key =>
  IO.pure(Right(Stream.chunk(Chunk.array("Hello world!".getBytes)).repeatN(key)))
}
```

## ZIO

Add the following dependency
```scala
"com.softwaremill.sttp.tapir" %% "tapir-vertx-server" % "0.17.14"
"com.softwaremill.sttp.shared" %% "zio" % "LatestVersion"
```
to use this interpreter with ZIO.

Then import the object:
```scala
import sttp.tapir.server.vertx.VertxZioServerInterpreter._
```

This object contains method `route[R, I, E, O](e: Endpoint[I, E, O, ZioStreams])(logic: I => ZIO[R, E, O])` which returns a function `Router => Route` that will create a route maching the endpoint definition, and attach your logic as an handler.

Here is simple example which starts HTTP server with one route:
```scala
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import sttp.tapir._
import sttp.tapir.server.vertx.VertxZioServerInterpreter._
import sttp.tapir.server.vertx.VertxEffectfulEndpointOptions

import zio._

object Short extends zio.App {

  implicit val opts = VertxEffectfulEndpointOptions()

  implicit val runtime = Runtime.default

  val responseEndpoint =
    endpoint
      .in("response")
      .in(query[String]("key"))
      .out(plainBody[String])

  val attach = route(responseEndpoint) { key => UIO.succeed(key) }

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    ZManaged.make(ZIO.effect {
      val vertx = Vertx.vertx()
      val server = vertx.createHttpServer()
      val router = Router.router(vertx)
      attach(router)
      server.requestHandler(router).listen(8080)
    } >>= (_.asTask))({ server =>
      ZIO.effect(server.close()).flatMap(_.asTask).orDie
    }).useForever.as(ExitCode.success).orDie
}
```

This interpreter supports streaming using ZStreams.
