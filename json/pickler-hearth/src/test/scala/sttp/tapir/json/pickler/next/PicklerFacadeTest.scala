package sttp.tapir.json.pickler.next

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.SchemaType.{SCoproduct, SOpenProduct, SProduct, SString}
import sttp.tapir.{Schema, Validator}

import java.util.UUID
import scala.compiletime.testing.typeCheckErrors

/** The public surface of `Pickler` beyond `derived` (plan §1.1, Phase 4b): `derivedEnumeration`, `picklerForMap`, `asOption` / `asIterable`
  * / `asArray`, the `JsonCodec` bridge, and `Either`.
  *
  * Expected JSON strings come from the uPickle-based module's tests where it has an equivalent (`PicklerEnumTest`, `PicklerBasicTest`); the
  * `Either` encoding is the one intentional divergence (plan §5.3).
  */
class PicklerFacadeTest extends AnyFlatSpec with Matchers {
  import CodecFixtures.*
  import FacadeFixtures.*

  private def roundTrip[T](pickler: Pickler[T], value: T, expectedJson: String): Unit = {
    val codec = pickler.toCodec
    codec.encode(value) shouldBe expectedJson
    val _ = codec.decode(expectedJson) shouldBe Value(value)
  }

  behavior of "derivedEnumeration"

  it should "encode with a custom function (ordinal), for a nested enum" in {
    // PicklerEnumTest L45
    given Pickler[ColorEnum] = Pickler.derivedEnumeration[ColorEnum].customStringBased(_.ordinal.toString)
    roundTrip(Pickler.derived[Response], Response(ColorEnum.Pink, "pink!!"), """{"color":"1","description":"pink!!"}""")
  }

  it should "encode with a custom function over a parameterised enum case" in {
    // PicklerEnumTest L62
    given picklerColorEnum: Pickler[RichColorEnum] =
      Pickler.derivedEnumeration[RichColorEnum].customStringBased(enumValue => s"color-number-${enumValue.code}")
    roundTrip(Pickler.derived[RichColorResponse], RichColorResponse(RichColorEnum.Cyan), """{"color":"color-number-3"}""")
  }

  it should "document the custom encoding in the schema's enumeration validator" in {
    val pickler = Pickler.derivedEnumeration[ColorEnum].customStringBased(_.ordinal.toString)
    pickler.schema.schemaType shouldBe SString()
    pickler.schema.name shouldBe Pickler.derived[ColorEnum].schema.name
    val validator = pickler.schema.validator.asInstanceOf[Validator.Enumeration[ColorEnum]]
    validator.possibleValues shouldBe List(ColorEnum.Green, ColorEnum.Pink)
    validator.encode.flatMap(_(ColorEnum.Pink)) shouldBe Some("1")
  }

  it should "be equivalent to Pickler.derived when defaultStringBased" in {
    val viaBuilder = Pickler.derivedEnumeration[ColorEnum].defaultStringBased
    val viaDerived = Pickler.derived[ColorEnum]
    // Two expansions, two validator lambdas: compare structurally rather than with `shouldBe`.
    viaBuilder.schema.copy(validator = Validator.pass) shouldBe viaDerived.schema.copy(validator = Validator.pass)
    val encode = viaBuilder.schema.validator.asInstanceOf[Validator.Enumeration[ColorEnum]].encode.get
    ColorEnum.values.toList.map(encode) shouldBe ColorEnum.values.toList.map(
      viaDerived.schema.validator.asInstanceOf[Validator.Enumeration[ColorEnum]].encode.get
    )
    viaBuilder.toCodec.encode(ColorEnum.Pink) shouldBe viaDerived.toCodec.encode(ColorEnum.Pink)
    viaBuilder.toCodec.encode(ColorEnum.Pink) shouldBe "\"Pink\""
  }

  it should "work for a sealed hierarchy of objects, not only Scala 3 enums" in {
    given Pickler[SealedVariant] = Pickler.derivedEnumeration[SealedVariant].customStringBased(_.toString.toLowerCase)
    roundTrip(Pickler.derived[SealedVariantContainer], SealedVariantContainer(VariantB), """{"v":"variantb"}""")
  }

  it should "reject an unknown string on decode" in {
    val codec = Pickler.derivedEnumeration[ColorEnum].customStringBased(_.ordinal.toString).toCodec
    codec.decode("\"7\"") should not be a[Value[?]]
  }

  it should "fail fast when the encoding is not injective" in {
    an[IllegalArgumentException] should be thrownBy Pickler.derivedEnumeration[ColorEnum].customStringBased(_ => "same")
  }

