package sttp.tapir.integ.cats

import cats.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode
import sttp.tapir.integ.cats.instances._
import sttp.tapir._

class EndpointIOInstancesSpec extends AnyFunSuite with Matchers {
  test("InvariantSemigroupal syntax should work with EndpointInput") {
    """|(query[Int]("foo"): EndpointInput[Int],
       |  query[String]("bar")
       |).tupled: EndpointInput[(Int, String)]""".stripMargin should compile
  }
  test("InvariantSemigroupal syntax should work with EndpointOutput") {
    """|(statusCode: EndpointOutput[StatusCode],
       |  statusCode
       |).tupled: EndpointOutput[(StatusCode, StatusCode)]""".stripMargin should compile
  }
  test("InvariantSemigroupal syntax should work with EndpointIO") {
    """|(header[Int]("foo"): EndpointIO[Int],
       |  header[String]("bar")
       |).tupled: EndpointIO[(Int, String)]""".stripMargin should compile
  }
}
