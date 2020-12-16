package sttp.tapir.integ.cats

import cats.data.{NonEmptyChain, NonEmptyList, NonEmptySet}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers
import org.scalacheck.Arbitrary.arbString
import sttp.tapir.SchemaType.{SArray, SString}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema, Validator}
import codec._

import scala.collection.immutable.SortedSet

class TapirCodecCatsTest extends AnyFlatSpec with Matchers with Checkers {
  case class Test(value: String)

  it should "find schema for cats collections" in {
    implicitly[Schema[NonEmptyList[String]]].schemaType shouldBe SArray(Schema(SString))
    implicitly[Schema[NonEmptyList[String]]].isOptional shouldBe false

    implicitly[Schema[NonEmptySet[String]]].schemaType shouldBe SArray(Schema(SString))
    implicitly[Schema[NonEmptySet[String]]].isOptional shouldBe false

    implicitly[Schema[NonEmptyChain[String]]].schemaType shouldBe SArray(Schema(SString))
    implicitly[Schema[NonEmptyChain[String]]].isOptional shouldBe false
  }

  it should "find proper validator for cats collections" in {
    implicit val schemaForTest: Schema[Test] = Schema.derived[Test].validate(Validator.minLength(3).contramap(_.value))

    def expectedValidator[C[X] <: Iterable[X]] = schemaForTest.validator.asIterableElements[C].and(Validator.minSize(1))

    implicitly[Schema[NonEmptyList[Test]]].validator.show shouldBe expectedValidator[List].show

    implicitly[Schema[NonEmptySet[Test]]].validator.show shouldBe expectedValidator[List].show

    implicitly[Schema[NonEmptyChain[Test]]].validator.show shouldBe expectedValidator[Set].show
  }

  implicit def arbitraryNonEmptyList[T: Arbitrary]: Arbitrary[NonEmptyList[T]] =
    Arbitrary(
      Gen.nonEmptyListOf(implicitly[Arbitrary[T]].arbitrary).map(NonEmptyList.fromListUnsafe(_))
    )

  implicit def arbitraryNonEmptyChain[T: Arbitrary]: Arbitrary[NonEmptyChain[T]] =
    Arbitrary(
      Gen.nonEmptyListOf(implicitly[Arbitrary[T]].arbitrary).map(NonEmptyChain.fromSeq(_).get)
    )

  implicit def arbitraryNonEmptySet[T: Arbitrary: Ordering]: Arbitrary[NonEmptySet[T]] =
    Arbitrary(
      Gen.nonEmptyBuildableOf[SortedSet[T], T](implicitly[Arbitrary[T]].arbitrary).map(NonEmptySet.fromSet(_).get)
    )

  "Provided PlainText coder for non empty list" should "correctly serialize a non empty list" in {
    val codecForNel = implicitly[Codec[List[String], NonEmptyList[String], CodecFormat.TextPlain]]
    val rawCodec = implicitly[Codec[List[String], List[String], CodecFormat.TextPlain]]
    check((a: NonEmptyList[String]) => codecForNel.encode(a) == rawCodec.encode(a.toList))
  }

  it should "correctly deserialize everything it serialize" in {
    val codecForNel = implicitly[Codec[List[String], NonEmptyList[String], CodecFormat.TextPlain]]
    check((a: NonEmptyList[String]) => codecForNel.decode(codecForNel.encode(a)) == DecodeResult.Value(a))
  }

  it should "fail on empty list" in {
    val codecForNel = implicitly[Codec[List[String], NonEmptyList[String], CodecFormat.TextPlain]]
    codecForNel.decode(List()) shouldBe DecodeResult.Missing
  }

  it should "have the proper schema" in {
    val codecForNel = implicitly[Codec[List[String], NonEmptyList[String], CodecFormat.TextPlain]]
    codecForNel.schema.copy(validator = Validator.pass) shouldBe implicitly[Schema[NonEmptyList[String]]].copy(validator = Validator.pass)
    codecForNel.schema.validator.show shouldBe implicitly[Schema[NonEmptyList[String]]].validator.show
  }

  it should "have the proper validator" in {
    val codecForNel = implicitly[Codec[List[String], NonEmptyList[String], CodecFormat.TextPlain]]
    codecForNel.validator.show shouldBe implicitly[Schema[NonEmptyList[String]]].validator.show
  }

  "Provided PlainText codec for non empty chain" should "correctly serialize a non empty chain" in {
    val codecForNec = implicitly[Codec[List[String], NonEmptyChain[String], CodecFormat.TextPlain]]
    val rawCodec = implicitly[Codec[List[String], List[String], CodecFormat.TextPlain]]
    check((a: NonEmptyChain[String]) => codecForNec.encode(a) == rawCodec.encode(a.toNonEmptyList.toList))
  }

  it should "correctly deserialize everything it serialize" in {
    val codecForNec = implicitly[Codec[List[String], NonEmptyChain[String], CodecFormat.TextPlain]]
    check((a: NonEmptyChain[String]) => codecForNec.decode(codecForNec.encode(a)) == DecodeResult.Value(a))
  }

  it should "fail on empty list" in {
    val codecForNec = implicitly[Codec[List[String], NonEmptyChain[String], CodecFormat.TextPlain]]
    codecForNec.decode(List()) shouldBe DecodeResult.Missing
  }

  it should "have the proper schema" in {
    val codecForNec = implicitly[Codec[List[String], NonEmptyChain[String], CodecFormat.TextPlain]]
    codecForNec.schema.copy(validator = Validator.pass) shouldBe implicitly[Schema[NonEmptyChain[String]]].copy(validator = Validator.pass)
    codecForNec.schema.validator.show shouldBe implicitly[Schema[NonEmptyChain[String]]].validator.show
  }

  it should "have the proper validator" in {
    val codecForNec = implicitly[Codec[List[String], NonEmptyChain[String], CodecFormat.TextPlain]]
    codecForNec.validator.show shouldBe implicitly[Schema[NonEmptyChain[String]]].validator.show
  }

  "Provided PlainText codec for non empty set" should "correctly serialize a non empty set" in {
    val codecForNes = implicitly[Codec[List[String], NonEmptySet[String], CodecFormat.TextPlain]]
    val rawCodec = implicitly[Codec[List[String], Set[String], CodecFormat.TextPlain]]
    check((a: NonEmptySet[String]) => codecForNes.encode(a) == rawCodec.encode(a.toSortedSet))
  }

  it should "correctly deserialize everything it serialize" in {
    val codecForNes = implicitly[Codec[List[String], NonEmptySet[String], CodecFormat.TextPlain]]
    check((a: NonEmptySet[String]) => codecForNes.decode(codecForNes.encode(a)) == DecodeResult.Value(a))
  }

  it should "fail on empty set" in {
    val codecForNes = implicitly[Codec[List[String], NonEmptySet[String], CodecFormat.TextPlain]]
    codecForNes.decode(Nil) shouldBe DecodeResult.Missing
  }

  it should "have the proper schema" in {
    val codecForNes = implicitly[Codec[List[String], NonEmptySet[String], CodecFormat.TextPlain]]
    codecForNes.schema.copy(validator = Validator.pass) shouldBe implicitly[Schema[NonEmptySet[String]]].copy(validator = Validator.pass)
    codecForNes.schema.validator.show shouldBe implicitly[Schema[NonEmptySet[String]]].validator.show
  }

  it should "have the proper validator" in {
    val codecForNes = implicitly[Codec[List[String], NonEmptySet[String], CodecFormat.TextPlain]]
    codecForNes.validator.show shouldBe implicitly[Schema[NonEmptySet[String]]].validator.show
  }
}
