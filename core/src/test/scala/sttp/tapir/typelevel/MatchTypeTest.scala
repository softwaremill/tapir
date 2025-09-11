package sttp.tapir.typelevel

import org.scalatest.AppendedClues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MatchTypeTest extends AnyFlatSpec with MatchTypeTestExtensions with Matchers with AppendedClues {
  "MatchType" should "provide implicit for basic type" in {

    val string: Any = "String"
    val bool: Any = true
    val int: Any = 42

    val matchTypeString: MatchType[String] = implicitly
    val matchTypeBool: MatchType[Boolean] = implicitly
    val matchTypeInt: MatchType[Int] = implicitly

    matchTypeString(string) shouldBe true
    matchTypeString(bool) shouldBe false

    matchTypeBool(string) shouldBe false
    matchTypeBool(bool) shouldBe true

    matchTypeInt(string) shouldBe false
    matchTypeInt(bool) shouldBe false
    matchTypeInt(int) shouldBe true
  }

  it should "provide implicit for all primitive class returning true for other primitive type" in {
    matcherAndTypes.foreach({ (matcher: MatchType[?], a: Any) =>
      (matcher(a) shouldBe true) withClue (s"Matcher $matcher did not recognize $a")
    }.tupled)
  }

  it should "provide implicit for all primitive class returning false for any other primitive type" in {
    matcherAndTypes.permutations.foreach({
      case (matcher, _) :: tl =>
        tl.map(_._2).foreach { badValue =>
          (matcher(badValue) shouldBe false) withClue (s"Matcher $matcher did not reject $badValue")
        }
      case _ => fail("impossible")
    })
  }

  it should "provide implicit for case class" in {
    val matchTypeLeftInt: MatchType[Left[Int, Nothing]] = implicitly
    val leftInt: Any = Left(42)
    val leftString: Any = Left("string")
    val rightInt: Any = Right(42)

    matchTypeLeftInt(leftInt) shouldBe true
    matchTypeLeftInt(leftString) shouldBe false
    matchTypeLeftInt(rightInt) shouldBe false
  }

  it should "provide implicit for sealed stuff" in {
    val matchTypeLeftInt: MatchType[Either[String, Int]] = implicitly
    val leftInt: Any = Left(42)
    val leftString: Any = Left("string")
    val rightInt: Any = Right(42)

    matchTypeLeftInt(leftInt) shouldBe false
    matchTypeLeftInt(leftString) shouldBe true
    matchTypeLeftInt(rightInt) shouldBe true
  }

  it should "provide implicit for complex nested type" in {
    val matchType: MatchType[Left[Right[String, Some[Int]], String]] = implicitly
    var a: Any = null

    a = Left(Right(Some(42)))
    matchType(a) shouldBe true

    a = Left(Right(Some(true)))
    matchType(a) shouldBe false

    a = Left(Right(None))
    matchType(a) shouldBe false

    a = Left(Left("plop"))
    matchType(a) shouldBe false
  }

  it should "provide implicit for recursive structure" in {
    case class A(s: Option[A])

    lazy implicit val matchType: MatchType[A] = MatchType.gen
    var a: Any = null

    a = A(Some(A(None)))
    matchType(a) shouldBe true

    a = Left(Left("plop"))
    matchType(a) shouldBe false
  }

  it should "work for list" in {
    lazy implicit val matchType: MatchType[List[Int]] = MatchType.gen

    var a: Any = List(1)
    matchType(a) shouldBe true

    a = List(1, 2, 3)
    matchType(a) shouldBe true

    a = List("plop")
    matchType(a) shouldBe false

    a = List()
    matchType(a) shouldBe true
  }
}
