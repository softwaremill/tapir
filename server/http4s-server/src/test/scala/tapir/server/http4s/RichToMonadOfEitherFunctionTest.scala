package tapir.server.http4s

import cats.effect.IO
import org.scalatest.{FunSuite, Matchers}

class RichToMonadOfEitherFunctionTest extends FunSuite with Matchers {

  case class Error(r: String)
  case class User(u: String)
  case class Result(r: String)

  test("should compose functions when both succeed") {
    // given
    def f1(p: String): IO[Either[Error, User]] = IO {
      Right(User(p))
    }
    def f2(u: User, i: Int, s: String): IO[Either[Error, Result]] = IO {
      Right(Result(List(u.toString, i.toString, s).mkString(",")))
    }

    // when
    val result = (f1 _).andThenFirstE((f2 _).tupled).apply(("john", 10, "x")).unsafeRunSync()

    // then
    result shouldBe Right(Result("User(john),10,x"))
  }

  test("should return error if first fails") {
    // given
    def f1(p: String): IO[Either[Error, User]] = IO {
      Left(Error("e1"))
    }
    def f2(u: User, i: Int, s: String): IO[Either[Error, Result]] = IO {
      Right(Result(List(u.toString, i.toString, s).mkString(",")))
    }

    // when
    val result = (f1 _).andThenFirstE((f2 _).tupled).apply(("john", 10, "x")).unsafeRunSync()

    // then
    result shouldBe Left(Error("e1"))
  }

  test("should return error if second fails") {
    // given
    def f1(p: String): IO[Either[Error, User]] = IO {
      Right(User(p))
    }
    def f2(u: User, i: Int, s: String): IO[Either[Error, Result]] = IO {
      Left(Error("e2"))
    }

    // when
    val result = (f1 _).andThenFirstE((f2 _).tupled).apply(("john", 10, "x")).unsafeRunSync()

    // then
    result shouldBe Left(Error("e2"))
  }
}
