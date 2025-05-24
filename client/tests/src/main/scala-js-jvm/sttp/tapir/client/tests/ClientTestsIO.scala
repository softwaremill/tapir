package sttp.tapir.client.tests

import java.io.InputStream
import cats.effect._
import cats.effect.unsafe.IORuntime
import cats.implicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.tests.TestUtil._
import sttp.tapir.{DecodeResult, _}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait ClientTestsIO[R] extends AsyncFunSuite with ClientTests[R] with Matchers with BeforeAndAfterAll {
  implicit protected val ioRT: IORuntime = ClientTestsPlatform.ioRT
  implicit override val executionContext: ExecutionContext = ClientTestsPlatform.executionContext

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): Future[Either[E, O]] =
    sendIO(e, port, securityArgs, args, scheme).unsafeToFuture()

  override def safeSend[A, I, E, O](e: Endpoint[A, I, E, O, R], port: Port, securityArgs: A, args: I): Future[DecodeResult[Either[E, O]]] =
    safeSendIO(e, port, securityArgs, args).unsafeToFuture()

  def sendIO[A, I, E, O](e: Endpoint[A, I, E, O, R], port: Port, securityArgs: A, args: I, scheme: String = "http"): IO[Either[E, O]]
  def safeSendIO[A, I, E, O](e: Endpoint[A, I, E, O, R], port: Port, securityArgs: A, args: I): IO[DecodeResult[Either[E, O]]]

  override def testClient[A, I, E, O](e: Endpoint[A, I, E, O, R], securityArgs: A, args: I, expectedResult: Either[E, O]): Unit = {
    test(e.showDetail) {
      // adjust test result values to a form that is comparable by scalatest
      def adjust(r: Either[Any, Any]): IO[Either[Any, Any]] = {
        def doAdjust(v: Any) =
          v match {
            case is: InputStream => IO(inputStreamToByteArray(is).toList)
            case a: Array[Byte]  => IO(a.toList)
            case f: TapirFile    => IO.fromFuture(IO(readFromFile(f)))
            case _               => IO(v)
          }

        r.map(doAdjust).left.map(doAdjust).bisequence
      }

      val r = for {
        result <- sendIO(e, port, securityArgs, args)
        adjustedResult <- adjust(result)
        adjustedExpectedResult <- adjust(expectedResult)
      } yield {
        adjustedResult shouldBe adjustedExpectedResult
      }

      r.unsafeToFuture()
    }
  }
}
