package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.Assertion
import sttp.client3._
import sttp.model._
import sttp.tapir._
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.tests._

class CreateServerTest[F[_], +R, ROUTE](interpreter: TestServerInterpreter[F, R, ROUTE]) extends StrictLogging {
  def testServer[I, E, O](
      e: Endpoint[I, E, O, R],
      testNameSuffix: String = "",
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  )(
      fn: I => F[Either[E, O]]
  )(runTest: Uri => IO[Assertion]): Test = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(interpreter.route(e.serverLogic(fn), decodeFailureHandler))
    )(runTest)
  }

  def testServerLogic[I, E, O](e: ServerEndpoint[I, E, O, R, F], testNameSuffix: String = "")(runTest: Uri => IO[Assertion]): Test = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(interpreter.route(e))
    )(runTest)
  }

  def testServer(name: String, rs: => NonEmptyList[ROUTE])(runTest: Uri => IO[Assertion]): Test = {
    val resources = for {
      port <- interpreter.server(rs).onError { case e: Exception =>
        Resource.liftF(IO(logger.error(s"Starting server failed because of ${e.getMessage}")))
      }
      _ <- Resource.liftF(IO(logger.info(s"Bound server on port: $port")))
    } yield port

    Test(name)(
      resources
        .use { port =>
          runTest(uri"http://localhost:$port").guarantee(IO(logger.info(s"Tests completed on port $port")))
        }
        .unsafeRunSync()
    )
  }
}
