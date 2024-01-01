package sttp.tapir

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{Assertion, Inside}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers
import sttp.model.Uri
import sttp.model.Uri._
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.Value

import java.math.{BigDecimal => JBigDecimal}
import java.nio.charset.StandardCharsets
import java.time._
import java.util.{Date, UUID}
import scala.reflect.ClassTag
import scala.util.Try

// see also CodecTestDateTime and CodecDelimitedTest
class CodecTest extends AnyFlatSpec with Matchers with Checkers with Inside {

  private implicit val arbitraryUri: Arbitrary[Uri] = Arbitrary(for {
    scheme <- Gen.alphaLowerStr if scheme.nonEmpty
    host <- Gen.identifier.map(_.take(5)) // schemes may not be too long
    port <- Gen.option[Int](Gen.chooseNum(1, Short.MaxValue))
    path <- Gen.listOfN(5, Gen.identifier)
    query <- Gen.mapOf(Gen.zip(Gen.identifier, Gen.identifier))
  } yield uri"$scheme://$host:$port/${path.mkString("/")}?$query")

  private implicit val arbitraryJBigDecimal: Arbitrary[JBigDecimal] = Arbitrary(
    implicitly[Arbitrary[BigDecimal]].arbitrary.map(bd => new JBigDecimal(bd.toString))
  )

  it should "encode simple types using .toString" in {
    checkEncodeDecodeToString[String]
    checkEncodeDecodeToString[Byte]
    checkEncodeDecodeToString[Int]
    checkEncodeDecodeToString[Long]
    checkEncodeDecodeToString[Float]
    checkEncodeDecodeToString[Double]
    checkEncodeDecodeToString[Boolean]
    checkEncodeDecodeToString[UUID]
    checkEncodeDecodeToString[Uri]
    checkEncodeDecodeToString[BigDecimal]
    checkEncodeDecodeToString[JBigDecimal]
  }

  it should "use default, when available" in {
    val codec = implicitly[Codec[List[String], String, TextPlain]]
    val codecWithDefault = codec.schema(_.default("X"))

    codec.decode(Nil) shouldBe DecodeResult.Missing
    codecWithDefault.decode(Nil) shouldBe DecodeResult.Value("X")

    codec.decode(List("y")) shouldBe DecodeResult.Value("y")
    codec.decode(List("y", "z")) shouldBe DecodeResult.Multiple(List("y", "z"))
  }

  it should "correctly encode and decode Date" in {
    // while Date works on Scala.js, ScalaCheck tests involving Date use java.util.Calendar which doesn't - hence here a normal test
    val codec = implicitly[Codec[String, Date, TextPlain]]
    val d = Date.from(Instant.ofEpochMilli(1619518169000L))
    val encoded = codec.encode(d)
    codec.decode(encoded) == Value(d) && Date.from(Instant.parse(encoded)) == d
  }

  def checkEncodeDecodeToString[T: Arbitrary](implicit c: Codec[String, T, TextPlain], ct: ClassTag[T]): Assertion =
    withClue(s"Test for ${ct.runtimeClass.getName}") {
      check((a: T) => {
        val decodedAndReEncoded = c.decode(c.encode(a)) match {
          case Value(v)   => c.encode(v)
          case unexpected => fail(s"Value $a got decoded to unexpected $unexpected")
        }
        decodedAndReEncoded == a.toString
      })
    }

  it should "create a part codec" in {
    PartCodec(RawBodyType.StringBody(StandardCharsets.UTF_8))[String]
    PartCodec(RawBodyType.StringBody(StandardCharsets.UTF_8))[Int]
    PartCodec(RawBodyType.ByteArrayBody)[Array[Byte]]
  }

  it should "call the validator during decoding when using .mapValidate" in {
    val codec = Codec.int.mapValidate(Validator.min(18))(Member(_))(_.age)

    codec.decode("10") should matchPattern { case DecodeResult.InvalidValue(_) => }
    codec.schema.validator should not be (Validator.pass)
  }

  it should "provide a mapEither function" in {
    val codec: Codec[String, Int, TextPlain] = Codec.string.mapEither(s => Try(s.toInt).toEither.left.map(_.getMessage))(_.toString)
    codec.encode(10) should be("10")
    codec.decode("10") should be(DecodeResult.Value(10))
    inside(codec.decode("foo")) { case DecodeResult.Error(original, error) =>
      original should be("foo")
      error.getMessage should be("""For input string: "foo"""")
    }
  }

  case class Member(age: Int) {
    require(age >= 18)
  }
}
