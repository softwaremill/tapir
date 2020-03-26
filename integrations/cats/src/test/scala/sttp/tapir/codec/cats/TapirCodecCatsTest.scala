package sttp.tapir.codec.cats

import cats.data.{NonEmptyChain, NonEmptyList, NonEmptySet}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatestplus.scalacheck.Checkers
import org.scalacheck.Arbitrary.arbString
import sttp.tapir.SchemaType.{SArray, SString}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema, Validator}
import sttp.tapir.internal._

import scala.collection.immutable.SortedSet

class TapirCodecCatsTest extends FlatSpec with Matchers with Checkers {
  case class Test(value: String)

  implicit val validatorForTest: Validator[Test] = Validator.minLength(3).contramap(_.value)

  it should "find schema for cats collections" in {
    implicitly[Schema[NonEmptyList[String]]].schemaType shouldBe SArray(Schema(SString))
    implicitly[Schema[NonEmptyList[String]]].isOptional shouldBe false

    implicitly[Schema[NonEmptySet[String]]].schemaType shouldBe SArray(Schema(SString))
    implicitly[Schema[NonEmptySet[String]]].isOptional shouldBe false

    implicitly[Schema[NonEmptyChain[String]]].schemaType shouldBe SArray(Schema(SString))
    implicitly[Schema[NonEmptyChain[String]]].isOptional shouldBe false
  }

  it should "find proper validator for cats collections" in {
    val expectedValidator = validatorForTest.asIterableElements[List].and(Validator.minSize(1))

    implicitly[Validator[NonEmptyList[Test]]].show shouldBe expectedValidator.show

    implicitly[Validator[NonEmptySet[Test]]].show shouldBe expectedValidator.show

    implicitly[Validator[NonEmptyChain[Test]]].show shouldBe expectedValidator.show
  }

  implicit def arbitraryNonEmptyList[T: Arbitrary]: Arbitrary[NonEmptyList[T]] = Arbitrary(
    Gen.nonEmptyListOf(implicitly[Arbitrary[T]].arbitrary).map(NonEmptyList.fromListUnsafe(_))
  )

  implicit def arbitraryNonEmptyChain[T: Arbitrary]: Arbitrary[NonEmptyChain[T]] = Arbitrary(
    Gen.nonEmptyListOf(implicitly[Arbitrary[T]].arbitrary).map(NonEmptyChain.fromSeq(_).get)
  )

  implicit def arbitraryNonEmptySet[T: Arbitrary: Ordering]: Arbitrary[NonEmptySet[T]] = Arbitrary(
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

  implicit def set[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[Set[T], Set[U], CF] =
    Codec
      .id[Set[T], CF](c.format)
      .mapDecode(ts => DecodeResult.sequence(ts.toList.map(c.decode)).map(_.toSet))(us => us.map(c.encode))
      .schema(c.schema.map(_.asArrayElement.as[Set[U]]))
      .validate(c.validator.asIterableElements[Set])

  "Provided PlainText codec for non empty set" should "correctly serialize a non empty set" in {
    val codecForNes = implicitly[Codec[Set[String], NonEmptySet[String], CodecFormat.TextPlain]]
    val rawCodec = implicitly[Codec[Set[String], Set[String], CodecFormat.TextPlain]]
    check((a: NonEmptySet[String]) => codecForNes.encode(a) == rawCodec.encode(a.toSortedSet))
  }

  it should "correctly deserialize everything it serialize" in {
    val codecForNes = implicitly[Codec[Set[String], NonEmptySet[String], CodecFormat.TextPlain]]
    check((a: NonEmptySet[String]) => codecForNes.decode(codecForNes.encode(a)) == DecodeResult.Value(a))
  }

  it should "fail on empty list" in {
    val codecForNec = implicitly[Codec[List[String], NonEmptyChain[String], CodecFormat.TextPlain]]
    codecForNec.decode(List()) shouldBe DecodeResult.Missing
  }
}