  it should "reject a hierarchy with non-singleton cases" in {
    typeCheckErrors("Pickler.derivedEnumeration[ErrorCode]").map(_.message).mkString should include("cases with fields: ")
    typeCheckErrors("Pickler.derivedEnumeration[FlatClass]").map(_.message).mkString should include("is not one")
  }

  it should "reject oneOfUsingField for enums" in {
    // PicklerEnumTest L92. The incumbent built picklers for the individual cases first; jsoniter cannot
    // (`JsonCodecMaker.make[RichColorEnum.Cyan.type]` fails to type-check for a parameterised enum's case), so the
    // children here are only typed, which is all the rejection needs.
    val errors = typeCheckErrors("""
      given picklerRichColor: Pickler[RichColorEnum] =
        Pickler.oneOfUsingField[RichColorEnum, Int](_.code, codeInt => s"code-$codeInt")(
          3 -> (null: Pickler[RichColorEnum.Cyan.type]),
          18 -> (null: Pickler[RichColorEnum.Magenta.type])
        )""").map(_.message).mkString
    errors should include("derivedEnumeration")
    // A case object on its own, outside its hierarchy, is an empty object -- which is what its schema (an `SProduct`
    // with no fields) documents. Only as a member of an all-singleton hierarchy is it a bare string.
    Pickler.derived[VariantA.type].toCodec.encode(VariantA) shouldBe "{}"
    Pickler.derived[VariantA.type].schema.schemaType shouldBe SProduct[VariantA.type](Nil)
  }

  behavior of "picklerForMap"

  it should "derive picklers for Map with non-String key" in {
    // PicklerBasicTest L224
    import sttp.tapir.json.pickler.next.generic.auto.*
    given picklerMap: Pickler[Map[UUID, SimpleTestResult]] = Pickler.picklerForMap(_.toString, UUID.fromString)
    val pickler = Pickler.derived[ClassWithMapCustomKey]
    val uuid1: UUID = UUID.randomUUID()
    val uuid2: UUID = UUID.randomUUID()
    val obj = ClassWithMapCustomKey(Map((uuid1, SimpleTestResult("result3")), (uuid2, SimpleTestResult("result4"))))

    roundTrip(pickler, obj, s"""{"field":{"$uuid1":{"msg":"result3"},"$uuid2":{"msg":"result4"}}}""")
    {
      import sttp.tapir.generic.auto.*
      picklerMap.schema shouldBe Schema.schemaForMap[UUID, SimpleTestResult](_.toString)
      given Schema[Map[UUID, SimpleTestResult]] = picklerMap.schema
      pickler.schema shouldBe Schema.derived[ClassWithMapCustomKey]
    }
  }

  it should "decode an empty map and reject a malformed one" in {
    val codec = Pickler.picklerForMap[Int, String](_.toString, _.toInt)(using Pickler.derived[String]).toCodec
    codec.decode("{}") shouldBe Value(Map.empty[Int, String])
    codec.encode(Map(1 -> "a", 2 -> "b")) shouldBe """{"1":"a","2":"b"}"""
    codec.decode("""{"x":"a"}""") should not be a[Value[?]]
  }

  it should "still require picklerForMap for a non-String key in structural derivation" in {
    assertDoesNotCompile("""Pickler.derived[ClassWithMapCustomKey]""")
  }

  behavior of "asOption / asIterable / asArray"

  it should "wrap a pickler in Option, with null for None" in {
    val pickler = Pickler.derived[FlatClass].asOption
    pickler.schema shouldBe Pickler.derived[FlatClass].schema.asOption
    roundTrip(pickler, Some(FlatClass(1, "a")), """{"fieldA":1,"fieldB":"a"}""")
    // The jsoniter codec writes `null`; tapir's `Codec.json` then maps `None` of an optional schema to an empty body.
    com.github.plokhotnyuk.jsoniter_scala.core.writeToString(None: Option[FlatClass])(using pickler.codec) shouldBe "null"
    pickler.toCodec.encode(None) shouldBe ""
    pickler.toCodec.decode("") shouldBe Value(None)
    pickler.toCodec.decode("null") shouldBe Value(None)
  }

  it should "wrap a pickler in a collection" in {
    val pickler = Pickler.derived[FlatClass].asIterable[Vector]
    pickler.schema shouldBe Pickler.derived[FlatClass].schema.asIterable[Vector]
    roundTrip(pickler, Vector(FlatClass(1, "a"), FlatClass(2, "b")), """[{"fieldA":1,"fieldB":"a"},{"fieldA":2,"fieldB":"b"}]""")
    roundTrip(pickler, Vector.empty, "[]")
  }

