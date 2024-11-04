# Running as an Armeria server

Endpoints can be mounted as `TapirService[S, F]` on top of [Armeria](https://armeria.dev)'s `HttpServiceWithRoutes`.

Armeria interpreter can be used with different effect systems (cats-effect, ZIO) as well as Scala's standard `Future`.

## Scala's standard `Future`

Add the following dependency
```scala
"com.softwaremill.sttp.tapir" %% "tapir-armeria-server" % "1.11.8"
```

and import the object:

```scala
import sttp.tapir.server.armeria.ArmeriaFutureServerInterpreter
```
to use this interpreter with `Future`.

The `toService` method require a single, or a list of `ServerEndpoint`s, which can be created by adding
[server logic](logic.md) to an endpoint.

```scala
import sttp.tapir.*
import sttp.tapir.server.armeria.ArmeriaFutureServerInterpreter
import scala.concurrent.Future
import com.linecorp.armeria.server.Server

// JVM entry point that starts the HTTP server - uncommment @main to run
/* @main */ def armeriaSerer(): Unit =
  val tapirEndpoint: PublicEndpoint[(String, Int), Unit, String, Any] = ??? // your definition here
  def logic(s: String, i: Int): Future[Either[Unit, String]] = ??? // your logic here
  val tapirService = ArmeriaFutureServerInterpreter().toService(tapirEndpoint.serverLogic((logic _).tupled))
  val server = Server
    .builder()
    .service(tapirService) // your endpoint is bound to the server
    .build()
  server.start().join()
```

This interpreter also supports streaming using Armeria Streams which is fully compatible with Reactive Streams:

```scala
import sttp.capabilities.armeria.ArmeriaStreams
import sttp.tapir.*
import sttp.tapir.server.armeria.ArmeriaFutureServerInterpreter
import scala.concurrent.Future
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import org.reactivestreams.Publisher

val streamingResponse: PublicEndpoint[Int, Unit, Publisher[HttpData], ArmeriaStreams] =
  endpoint
    .in("stream")
    .in(query[Int]("key"))
    .out(streamTextBody(ArmeriaStreams)(CodecFormat.TextPlain()))

def streamLogic(foo: Int): Future[Publisher[HttpData]] = 
  Future.successful(StreamMessage.of(HttpData.ofUtf8("hello"), HttpData.ofUtf8("world")))

val tapirService = ArmeriaFutureServerInterpreter().toService(streamingResponse.serverLogicSuccess(streamLogic))
```

## Configuration

Every endpoint can be configured by providing an instance of `ArmeriaFutureEndpointOptions`, see [server options](options.md) for details.
Note that Armeria automatically injects an `ExecutionContext` on top of Armeria's `EventLoop` to invoke the logic.

## Cats Effect

Add the following dependency
```scala
"com.softwaremill.sttp.tapir" %% "tapir-armeria-server-cats" % "1.11.8"
```
to use this interpreter with Cats Effect typeclasses.

Then import the object:
```scala
import sttp.tapir.server.armeria.cats.ArmeriaCatsServerInterpreter
```

This object contains the `toService(e: ServerEndpoint[Fs2Streams[F], F])` method which returns a `TapirService[Fs2Streams[F], F]`.
An HTTP server can then be started as in the following example:

```scala
import sttp.tapir.*
import sttp.tapir.server.armeria.cats.ArmeriaCatsServerInterpreter
import cats.effect.*
import cats.effect.std.Dispatcher
import com.linecorp.armeria.server.Server
import java.util.concurrent.CompletableFuture

object Main extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    val tapirEndpoint: PublicEndpoint[String, Unit, String, Any] = ???
    def logic(req: String): IO[Either[Unit, String]] = ???
  
    Dispatcher[IO]
      .flatMap { dispatcher =>
        Resource
          .make(
            IO.async_[Server] { cb =>
              val tapirService = ArmeriaCatsServerInterpreter[IO](dispatcher).toService(tapirEndpoint.serverLogic(logic))

              val server = Server
                .builder()
                .service(tapirService)
                .build()
              server.start().handle[Unit] {
                case (_, null)  => cb(Right(server))
                case (_, cause) => cb(Left(cause))
              }
            }
          )({ server =>
            IO.fromCompletableFuture(IO(server.closeAsync().asInstanceOf[CompletableFuture[Unit]]))
          })
      }
      .use(_ => IO.never)
```

This interpreter also supports streaming using FS2 streams:

```scala
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*
import sttp.tapir.server.armeria.cats.ArmeriaCatsServerInterpreter
import cats.effect.*
import cats.effect.std.Dispatcher
import fs2.*

val streamingResponse: Endpoint[Unit, Int, Unit, Stream[IO, Byte], Fs2Streams[IO]] =
  endpoint
    .in("stream")
    .in(query[Int]("times"))
    .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))

def streamLogic(times: Int): IO[Stream[IO, Byte]] =
  IO.pure(Stream.chunk(Chunk.array("Hello world!".getBytes)).repeatN(times))

def dispatcher: Dispatcher[IO] = ???

val tapirService = ArmeriaCatsServerInterpreter(dispatcher).toService(streamingResponse.serverLogicSuccess(streamLogic))
```

## ZIO

Add the following dependency

```scala
"com.softwaremill.sttp.tapir" %% "tapir-armeria-server-zio" % "1.11.8"
```

to use this interpreter with ZIO.

Then import the object:
```scala
import sttp.tapir.server.armeria.zio.ArmeriaZioServerInterpreter
```

This object contains `toService(e: ServerEndpoint[ZioStreams, RIO[R, *]])` method which returns a `TapirService[ZioStreams, RIO[R, *]]`.
An HTTP server can then be started as in the following example:

```scala
import com.linecorp.armeria.server.Server
import sttp.tapir.*
import sttp.tapir.server.armeria.zio.ArmeriaZioServerInterpreter
import sttp.tapir.ztapir.*
import zio.{ExitCode, Runtime, UIO, URIO, ZIO, ZIOAppDefault}
import java.util.concurrent.CompletableFuture

object Main extends ZIOAppDefault:
  override def run: URIO[Any, ExitCode] = 
    given Runtime[Any] = Runtime.default

    val tapirEndpoint: PublicEndpoint[String, Unit, String, Any] = ???
    def logic(key: String): UIO[String] = ???

    val s = ZIO.fromCompletableFuture {
      val tapirService = ArmeriaZioServerInterpreter().toService(tapirEndpoint.zServerLogic(logic))
      val server = Server
        .builder()
        .service(tapirService)
        .build()
      server.start().thenApply[Server](_ => server)
    }

    ZIO.scoped(ZIO.acquireRelease(s)(server => 
      ZIO.fromCompletableFuture(server.closeAsync().asInstanceOf[CompletableFuture[Unit]]).orDie) *> ZIO.never).exitCode
```

This interpreter supports streaming using ZStreams.
