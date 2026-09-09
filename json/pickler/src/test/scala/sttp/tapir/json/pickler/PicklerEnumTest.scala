package sttp.tapir.json.pickler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value

import scala.compiletime.testing.typeCheckErrors

import Fixtures.*

/** Port of the uPickle-based module's `PicklerEnumTest`. One change: the `oneOfUsingField`-on-enums rejection checks the error message and
  * no longer builds picklers for individual enum cases (see `PicklerFacadeTest`).
  */
class PicklerEnumTest extends AnyFlatSpec with Matchers {

  behavior of "Pickler derivation for enumerations"

  it should "support simple enums" in {
    // given
    import generic.auto.* // for Pickler auto-derivation

    // when
    val picklerResponse = Pickler.derived[Response]
    val codec = picklerResponse.toCodec
    val inputObj = Response(ColorEnum.Pink, "pink!!")
    val encoded = codec.encode(inputObj)

    // then
    encoded shouldBe """{"color":"Pink","description":"pink!!"}"""
    codec.decode(encoded) shouldBe Value(inputObj)
  }

  it should "support enums with fields" in {
    // given
    import generic.auto.* // for Pickler auto-derivation

    // when
    val picklerResponse = Pickler.derived[RichColorResponse]
    val codec = picklerResponse.toCodec
    val inputObj = RichColorResponse(RichColorEnum.Cyan)
    val encoded = codec.encode(inputObj)

    // then
    encoded shouldBe """{"color":"Cyan"}"""
    codec.decode(encoded) shouldBe Value(inputObj)
  }

  it should "handle enums with ordinal encoding" in {
    // given
    given Pickler[ColorEnum] = Pickler
      .derivedEnumeration[ColorEnum]
      .customStringBased(_.ordinal.toString)

    // when
    val picklerResponse = Pickler.derived[Response]
    val codec = picklerResponse.toCodec
    val inputObj = Response(ColorEnum.Pink, "pink!!")
    val encoded = codec.encode(inputObj)

    // then
    encoded shouldBe """{"color":"1","description":"pink!!"}"""
    codec.decode(encoded) shouldBe Value(inputObj)
  }

  it should "handle enums with custom function encoding" in {
    // given
    given picklerColorEnum: Pickler[RichColorEnum] =
      Pickler.derivedEnumeration[RichColorEnum].customStringBased(enumValue => s"color-number-${enumValue.code}")

    // when
    val picklerResponse = Pickler.derived[RichColorResponse]
    val codec = picklerResponse.toCodec
    val inputObj = RichColorResponse(RichColorEnum.Cyan)
    val encoded = codec.encode(inputObj)

    // then
    encoded shouldBe """{"color":"color-number-3"}"""
    codec.decode(encoded) shouldBe Value(inputObj)
  }

  it should "handle sealed hierarchies consisting of objects only" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    val inputObj = SealedVariantContainer(VariantA)

    // when
    val pickler = Pickler.derived[SealedVariantContainer]
    val codec = pickler.toCodec
    val encoded = codec.encode(inputObj)

    // then
    encoded shouldBe """{"v":"VariantA"}"""
  }

  it should "Reject oneOfUsingField for enums" in {
    val errors = typeCheckErrors("""
      given picklerRichColor: Pickler[RichColorEnum] =
        Pickler.oneOfUsingField[RichColorEnum, Int](_.code, codeInt => s"code-$codeInt")(
          3 -> (null: Pickler[RichColorEnum.Cyan.type]),
          18 -> (null: Pickler[RichColorEnum.Magenta.type])
        )""").map(_.message).mkString
    errors should include("derivedEnumeration")
  }

  it should "encode and decode an enum where the cases are not alphabetically sorted" in {
    // given
    import generic.auto.* // for Pickler auto-derivation

    // when
    val testPickler = Pickler.derived[NotAlphabetical]
    val codec = testPickler.toCodec
    val encoded = codec.encode(NotAlphabetical.Xyz)

    // then
    encoded shouldBe """"Xyz""""
    codec.decode(encoded) shouldBe Value(NotAlphabetical.Xyz)
  }
}
