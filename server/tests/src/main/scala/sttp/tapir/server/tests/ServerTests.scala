package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.Assertion
import sttp.client3._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.{DecodeFailureHandler, ServerEndpoint}
import sttp.tapir.tests._

class ServerTests[F[_], +R, ROUTE](interpreter: ServerInterpreter[F, R, ROUTE])(implicit m: MonadError[F]) extends StrictLogging {
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
      port <- Resource.liftF(IO(PortCounter.next()))
      _ <- Resource.liftF(IO(logger.info(s"Trying to bind to $port")))
      _ <- interpreter.server(rs, port).onError { case e: Exception =>
        Resource.liftF(IO(logger.error(s"Starting server on $port failed because of ${e.getMessage}")))
      }
    } yield port

    Test(name)(
      retryIfAddressAlreadyInUse(resources, 3)
        .use { port =>
          runTest(uri"http://localhost:$port").guarantee(IO(logger.info(s"Tests completed on port $port")))
        }
        .unsafeRunSync()
    )
  }

  private def retryIfAddressAlreadyInUse[A](r: Resource[IO, A], tries: Int): Resource[IO, A] = {
    r.recoverWith {
      case e: Exception if tries > 1 && e.getMessage.contains("Address already in use") =>
        logger.error(s"Exception when evaluating resource, retrying ${tries - 1} more times", e)
        retryIfAddressAlreadyInUse(r, tries - 1)
    }
  }
}