  it should "wrap a pickler in an Array" in {
    val pickler = Pickler.derived[Int].asArray
    pickler.schema shouldBe Pickler.derived[Int].schema.asArray
    pickler.toCodec.encode(Array(1, 2, 3)) shouldBe "[1,2,3]"
    pickler.toCodec.decode("[1,2,3]").map(_.toList) shouldBe Value(List(1, 2, 3))
  }

  it should "agree with structural derivation of the same wrapper" in {
    val wrapped = Pickler.derived[FlatClass].asIterable[List]
    val structural = Pickler.derived[List[FlatClass]]
    val value = List(FlatClass(1, "a"))
    wrapped.toCodec.encode(value) shouldBe structural.toCodec.encode(value)
    wrapped.schema.schemaType shouldBe structural.schema.schemaType
  }

  behavior of "the JsonCodec bridge"

  it should "make jsonBody resolve from a given Pickler" in {
    given Pickler[FlatClass] = Pickler.derived[FlatClass]
    val body = jsonBody[FlatClass]
    body.codec.encode(FlatClass(1, "a")) shouldBe """{"fieldA":1,"fieldB":"a"}"""
    body.codec.schema shouldBe summon[Pickler[FlatClass]].schema
    summon[sttp.tapir.Codec.JsonCodec[FlatClass]].encode(FlatClass(1, "a")) shouldBe """{"fieldA":1,"fieldB":"a"}"""
  }

  it should "make core's customCodecJsonBody resolve too, through the implicit codec" in {
    import sttp.tapir.json.pickler.next.generic.auto.*
    val body = sttp.tapir.customCodecJsonBody[TopClass]
    body.codec.encode(TopClass("a", InnerClass(1))) shouldBe """{"fieldA":"a","fieldB":{"fieldA11":1}}"""
    jsonQuery[FlatClass]("q").codec.encode(FlatClass(1, "a")) shouldBe List("""{"fieldA":1,"fieldB":"a"}""")
  }

  behavior of "Either"

  it should "encode Either fields untagged, as tapir core's Codec.eitherRight does" in {
    // PicklerBasicTest L187, with the encoding changed on purpose: uPickle wrote `[0,"err1"]` / `[1,{...}]`.
    val pickler = Pickler.derived[ClassWithEither]
    roundTrip(pickler, ClassWithEither("fieldA 1", Left("err1")), """{"fieldA":"fieldA 1","fieldB":"err1"}""")
    roundTrip(
      pickler,
      ClassWithEither("fieldA 2", Right(SimpleTestResult("it is fine"))),
      """{"fieldA":"fieldA 2","fieldB":{"msg":"it is fine"}}"""
    )
    {
      import sttp.tapir.generic.auto.*
      pickler.schema shouldBe Schema.derived[ClassWithEither]
    }
  }

  it should "document Either as an untagged coproduct of both sides" in {
    val schema = Pickler.derived[ClassWithEither].schema.schemaType.asInstanceOf[SProduct[ClassWithEither]].fields(1).schema
    val coproduct = schema.schemaType.asInstanceOf[SCoproduct[?]]
    coproduct.discriminator shouldBe None
    coproduct.subtypes.map(_.schemaType) shouldBe List(SString(), Pickler.derived[SimpleTestResult].schema.schemaType)
  }

  it should "derive Either as a root, with structural sides" in {
    val pickler = Pickler.derived[Either[List[Int], Status]]
    roundTrip(pickler, Left(List(1, 2)), "[1,2]")
    roundTrip(pickler, Right(StatusOk(200)), """{"$type":"StatusOk","oF":200}""")
    roundTrip(pickler, Right(StatusInternalError), """{"$type":"StatusInternalError"}""")
  }

  it should "prefer Right when both sides accept the value" in {
    Pickler.derived[Either[String, String]].toCodec.decode("\"x\"") shouldBe Value(Right("x"))
  }

  it should "honour a user pickler for one side" in {
    given Pickler[SimpleTestResult] =
      Pickler.derived[SimpleTestResult](using PicklerConfiguration.default.withScreamingSnakeCaseMemberNames)
    roundTrip(
      Pickler.derived[ClassWithEither],
      ClassWithEither("a", Right(SimpleTestResult("r"))),
      """{"fieldA":"a","fieldB":{"MSG":"r"}}"""
    )
  }
}

object FacadeFixtures {
  import CodecFixtures.SimpleTestResult
  case class ClassWithEither(fieldA: String, fieldB: Either[String, SimpleTestResult])
  case class ClassWithMapCustomKey(field: Map[UUID, SimpleTestResult])
}
