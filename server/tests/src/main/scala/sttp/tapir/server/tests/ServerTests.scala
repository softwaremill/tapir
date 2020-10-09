package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{Blocker, ContextShift, IO, Resource}
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, ConfigMap}
import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.WebSockets
import sttp.client3._
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.model._
import sttp.tapir._
import sttp.tapir.server.{DecodeFailureHandler, ServerEndpoint}
import sttp.tapir.tests._

import scala.reflect.ClassTag

abstract class ServerTests[F[_], R, ROUTE] extends AnyFunSuite with Matchers with PortCounterFromConfig with StrictLogging {

  implicit lazy val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
  val backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets] =
    AsyncHttpClientFs2Backend[IO](Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.global)).unsafeRunSync()

  override protected def afterAll(configMap: ConfigMap): Unit = {
    backend.close()
    super.afterAll(configMap)
  }

  //

  def pureResult[T](t: T): F[T]
  def suspendResult[T](t: => T): F[T]

  def route[I, E, O](e: ServerEndpoint[I, E, O, R, F], decodeFailureHandler: Option[DecodeFailureHandler] = None): ROUTE

  def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, R], fn: I => F[O])(implicit eClassTag: ClassTag[E]): ROUTE

  def server(routes: NonEmptyList[ROUTE], port: Port): Resource[IO, Unit]

  def testServer[I, E, O](
      e: Endpoint[I, E, O, R],
      testNameSuffix: String = "",
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  )(
      fn: I => F[Either[E, O]]
  )(runTest: Uri => IO[Assertion]): Unit = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(route(e.serverLogic(fn), decodeFailureHandler))
    )(runTest)
  }

  def testServerLogic[I, E, O](e: ServerEndpoint[I, E, O, R, F], testNameSuffix: String = "")(runTest: Uri => IO[Assertion]): Unit = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(route(e))
    )(runTest)
  }

  def testServer(name: String, rs: => NonEmptyList[ROUTE])(runTest: Uri => IO[Assertion]): Unit = {
    val resources = for {
      port <- Resource.liftF(IO(PortCounter.next()))
      _ <- Resource.liftF(IO(logger.info(s"Trying to bind to $port")))
      _ <- server(rs, port).onError { case e: Exception =>
        Resource.liftF(IO(logger.error(s"Starting server on $port failed because of ${e.getMessage}")))
      }
    } yield uri"http://localhost:$port"

    if (testNameFilter forall name.contains) {
      test(name)(retryIfAddressAlreadyInUse(resources, 3).use(runTest).unsafeRunSync())
    }
  }

  private def retryIfAddressAlreadyInUse[A](r: Resource[IO, A], tries: Int): Resource[IO, A] = {
    r.recoverWith {
      case e: Exception if tries > 1 && e.getMessage.contains("Address already in use") =>
        logger.error(s"Exception when evaluating resource, retrying ${tries - 1} more times", e)
        retryIfAddressAlreadyInUse(r, tries - 1)
    }
  }

  // define to run a single test (temporarily for debugging)
  def testNameFilter: Option[String] = None
}
