package sttp.tapir.json.pickler

import magnolia1.SealedTrait
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.Schema.annotations.{default, encodedName}
import sttp.tapir.{Schema, SchemaType}
import upickle.core.{ObjVisitor, Visitor}

import Fixtures.*

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

  it should "Decode in a Reader using custom encodedName" in {
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

  it should "apply defaults from annotations" in {
    // given
    import generic.auto.* // for Pickler auto-derivation

    // when
    val codecCc1 = Pickler.derived[ClassWithDefault].toCodec
    val codecCc2 = Pickler.derived[ClassWithDefault2].toCodec
    val codecCc3 = Pickler.derived[ClassWithDefault3].toCodec
    val jsonStrCc11 = codecCc1.encode(ClassWithDefault("field-a-user-value", "msg104"))
    val object12 = codecCc1.decode("""{"fieldB":"msg105"}""")
    val object2 = codecCc2.decode("""{"fieldA":"msgCc12"}""")
    val object3 =
      codecCc3.decode(
        """{"fieldA":{"$type":"ErrorNotFound"}, "fieldC": {"fieldInner": "deeper field inner"}}"""
      )

    // then
    jsonStrCc11 shouldBe """{"fieldA":"field-a-user-value","fieldB":"msg104"}"""
    object12 shouldBe Value(ClassWithDefault("field-a-default", "msg105"))
    object2 shouldBe Value(ClassWithDefault2("msgCc12", ErrorTimeout))
    object3 shouldBe Value(ClassWithDefault3(ErrorNotFound, InnerCaseClass("def-field", 65), InnerCaseClass("deeper field inner", 4)))
  }

  it should "apply defaults from class fields, then annotations" in {
    // given
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
    object2 shouldBe Value(ClassWithScalaAndTapirDefault("field-a-tapir-default", "msgCc22", 55))
  }
}
