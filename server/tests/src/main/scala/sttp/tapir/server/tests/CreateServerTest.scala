package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.implicits._
import org.scalatest.Assertion
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.model._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.tests._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

trait CreateServerTest[F[_], +R, OPTIONS, ROUTE] {
  protected type Interceptors = CustomiseInterceptors[F, OPTIONS] => CustomiseInterceptors[F, OPTIONS]

  def testServer[I, E, O](
      e: PublicEndpoint[I, E, O, R],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity
  )(fn: I => F[Either[E, O]])(runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]): Test

  def testServerLogic(
      e: ServerEndpoint[R, F],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity
  )(
      runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test

  def testServerLogicWithStop(
      e: ServerEndpoint[R, F],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity,
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  )(
      runTest: KillSwitch => (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test

  def testServer(name: String, rs: => NonEmptyList[ROUTE])(
      runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test

  /** Override for a server to allow running tests which have access to a stop() effect, allowing shutting down the server within the test.
    * By default, this method just uses a no-op IO.unit.
    */
  def testServerWithStop(name: String, rs: => NonEmptyList[ROUTE], gracefulShutdownTimeout: Option[FiniteDuration])(
      runTest: KillSwitch => (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test
}

class DefaultCreateServerTest[F[_], +R, OPTIONS, ROUTE](
    backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets],
    interpreter: TestServerInterpreter[F, R, OPTIONS, ROUTE]
) extends CreateServerTest[F, R, OPTIONS, ROUTE] {

  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def testServer[I, E, O](
      e: PublicEndpoint[I, E, O, R],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity
  )(
      fn: I => F[Either[E, O]]
  )(runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]): Test = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(interpreter.route(e.serverLogic(fn), interceptors))
    )(runTest)
  }

  override def testServerLogicWithStop(
      e: ServerEndpoint[R, F],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity,
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  )(
      runTest: IO[Unit] => (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test = {
    testServerWithStop(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(interpreter.route(e, interceptors)),
      gracefulShutdownTimeout
    )(runTest)
  }
  override def testServerLogic(
      e: ServerEndpoint[R, F],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity
  )(
      runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(interpreter.route(e, interceptors))
    )(runTest)
  }

  override def testServerWithStop(name: String, rs: => NonEmptyList[ROUTE], gracefulShutdownTimeout: Option[FiniteDuration])(
      runTest: IO[Unit] => (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test = {
    val resources = for {
      portAndStop <- interpreter.serverWithStop(rs, gracefulShutdownTimeout).onError { case e: Exception =>
        Resource.eval(IO(logger.error(s"Starting server failed because of ${e.getMessage}")))
      }
      _ <- Resource.eval(IO(logger.info(s"Bound server on port: ${portAndStop._1}")))
    } yield portAndStop

    Test(name)(
      resources
        .use { case (port, stopServer) =>
          runTest(stopServer)(backend, uri"http://localhost:$port").guarantee(IO(logger.info(s"Tests completed on port $port")))
        }
        .unsafeToFuture()
    )
  }
  override def testServer(name: String, rs: => NonEmptyList[ROUTE])(
      runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test = {
    val resources = for {
      port <- interpreter.server(rs).onError { case e: Exception =>
        Resource.eval(IO(logger.error(s"Starting server failed because of ${e.getMessage}")))
      }
      _ <- Resource.eval(IO(logger.info(s"Bound server on port: $port")))
    } yield port

    Test(name)(
      resources
        .use { port =>
          runTest(backend, uri"http://localhost:$port").guarantee(IO(logger.info(s"Tests completed on port $port")))
        }
        .unsafeToFuture()
    )
  }
}
