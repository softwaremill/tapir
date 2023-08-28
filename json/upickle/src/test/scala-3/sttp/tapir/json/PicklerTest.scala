package sttp.tapir.json

import _root_.upickle.default._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.Schema
import sttp.tapir.generic.Configuration
import sttp.tapir.SchemaType

class PicklerTest extends AnyFlatSpec with Matchers {
  behavior of "Pickler derivation"

  case class FlatClass(fieldA: Int, fieldB: String)
  case class Level1TopClass(fieldA: String, fieldB: Level1InnerClass)
  case class Level1InnerClass(fieldA11: Int)

  it should "build from an existing Schema and ReadWriter" in {
    // given schema and reader / writer in scope
    given givenSchemaForCc: Schema[FlatClass] = Schema.derived[FlatClass]
    given givenRwForCc: ReadWriter[FlatClass] = macroRW[FlatClass]

    // when
    val derived = Pickler.derived[FlatClass]
    val obj = derived.toCodec.decode("""{"fieldA": 654, "fieldB": "field_b_value"}""")

    // then
    obj shouldBe Value(FlatClass(654, "field_b_value"))
  }

  it should "build an instance for a flat case class" in {
    // when
    val derived = Pickler.derived[FlatClass]
    val jsonStr = derived.toCodec.encode(FlatClass(44, "b_value"))

    // then
    jsonStr shouldBe """{"fieldA":44,"fieldB":"b_value"}"""
  }

  it should "build an instance for a case class with a nested case class" in {
    // given
    import generic.auto._ // for Pickler auto-derivation

    // when
    val derived = Pickler.derived[Level1TopClass]
    val jsonStr = derived.toCodec.encode(Level1TopClass("field_a_value", Level1InnerClass(7954)))
    val inputJson = """{"fieldA":"field_a_value_2","fieldB":{"fieldA11":-321}}"""
    val resultObj = derived.toCodec.decode(inputJson)

    // then
    jsonStr shouldBe """{"fieldA":"field_a_value","fieldB":{"fieldA11":7954}}"""
    resultObj shouldBe Value(Level1TopClass("field_a_value_2", Level1InnerClass(-321)))
  }

  it should "fail to derive a Pickler when there's a Schema but missing ReadWriter" in {
    assertDoesNotCompile("""
      given givenSchemaForCc: Schema[FlatClass] = Schema.derived[FlatClass]
      Pickler.derived[FlatClass]
    """)
  }

  it should "respect encodedName from Configuration" in {
    // given
    import generic.auto._ // for Pickler auto-derivation
    given schemaConfig: Configuration = Configuration.default.withSnakeCaseMemberNames

    // when
    val derived = Pickler.derived[Level1TopClass]
    val jsonStr = derived.toCodec.encode(Level1TopClass("field_a_value", Level1InnerClass(7954)))

    // then
    jsonStr shouldBe """{"field_a":"field_a_value","field_b":{"field_a11":7954}}"""
  }

  it should "Decode in a Reader using custom encodedName" in {
    // given
    import generic.auto._ // for Pickler auto-derivation
    given schemaConfig: Configuration = Configuration.default.withSnakeCaseMemberNames

    // when
    val derived = Pickler.derived[Level1TopClass]
    val jsonStr = """{"field_a":"field_a_value","field_b":{"field_a11":7954}}"""
    val obj = derived.toCodec.decode(jsonStr)

    // then
    obj shouldBe Value(Level1TopClass("field_a_value", Level1InnerClass(7954)))
  }

  it should "encode sealed trait as enum according to Schema's configuration" in {
    // given
    // sealed trait ErrorCode:
    //   def specialCode: Int
    //
    // case object ErrorNotFound extends ErrorCode:
    //   override def specialCode = 612
    //
    // case object ErrorTimeout extends ErrorCode:
    //   override def specialCode = -5
    //
    //
    // implicit val yEnumSchema: Schema[ErrorCode] = Schema.derivedEnumeration[ErrorCode](
    //   encode = Some(v => v.specialCode),
    //   schemaType = SchemaType.SInteger[ErrorCode]()
    // )
    // case class TopCaseClass(fieldA: NestedCaseClass, fieldB: String)
    // case class NestedCaseClass(errorCode: ErrorCode)
    //
    // import sttp.tapir.generic.auto._ // for Schema auto-derivation
    // import generic.auto._ // for Pickler auto-derivationi
    //
    // // when
    // val derived = Pickler.derived[TopCaseClass]
    // val jsonStr = derived.toCodec.encode(TopCaseClass(NestedCaseClass(ErrorTimeout), "msg18"))
    //
    // // then
    // jsonStr shouldBe """xxxxx"""
  }
}
