package sttp.tapir.json.pickler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.{DecodeResult, SchemaType}

import scala.compiletime.testing.typeCheckErrors

import Fixtures.*

/** Field-name configuration and annotations. Note that Scala default parameters drive decoding; tapir's `@default` annotation only
  * documents them.
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

  it should "reject @default on a field without a Scala default, which decoding could not honour" in {
    typeCheckErrors("Pickler.derived[ClassWithDefault]").map(_.message).mkString should include("add a Scala default")
  }

  it should "document a Scala default parameter in the schema, as it may be missing when decoding" in {
    val field =
      Pickler.derived[ClassWithScalaDefault].schema.schemaType.asInstanceOf[SchemaType.SProduct[ClassWithScalaDefault]].fields.head
    field.schema.isOptional shouldBe true
    field.schema.default.map(_._1) shouldBe Some("field-a-default")
  }

  it should "apply Scala default parameters, ignoring a competing @default" in {
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
