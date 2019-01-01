package tapir.server.http4s
import cats.data.Kleisli
import cats.effect.concurrent.Ref
import cats.effect._
import fs2.concurrent.SignallingRef
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.http4s.{Request, Response}
import tapir.Endpoint
import tapir.server.tests.ServerTests
import tapir.typelevel.ParamsAsArgs
import cats.implicits._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Http4sServerTests extends ServerTests[IO] {

  implicit private val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit private val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit private val timer: Timer[IO] = IO.timer(ec)

  override def pureResult[T](t: T): IO[T] = IO.pure(t)

  override def server[I, E, O, FN[_]](e: Endpoint[I, E, O], port: Port, fn: FN[IO[Either[E, O]]])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Resource[IO, Unit] = {

    val service: Kleisli[IO, Request[IO], Response[IO]] = e.toHttp4sRoutes(fn).orNotFound

    val server: IO[(SignallingRef[IO, Boolean], Fiber[IO, ExitCode])] =
      for {
        exitSignal <- SignallingRef[IO, Boolean](initial = false)
        exitCodeFiber <- BlazeServerBuilder[IO]
          .bindHttp(port, "localhost")
          .withHttpApp(service)
          .serveWhile(exitSignal, Ref.unsafe(ExitCode.Success))
          .compile
          .lastOrError
          .start
        _ <- timer.sleep(100.millis) //TODO: Find a way to get a signal when server is online
      } yield (exitSignal, exitCodeFiber)

    val serverResource: Resource[IO, (SignallingRef[IO, Boolean], Fiber[IO, ExitCode])] = Resource.make(server) {
      case (signallingRef, _) => signallingRef.set(true)
    }

    serverResource.map(_ => ())
  }

}
