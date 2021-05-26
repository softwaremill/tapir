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

class DefaultCreateServerTest[F[_], +R, ROUTE, B](
    backend: SttpBackend[IO, R],
    interpreter: TestServerInterpreter[F, R, ROUTE, B]
) extends CreateServerTest[F, R, ROUTE, B]
    with StrictLogging {
  override def testServer[I, E, O](
      e: Endpoint[I, E, O, R],
      testNameSuffix: String = "",
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[F, B]] = None
  )(
      fn: I => F[Either[E, O]]
  )(runTest: (SttpBackend[IO, R], Uri) => IO[Assertion]): Test = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(interpreter.route(e.serverLogic(fn), decodeFailureHandler, metricsInterceptor))
    )(runTest)
  }

  override def testServerLogic[I, E, O](e: ServerEndpoint[I, E, O, R, F], testNameSuffix: String = "")(
      runTest: (SttpBackend[IO, R], Uri) => IO[Assertion]
  ): Test = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(interpreter.route(e))
    )(runTest)
  }

  override def testServer(name: String, rs: => NonEmptyList[ROUTE])(
      runTest: (SttpBackend[IO, R], Uri) => IO[Assertion]
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
        .unsafeRunSync()
    )
  }
}

object DefaultCreateServerTest {
  type StreamsWithWebsockets = Fs2Streams[IO] with WebSockets
}

trait CreateServerTest[F[_], +R, ROUTE, B] {
  def testServer[I, E, O](
      e: Endpoint[I, E, O, R],
      testNameSuffix: String = "",
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[F, B]] = None
  )(fn: I => F[Either[E, O]])(runTest: (SttpBackend[IO, R], Uri) => IO[Assertion]): Test

  def testServerLogic[I, E, O](e: ServerEndpoint[I, E, O, R, F], testNameSuffix: String = "")(
      runTest: (SttpBackend[IO, R], Uri) => IO[Assertion]
  ): Test

  def testServer(name: String, rs: => NonEmptyList[ROUTE])(runTest: (SttpBackend[IO, R], Uri) => IO[Assertion]): Test
}
