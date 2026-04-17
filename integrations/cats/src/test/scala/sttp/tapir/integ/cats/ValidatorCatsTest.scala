package sttp.tapir.integ.cats

import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers
import sttp.tapir.{ValidationError, Validator}

class ValidatorCatsTest extends AnyFlatSpec with Matchers with Checkers {

  it should "success with non-empty list - NonEmptyList" in {
    val value: NonEmptyList[String] = NonEmptyList.of("A", "B", "C")
    val result: Seq[ValidationError[?]] = ValidatorCats.nonEmptyFoldable[NonEmptyList, String].apply(value)
    result shouldBe Nil
  }

  it should "success with non-empty list - List" in {
    val value: List[String] = List("A", "B", "C")
    val result: Seq[ValidationError[?]] = ValidatorCats.nonEmptyFoldable[List, String].apply(value)
    result shouldBe Nil
  }

  it should "fail with empty list - List" in {
    val value: List[String] = Nil
    val result: Seq[ValidationError[?]] = ValidatorCats.nonEmptyFoldable[List, String].apply(value)
    result shouldBe List(ValidationError(Validator.minSize[String, List](1), List(), List()))
  }
}
