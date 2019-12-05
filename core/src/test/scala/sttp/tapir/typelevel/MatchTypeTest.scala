package sttp.tapir.typelevel

import org.scalatest.{AppendedClues, FlatSpec, Matchers}

class MatchTypeTest extends FlatSpec with Matchers with AppendedClues {
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

  val t: Byte = 0xF.toByte
  val s: Short = 1

  val matcherAndTypes : Seq[(MatchType[_], Any)] = Seq(
    implicitly[MatchType[String]] -> "string",
    implicitly[MatchType[Boolean]] -> true,
    implicitly[MatchType[Char]] -> 'c',
    implicitly[MatchType[Byte]] -> t,
    implicitly[MatchType[Short]] -> s,
    implicitly[MatchType[Float]] -> 42.2f,
    implicitly[MatchType[Double]] -> 42.2d,
    implicitly[MatchType[Int]] -> 42,
  )

  it should "provide implicit for all primitive class returning true for other primitive type" in {
    matcherAndTypes.foreach({ (matcher: MatchType[_], a: Any) =>
      (matcher(a) shouldBe true) withClue  (s"Matcher $matcher did not recognize $a")
      }.tupled)
  }

  it should "provide implicit for all primitive class returning false for any other primitive type" in {
    matcherAndTypes.permutations.foreach({ case (matcher, _)::tl =>
      tl.map(_._2).foreach{ badValue =>
        (matcher(badValue) shouldBe false) withClue  (s"Matcher $matcher did not reject $badValue")
      }
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

  it should "work for list" in {
    val matchType: MatchType[List[Int]] = implicitly

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
