package sttp.tapir.generic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.Schema.annotations.description
import sttp.tapir.{Codec, DecodeResult, SchemaType, ValidationError}

class EnumsCodecDerivationTest extends AnyFlatSpec with Matchers {
  import EnumsCodecDerivationTest._

  it should "derive codec for an sealed-trait based enum" in {
    val codec = Codec.derivedEnumeration[String, Letters].defaultStringBased

    codec.schema.description shouldBe Some("it's a small alphabet")

    codec.encode(Letters.A) shouldBe "A"
    codec.encode(Letters.Phi) shouldBe "Phi"

    codec.decode("A") shouldBe DecodeResult.Value(Letters.A)
    codec.decode("a") shouldBe DecodeResult.Value(Letters.A)
    codec.decode("Phi") shouldBe DecodeResult.Value(Letters.Phi)
    codec.decode("phI") shouldBe DecodeResult.Value(Letters.Phi)

    codec.decode("D") should matchPattern { case DecodeResult.InvalidValue(List(ValidationError(_, "D", _, _))) =>
    }
  }

  it should "derive codec for an Scala enumeration" in {
    val codec = Codec.derivedEnumerationValueCustomise[String, Countries.Country].defaultStringBased

    codec.schema.description shouldBe Some("country")

    codec.encode(Countries.PL) shouldBe "PL"
    codec.encode(Countries.NL) shouldBe "NL"

    codec.decode("PL") shouldBe DecodeResult.Value(Countries.PL)
    codec.decode("pl") shouldBe DecodeResult.Value(Countries.PL)
    codec.decode("Nl") shouldBe DecodeResult.Value(Countries.NL)

    codec.decode("CZ") should matchPattern { case DecodeResult.InvalidValue(List(ValidationError(_, "CZ", _, _))) =>
    }
  }

  it should "derive a customised codec for an Scala enumeration" in {
    val codec = Codec.derivedEnumerationValueCustomise[Int, Countries.Country](
      {
        case 0 => Some(Countries.PL)
        case 1 => Some(Countries.NL)
        case _ => None
      },
      {
        case Countries.PL => 0
        case Countries.NL => 1
        case _            => -1
      },
      None
    )

    codec.schema.schemaType shouldBe SchemaType.SInteger()
    codec.schema.description shouldBe Some("country")

    codec.encode(Countries.PL) shouldBe "0"
    codec.encode(Countries.NL) shouldBe "1"

    codec.decode("0") shouldBe DecodeResult.Value(Countries.PL)
    codec.decode("1") shouldBe DecodeResult.Value(Countries.NL)

    codec.decode("2") should matchPattern { case DecodeResult.InvalidValue(List(ValidationError(_, 2, _, _))) =>
    }
  }

  it should "derive a default codec for an Scala enumeration" in {
    val codec = implicitly[PlainCodec[Countries.Country]]

    codec.schema.schemaType shouldBe SchemaType.SString()
    codec.schema.description shouldBe Some("country")

    codec.encode(Countries.PL) shouldBe "PL"
    codec.encode(Countries.NL) shouldBe "NL"

    codec.decode("PL") shouldBe DecodeResult.Value(Countries.PL)
    codec.decode("pl") shouldBe DecodeResult.Value(Countries.PL)
    codec.decode("Nl") shouldBe DecodeResult.Value(Countries.NL)

    codec.decode("CZ") should matchPattern { case DecodeResult.InvalidValue(List(ValidationError(_, "CZ", _, _))) =>
    }
  }

}

object EnumsCodecDerivationTest {
  @description("country")
  object Countries extends Enumeration {
    type Country = Value
    val PL = Value("PL")
    val NL = Value("NL")
  }

  @description("it's a small alphabet")
  sealed trait Letters

  object Letters {
    case object A extends Letters
    case object B extends Letters
    case object C extends Letters
    case object Phi extends Letters
  }
}
