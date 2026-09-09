package sttp.tapir.json.pickler.next

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.DecodeResult.Value
import sttp.tapir.SchemaType.{SOption, SProduct}

import scala.compiletime.testing.typeCheckErrors

/** The coverage gaps plan §7.2 lists for the incumbent's suite: numeric values, string escaping, nested `Option`s, `Char`, tuples, and
  * decode failures. None of these had a single assertion before.
  */
class PicklerEdgeCasesTest extends AnyFlatSpec with Matchers {
  import EdgeFixtures.*

  private def roundTrip[T](pickler: Pickler[T], value: T, expectedJson: String): Unit = {
    val codec = pickler.toCodec
    codec.encode(value) shouldBe expectedJson
    val _ = codec.decode(expectedJson) shouldBe Value(value)
  }

  behavior of "numeric values"

  it should "write and read every numeric type, including extremes" in {
    roundTrip(
      Pickler.derived[Numbers],
      Numbers(
        Byte.MinValue,
        Short.MaxValue,
        Int.MinValue,
        Long.MaxValue,
        1.5f,
        2.25,
        BigInt("123456789012345678901234567890"),
        BigDecimal("1.000000000000000000001")
      ),
      """{"b":-128,"s":32767,"i":-2147483648,"l":9223372036854775807,"f":1.5,"d":2.25,"bi":123456789012345678901234567890,"bd":1.000000000000000000001}"""
    )
  }

  it should "write java.math.BigDecimal / BigInteger through the hand-written leaf codecs" in {
    roundTrip(
      Pickler.derived[JavaNumbers],
      JavaNumbers(new java.math.BigDecimal("12.50"), new java.math.BigInteger("-7")),
      """{"bd":12.50,"bi":-7}"""
    )
  }

  it should "reject a non-numeric value for a numeric field" in {
    val result = Pickler.derived[Numbers].toCodec.decode("""{"b":1,"s":1,"i":"one","l":1,"f":1,"d":1,"bi":1,"bd":1}""")
    result shouldBe a[DecodeResult.Error]
    val error = result.asInstanceOf[DecodeResult.Error].error.asInstanceOf[JsonDecodeException]
    error.errors.map(_.msg).mkString should include("illegal number")
  }

  behavior of "strings"

  it should "escape quotes, backslashes and control characters" in {
    roundTrip(Pickler.derived[Text], Text("a\"b\\c\n\t\u0001"), """{"s":"a\"b\\c\n\t\u0001"}""")
  }

  it should "pass non-ASCII and surrogate pairs through unescaped" in {
    roundTrip(Pickler.derived[Text], Text("zażółć 日本 😀"), """{"s":"zażółć 日本 😀"}""")
  }

  it should "read escaped non-ASCII" in {
    Pickler.derived[Text].toCodec.decode("""{"s":"\u0105\ud83d\ude00"}""") shouldBe Value(Text("ą😀"))
  }

  it should "write a Char as a one-character string, and document it as a string" in {
    val pickler = Pickler.derived[WithChar]
    roundTrip(pickler, WithChar('x'), """{"c":"x"}""")
    pickler.schema.schemaType.asInstanceOf[SProduct[WithChar]].fields.head.schema.schemaType shouldBe sttp.tapir.SchemaType.SString()
    pickler.toCodec.decode("""{"c":"xy"}""") shouldBe a[DecodeResult.Error]
  }

  behavior of "nested Options"

  it should "flatten Option[Option[X]] to a nullable X, as the schema documents" in {
    val pickler = Pickler.derived[WithOptOpt]
    val codec = pickler.toCodec
    codec.encode(WithOptOpt(Some(Some(1)))) shouldBe """{"o":1}"""
    codec.encode(WithOptOpt(Some(None))) shouldBe """{"o":null}"""
    codec.encode(WithOptOpt(None)) shouldBe """{}"""
    codec.decode("""{"o":1}""") shouldBe Value(WithOptOpt(Some(Some(1))))
    codec.decode("""{"o":null}""") shouldBe Value(WithOptOpt(None))
    codec.decode("""{}""") shouldBe Value(WithOptOpt(None))

    val fieldSchema = pickler.schema.schemaType.asInstanceOf[SProduct[WithOptOpt]].fields.head.schema
    fieldSchema.isOptional shouldBe true
    fieldSchema.schemaType shouldBe a[SOption[?, ?]]
  }

  it should "flatten Option[Option[X]] at the root too" in {
    val codec = Pickler.derived[Option[Option[String]]].toCodec
    codec.encode(Some(Some("a"))) shouldBe "\"a\""
    codec.decode("\"a\"") shouldBe Value(Some(Some("a")))
    codec.decode("null") shouldBe Value(None)
  }

  behavior of "tuples"

  it should "be rejected with an explanation, nested and at the root" in {
    typeCheckErrors("Pickler.derived[WithTuple]").map(_.message).mkString should include("tuples have no JSON schema")
    typeCheckErrors("Pickler.derived[(Int, String)]").map(_.message).mkString should include("tuples have no JSON schema")
  }

  behavior of "decode failures"

  it should "report malformed JSON as a DecodeResult.Error carrying a JsonDecodeException with jsoniter's message" in {
    val result = Pickler.derived[Text].toCodec.decode("not json")
    result shouldBe a[DecodeResult.Error]
    val DecodeResult.Error(original, error: JsonDecodeException) = result: @unchecked
    original shouldBe "not json"
    error.errors.map(_.msg).mkString should include("expected '{'")
    error.underlying shouldBe a[com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException]
  }

  it should "report a missing required field" in {
    val result = Pickler.derived[Numbers].toCodec.decode("""{"b":1}""")
    result shouldBe a[DecodeResult.Error]
    result.asInstanceOf[DecodeResult.Error].error.asInstanceOf[JsonDecodeException].errors.map(_.msg).mkString should include(
      "missing required field"
    )
  }

  it should "report an unknown discriminator value" in {
    val result = Pickler.derived[Fixtures.Status].toCodec.decode("""{"$type":"Nope"}""")
    result shouldBe a[DecodeResult.Error]
    result.asInstanceOf[DecodeResult.Error].error.asInstanceOf[JsonDecodeException].errors.map(_.msg).mkString should include(
      "illegal value of discriminator"
    )
  }

  it should "report an unknown enumeration value" in {
    Pickler.derived[Fixtures.ColorEnum].toCodec.decode("\"Mauve\"") shouldBe a[DecodeResult.Error]
    Pickler
      .derivedEnumeration[Fixtures.ColorEnum]
      .customStringBased(_.ordinal.toString)
      .toCodec
      .decode("\"9\"") shouldBe a[DecodeResult.Error]
  }

  it should "report trailing garbage" in {
    Pickler.derived[Text].toCodec.decode("""{"s":"a"} extra""") shouldBe a[DecodeResult.Error]
  }
}

object EdgeFixtures {
  case class Numbers(b: Byte, s: Short, i: Int, l: Long, f: Float, d: Double, bi: BigInt, bd: BigDecimal)
  case class JavaNumbers(bd: java.math.BigDecimal, bi: java.math.BigInteger)
  case class Text(s: String)
  case class WithChar(c: Char)
  case class WithOptOpt(o: Option[Option[Int]])
  case class WithTuple(t: (Int, String))
}
