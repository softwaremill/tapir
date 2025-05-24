package sttp.tapir.client.tests

import java.io.InputStream
// import cats.effect._
// import cats.effect.unsafe.IORuntime
import cats.implicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.tests.TestUtil._
import sttp.tapir.{DecodeResult, _}

import scala.concurrent.{ExecutionContext, Future}

trait ClientTests[R] extends AsyncFunSuite with Matchers with BeforeAndAfterAll {
  // implicit val ioRT: IORuntime = ClientTestsPlatform.ioRT
  implicit override val executionContext: ExecutionContext = ClientTestsPlatform.executionContext

  type Port = Int
  var port: Port = 51823

  def send[A, I, E, O](e: Endpoint[A, I, E, O, R], port: Port, securityArgs: A, args: I, scheme: String = "http"): Future[Either[E, O]]
  def safeSend[A, I, E, O](e: Endpoint[A, I, E, O, R], port: Port, securityArgs: A, args: I): Future[DecodeResult[Either[E, O]]]

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

      val r = for {
        result <- send(e, port, securityArgs, args)
        adjustedResult <- adjust(result)
        adjustedExpectedResult <- adjust(expectedResult)
      } yield {
        adjustedResult shouldBe adjustedExpectedResult
      }

      r
    }
  }

  def platformIsScalaJS: Boolean = ClientTestsPlatform.platformIsScalaJS
  def platformIsScalaNative: Boolean = ClientTestsPlatform.platformIsScalaNative
}
