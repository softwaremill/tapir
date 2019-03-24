package tapir.server.akkahttp

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RichToFutureFunctionTest extends FunSuite with Matchers with ScalaFutures {

  case class User(u: String)
  case class Result(r: String)

  test("should compose functions when both succeed") {
    // given
    def f1(p: String): Future[User] = Future {
      User(p)
    }
    def f2(u: User, i: Int, s: String): Future[Result] = Future {
      Result(List(u.toString, i.toString, s).mkString(","))
    }

    // when
    val result = (f1 _).andThenFirst((f2 _).tupled).apply(("john", 10, "x")).futureValue

    // then
    result shouldBe Result("User(john),10,x")
  }
}
