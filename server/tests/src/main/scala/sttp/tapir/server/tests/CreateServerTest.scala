package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.Assertion
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.model._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.tests._
import cats.effect.unsafe.implicits.global

case class Testable[R](name: String,
                    routes: () => NonEmptyList[R],
                    test: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
                   )

trait CreateServerTest[F[_], +R, ROUTE] {
  def testServer[I, E, O](
      e: PublicEndpoint[I, E, O, R],
      testNameSuffix: String = "",
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[F]] = None
  )(fn: I => F[Either[E, O]])(runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]): Test

  def testServerLogic(e: ServerEndpoint[R, F], testNameSuffix: String = "")(
      runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test

  def testServer(name: String, rs: => NonEmptyList[ROUTE])(
      runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test

  def testServer(testbale: Testable[ROUTE]): Test =
    testServer(testbale.name, testbale.routes())(testbale.test)
}

class DefaultCreateServerTest[F[_], +R, ROUTE](
    backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets],
    interpreter: TestServerInterpreter[F, R, ROUTE]
) extends CreateServerTest[F, R, ROUTE]
    with StrictLogging {

  override def testServer[I, E, O](
      e: PublicEndpoint[I, E, O, R],
      testNameSuffix: String = "",
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[F]] = None
  )(
      fn: I => F[Either[E, O]]
  )(runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]): Test = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(interpreter.route(e.serverLogic(fn), decodeFailureHandler, metricsInterceptor))
    )(runTest)
  }

  override def testServerLogic(e: ServerEndpoint[R, F], testNameSuffix: String = "")(
      runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(interpreter.route(e))
    )(runTest)
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
