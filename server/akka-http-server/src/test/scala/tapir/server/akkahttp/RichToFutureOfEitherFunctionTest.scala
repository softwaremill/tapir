package tapir.server.akkahttp

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RichToFutureOfEitherFunctionTest extends FunSuite with Matchers with ScalaFutures {

  case class Error(r: String)
  case class User(u: String)
  case class Result(r: String)

  test("should compose functions when both succeed") {
    // given
    def f1(p: String): Future[Either[Error, User]] = Future {
      Right(User(p))
    }
    def f2(u: User, i: Int, s: String): Future[Either[Error, Result]] = Future {
      Right(Result(List(u.toString, i.toString, s).mkString(",")))
    }

    // when
    val result = (f1 _).andThenFirstE((f2 _).tupled).apply(("john", 10, "x")).futureValue

    // then
    result shouldBe Right(Result("User(john),10,x"))
  }

  test("should return error if first fails") {
    // given
    def f1(p: String): Future[Either[Error, User]] = Future {
      Left(Error("e1"))
    }
    def f2(u: User, i: Int, s: String): Future[Either[Error, Result]] = Future {
      Right(Result(List(u.toString, i.toString, s).mkString(",")))
    }

    // when
    val result = (f1 _).andThenFirstE((f2 _).tupled).apply(("john", 10, "x")).futureValue

    // then
    result shouldBe Left(Error("e1"))
  }

  test("should return error if second fails") {
    // given
    def f1(p: String): Future[Either[Error, User]] = Future {
      Right(User(p))
    }
    def f2(u: User, i: Int, s: String): Future[Either[Error, Result]] = Future {
      Left(Error("e2"))
    }

    // when
    val result = (f1 _).andThenFirstE((f2 _).tupled).apply(("john", 10, "x")).futureValue

    // then
    result shouldBe Left(Error("e2"))
  }
}
