package sttp.tapir.client.tests

import java.io.InputStream
import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.implicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.tests.TestUtil._
import sttp.tapir.{DecodeResult, _}

import scala.concurrent.{ExecutionContext, Future}

abstract class ClientTests[R] extends AsyncFunSuite with Matchers with BeforeAndAfterAll {
  // Using the default ScalaTest execution context seems to cause issues on JS.
  // https://github.com/scalatest/scalatest/issues/1039
  implicit override val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  type Port = Int
  var port: Port = 51823

  def send[A, I, E, O](e: Endpoint[A, I, E, O, R], port: Port, securityArgs: A, args: I, scheme: String = "http"): IO[Either[E, O]]
  def safeSend[A, I, E, O](e: Endpoint[A, I, E, O, R], port: Port, securityArgs: A, args: I): IO[DecodeResult[Either[E, O]]]

  def testClient[A, I, E, O](e: Endpoint[A, I, E, O, R], securityArgs: A, args: I, expectedResult: Either[E, O]): Unit = {
    test(e.showDetail) {
      // adjust test result values to a form that is comparable by scalatest
      def adjust(r: Either[Any, Any]): Future[Either[Any, Any]] = {
        def doAdjust(v: Any) =
          v match {
            case is: InputStream => Future.successful(inputStreamToByteArray(is).toList)
            case a: Array[Byte]  => Future.successful(a.toList)
            case f: TapirFile    => readFromFile(f)
            case _               => Future.successful(v)
          }

        r.map(doAdjust).left.map(doAdjust).bisequence
      }

      for {
        result <- send(e, port, securityArgs, args).unsafeToFuture()
        (adjustedResult, adjustedExpectedResult) <- adjust(result).zip(adjust(expectedResult))
      } yield adjustedResult shouldBe adjustedExpectedResult
    }
  }

  def platformIsScalaJS: Boolean = System.getProperty("java.vm.name") == "Scala.js"
}
