package tapir.server.http4s

import cats.effect.IO
import org.scalatest.{FunSuite, Matchers}

class RichToMonadFunctionTest extends FunSuite with Matchers {

  case class Error(r: String)
  case class User(u: String)
  case class Result(r: String)

  test("should compose functions when both succeed") {
    // given
    def f1(p: String): IO[User] = IO {
      User(p)
    }
    def f2(u: User, i: Int, s: String): IO[Result] = IO {
      Result(List(u.toString, i.toString, s).mkString(","))
    }

    // when
    val result = (f1 _).andThenFirst((f2 _).tupled).apply(("john", 10, "x")).unsafeRunSync()

    // then
    result shouldBe Result("User(john),10,x")
  }
}
