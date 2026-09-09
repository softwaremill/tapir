package sttp.tapir.json.pickler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.{DecodeResult, SchemaType}

import Fixtures.*

/** Port of the uPickle-based module's `PicklerCustomizationTest`. The two `@default` tests are rewritten for the D5.2 decision: tapir's
  * `@default` annotation is documentation only, Scala default parameters drive decoding.
  */
class PicklerCustomizationTest extends AnyFlatSpec with Matchers {

  behavior of "Pickler customization"

  it should "use encodedName from configuration" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    given config: PicklerConfiguration = PicklerConfiguration.default.withSnakeCaseMemberNames

    // when
    val derived = Pickler.derived[TopClass]
    val jsonStr = derived.toCodec.encode(TopClass("field_a_value", InnerClass(7954)))

    // then
    jsonStr shouldBe """{"field_a":"field_a_value","field_b":{"field_a11":7954}}"""
  }

  it should "use encodedName from annotations" in {
    // given
    import generic.auto.* // for Pickler auto-derivation

    // when
    val derived = Pickler.derived[TopClass2]
    val jsonStr = derived.toCodec.encode(TopClass2("field_a_value", AnnotatedInnerClass("f-a-value", "f-b-value")))

    // then
    jsonStr shouldBe """{"fieldA":"field_a_value","fieldB":{"encoded_field-a":"f-a-value","fieldB":"f-b-value"}}"""
  }

  it should "decode using custom encodedName" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    given config: PicklerConfiguration = PicklerConfiguration.default.withSnakeCaseMemberNames

    // when
    val derived = Pickler.derived[TopClass]
    val jsonStr = """{"field_a":"field_a_value","field_b":{"field_a11":7954}}"""
    val obj = derived.toCodec.decode(jsonStr)

    // then
    obj shouldBe Value(TopClass("field_a_value", InnerClass(7954)))
  }

  it should "document @default in the schema but not use it to fill missing fields (D5.2)" in {
    // was: "apply defaults from annotations" -- the uPickle-based module filled fieldA from @default("field-a-default")
    import generic.auto.* // for Pickler auto-derivation

    // when
    val pickler1 = Pickler.derived[ClassWithDefault]
    val codecCc1 = pickler1.toCodec
    val codecCc2 = Pickler.derived[ClassWithDefault2].toCodec
    val codecCc3 = Pickler.derived[ClassWithDefault3].toCodec
    val jsonStrCc11 = codecCc1.encode(ClassWithDefault("field-a-user-value", "msg104"))

    // then: encoding is unaffected
    jsonStrCc11 shouldBe """{"fieldA":"field-a-user-value","fieldB":"msg104"}"""
    // the annotation lands in the schema
    pickler1.schema.schemaType.asInstanceOf[SchemaType.SProduct[ClassWithDefault]].fields.head.schema.default.map(_._1) shouldBe
      Some("field-a-default")
    // but a missing field with only a tapir @default is a decode error
    codecCc1.decode("""{"fieldB":"msg105"}""") shouldBe a[DecodeResult.Error]
    codecCc2.decode("""{"fieldA":"msgCc12"}""") shouldBe a[DecodeResult.Error]
    codecCc3.decode("""{"fieldA":{"$type":"ErrorNotFound"}, "fieldC": {"fieldInner": "deeper field inner"}}""") shouldBe
      a[DecodeResult.Error]
    // while a fully specified object decodes as before
    codecCc3.decode(
      """{"fieldA":{"$type":"ErrorNotFound"},"fieldB":{"fieldInner":"b","fieldInnerInt":1},"fieldC":{"fieldInner":"c","fieldInnerInt":2}}"""
    ) shouldBe Value(ClassWithDefault3(ErrorNotFound, InnerCaseClass("b", 1), InnerCaseClass("c", 2)))
  }

  it should "apply Scala default parameters, ignoring a competing @default (D5.2)" in {
    // was: "apply defaults from class fields, then annotations" -- the annotation used to win over the Scala default
    import generic.auto.* // for Pickler auto-derivation

    // when
    val codecCc1 = Pickler.derived[ClassWithScalaDefault].toCodec
    val codecCc2 = Pickler.derived[ClassWithScalaAndTapirDefault].toCodec
    val jsonStrCc11 = codecCc1.encode(ClassWithScalaDefault("field-a-user-value", "msg104"))
    val jsonStrCc12 = codecCc1.encode(ClassWithScalaDefault("field-a-default", "text b"))
    val object12 = codecCc1.decode("""{"fieldB":"msg205"}""")
    val object2 = codecCc2.decode("""{"fieldB":"msgCc22"}""")

    // then
    jsonStrCc11 shouldBe """{"fieldA":"field-a-user-value","fieldB":"msg104"}"""
    jsonStrCc12 shouldBe """{"fieldA":"field-a-default","fieldB":"text b"}"""
    object12 shouldBe Value(ClassWithScalaDefault("field-a-default", "msg205"))
    object2 shouldBe Value(ClassWithScalaAndTapirDefault("field-a-scala-default", "msgCc22", 55))
  }
}
