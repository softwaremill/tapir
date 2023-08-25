package sttp.tapir.json

import _root_.upickle.default._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.Schema

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
    import sttp.tapir.generic.auto._ // for Schema auto-derivation
    import generic.auto._ // for Pickler auto-derivation

    // when
    val derived = Pickler.derived[Level1TopClass]
    val jsonStr = derived.toCodec.encode(Level1TopClass("field_a_value", Level1InnerClass(7954)))
    
    // then
    jsonStr shouldBe """{"fieldA":"field_a_value","fieldB":{"fieldA11":7954}}"""
  }
}
