# Running as a Vert.X server

Endpoints can be mounted as Vert.x `Route`s on top of a Vert.x `Router`.

Vert.x interpreter can be used with different effect systems (cats-effect, ZIO) as well as Scala's standard `Future`.

## Scala's standard `Future`

Add the following dependency
```scala
"com.softwaremill.sttp.tapir" %% "tapir-vertx-server" % "1.11.8"
```
to use this interpreter with `Future`.

Then import the object:
```scala
import sttp.tapir.server.vertx.VertxFutureServerInterpreter.*
```

This object contains the following methods:

* `route(e: ServerEndpoint[Any, Future])`: returns a function `Router => Route` that will create a route with a handler attached, matching the endpoint definition. Errors will be recovered automatically (but generically)
* `blockingRoute(e: ServerEndpoint[Any, Future])`: returns a function `Router => Route` that will create a route with a blocking handler attached, matching the endpoint definition. Errors will be recovered automatically (but generically)

In practice, routes will be mounted on a router, this router can then be used as a request handler for your http server. 
An HTTP server can then be started as in the following example:

```scala
import sttp.tapir.*
import sttp.tapir.server.vertx.VertxFutureServerInterpreter
import sttp.tapir.server.vertx.VertxFutureServerInterpreter.*
import io.vertx.core.Vertx
import io.vertx.ext.web.*
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*

// JVM entry point that starts the HTTP server - uncomment @main to run
/* @main */ def vertxServer(): Unit = 
  val vertx = Vertx.vertx()
  val server = vertx.createHttpServer()
  val router = Router.router(vertx)
  val anEndpoint: PublicEndpoint[(String, Int), Unit, String, Any] = ??? // your definition here
  def logic(s: String, i: Int): Future[Either[Unit, String]] = ??? // your logic here
  val attach = VertxFutureServerInterpreter().route(anEndpoint.serverLogic((logic _).tupled))
  attach(router) // your endpoint is now attached to the router, and the route has been created
  Await.result(server.requestHandler(router).listen(9000).asScala, Duration.Inf)
```

## Configuration

Every endpoint can be configured by providing an instance of `VertxFutureEndpointOptions`, see [server options](options.md) for details.
You can also provide your own `ExecutionContext` to execute the logic.

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.

## Cats Effect typeclasses

Add the following dependency
```scala
"com.softwaremill.sttp.tapir" %% "tapir-vertx-server-cats" % "1.11.8"
```
to use this interpreter with Cats Effect typeclasses.

Then import the object:
```scala
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter.*
```

This object contains the `route[F[_]](e: ServerEndpoint[Fs2Streams[F], F])` method, which returns a function `Router => Route` that will create a route, with a handler attached, matching the endpoint definition. Errors will be recovered automatically.

Here is simple example which starts HTTP server with one route:

```scala
import cats.effect.*
import cats.effect.std.Dispatcher
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import sttp.tapir.*
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter.*

object App extends IOApp:
  val responseEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint
      .in("response")
      .in(query[String]("key"))
      .out(plainBody[String])

  def handler(req: String): IO[Either[Unit, String]] =
    IO.pure(Right(req))

  override def run(args: List[String]): IO[ExitCode] =
    Dispatcher[IO]
      .flatMap { dispatcher =>
        Resource
          .make(
            IO.delay {
              val vertx = Vertx.vertx()
              val server = vertx.createHttpServer()
              val router = Router.router(vertx)
              val attach = VertxCatsServerInterpreter[IO](dispatcher).route(responseEndpoint.serverLogic(handler))
              attach(router)
              server.requestHandler(router).listen(8080)
            }.flatMap(_.asF[IO])
          )({ server =>
            IO.delay(server.close).flatMap(_.asF[IO].void)
          })
      }
      .use(_ => IO.never)
```

This interpreter also supports streaming using FS2 streams:

```scala
import cats.effect.*
import cats.effect.std.Dispatcher
import fs2.*
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter

val streamedResponse =
  endpoint
    .in("stream")
    .in(query[Int]("key"))
    .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
    
def dispatcher: Dispatcher[IO] = ???

val attach = VertxCatsServerInterpreter(dispatcher).route(streamedResponse.serverLogicSuccess[IO] { key =>
  IO.pure(Stream.chunk(Chunk.array("Hello world!".getBytes)).repeatN(key))
})
```

## ZIO

Add the following dependency

```scala
"com.softwaremill.sttp.tapir" %% "tapir-vertx-server-zio" % "1.11.8"
```

to use this interpreter with ZIO.

Then import the object:
```scala
import sttp.tapir.server.vertx.zio.VertxZioServerInterpreter.*
```

This object contains method `def route(e: ServerEndpoint[ZioStreams, RIO[R, *]])` which returns a function `Router => Route` that will create a route matching the endpoint definition, and with the logic attached as a handler.

Here is simple example which starts HTTP server with one route:

```scala
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import sttp.tapir.{plainBody, query}
import sttp.tapir.ztapir.*
import sttp.tapir.server.vertx.zio.VertxZioServerInterpreter
import sttp.tapir.server.vertx.zio.VertxZioServerInterpreter.*
import zio.*

object Short extends ZIOAppDefault:
  override implicit val runtime = zio.Runtime.default

  val responseEndpoint =
    endpoint
      .in("response")
      .in(query[String]("key"))
      .out(plainBody[String])

  val attach = VertxZioServerInterpreter().route(responseEndpoint.zServerLogic { key => ZIO.succeed(key) })

  override def run = 
    ZIO.scoped(
      ZIO
        .acquireRelease(
          ZIO
            .attempt {
              val vertx = Vertx.vertx()
              val server = vertx.createHttpServer()
              val router = Router.router(vertx)
              attach(router)
              server.requestHandler(router).listen(8080)
            }
            .flatMap(_.asRIO)
        ) { server =>
          ZIO.attempt(server.close()).flatMap(_.asRIO).orDie
        } *> ZIO.never
    )
```

This interpreter supports streaming using ZStreams.
