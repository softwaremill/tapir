package sttp.tapir.json

import _root_.upickle.default._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.Schema
import sttp.tapir.generic.Configuration
import sttp.tapir.SchemaType
import sttp.tapir.static.StaticErrorOutput.BadRequest

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

  it should "handle a simple ADT (no customizations)" in {
    // given
    import generic.auto._ // for Pickler auto-derivation
    case class MyCaseClass(fieldA: ErrorCode, fieldB: String)

    // when
    val derived = Pickler.derived[MyCaseClass]
    val jsonStr1 = derived.toCodec.encode(MyCaseClass(ErrorTimeout, "msg18"))
    val jsonStr2 = derived.toCodec.encode(MyCaseClass(CustomError("customErrMsg"), "msg18"))

    // then
    jsonStr1 shouldBe """{"fieldA":"sttp.tapir.json.ErrorTimeout","fieldB":"msg18"}"""
    jsonStr2 shouldBe """{"fieldA":{"$type":"sttp.tapir.json.CustomError","msg":"customErrMsg"},"fieldB":"msg18"}"""
  }

  it should "apply custom field name encoding to a simple ADT" in {
    // given
    import generic.auto._ // for Pickler auto-derivation
    given schemaConfig: Configuration = Configuration.default.copy(toEncodedName = _.toUpperCase())
    case class MyCaseClass(fieldA: ErrorCode, fieldB: String)

    // when
    val derived = Pickler.derived[MyCaseClass]
    val jsonStr1 = derived.toCodec.encode(MyCaseClass(ErrorTimeout, "msg18"))
    val jsonStr2 = derived.toCodec.encode(MyCaseClass(CustomError("customErrMsg"), "msg18"))

    // then
    jsonStr1 shouldBe """{"FIELDA":"sttp.tapir.json.ErrorTimeout","FIELDB":"msg18"}"""
    jsonStr2 shouldBe """{"FIELDA":{"$type":"sttp.tapir.json.CustomError","MSG":"customErrMsg"},"FIELDB":"msg18"}"""
  }

  it should "apply custom discriminator name to a simple ADT" in {
    // given
    import generic.auto._ // for Pickler auto-derivation
    given schemaConfig: Configuration = Configuration.default.withDiscriminator("kind")
    case class MyCaseClass(fieldA: ErrorCode, fieldB: String)
    val inputObj = MyCaseClass(CustomError("customErrMsg2"), "msg19")

    // when
    val derived = Pickler.derived[MyCaseClass]
    val jsonStr = derived.toCodec.encode(inputObj)

    // then
    jsonStr shouldBe """{"fieldA":{"kind":"sttp.tapir.json.CustomError","msg":"customErrMsg2"},"fieldB":"msg19"}"""
    derived.toCodec.decode(jsonStr) shouldBe Value(inputObj)
  }

  it should "Set discriminator value with oneOfUsingField" in {
    // given
    sealed trait Status:
      def code: Int

    case class StatusOk(oF: Int) extends Status {
      def code = 200
    }
    case class StatusBadRequest(bF: Int) extends Status {
      def code = 400
    }

    case class Response(status: Status)
    val picklerOk = Pickler.derived[StatusOk]
    val picklerBadRequest = Pickler.derived[StatusBadRequest]

    // when
    given statusPickler: Pickler[Status] = Pickler.oneOfUsingField[Status, Int](_.code, codeInt => s"code-$codeInt")(
      200 -> picklerOk,
      400 -> picklerBadRequest
    )
    val picklerResponse = Pickler.derived[Response]
    val obj = Response(StatusBadRequest(54))

    // then
    picklerResponse.toCodec.encode(obj) shouldBe """{"status":{"$type":"code-400","bF":54}}"""
  }
}

sealed trait ErrorCode

case object ErrorNotFound extends ErrorCode
case object ErrorTimeout extends ErrorCode
case class CustomError(msg: String) extends ErrorCode
