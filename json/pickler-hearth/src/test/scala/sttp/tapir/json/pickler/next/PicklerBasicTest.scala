package sttp.tapir.json.pickler.next

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.{Schema, SchemaType}

import java.util.{TimeZone, UUID}

import Fixtures.*

/** Port of the uPickle-based module's `PicklerBasicTest`. Changes from the original, each marked inline:
  *   - the three tests that embedded uPickle types (`macroRW`, `AttributeTagged`/`Visitor`, `readwriter.bimap`) now use
  *     `Pickler.fromSchemaAndCodec` with a jsoniter `JsonValueCodec`;
  *   - `Either` is untagged (plan §5.3);
  *   - `picklerForMap` takes the key parser as well (D6.2);
  *   - `Map` assertions no longer depend on iteration order (plan §7.2).
  */
class PicklerBasicTest extends AnyFlatSpec with Matchers {

  behavior of "Pickler derivation"

  it should "build from an existing Schema and JsonValueCodec" in {
    // was: "build from an existing Schema and upickle.default.ReadWriter", with the two picked up implicitly
    val codec: JsonValueCodec[FlatClass] = new JsonValueCodec[FlatClass] {
      def nullValue: FlatClass = null
      def decodeValue(in: JsonReader, default: FlatClass): FlatClass = {
        val s = in.readString(null)
        val Array(a, b) = s.split('|')
        FlatClass(a.toInt, b)
      }
      def encodeValue(x: FlatClass, out: JsonWriter): Unit = out.writeVal(s"${x.fieldA}|${x.fieldB}")
    }
    val pickler = Pickler.fromSchemaAndCodec(Schema.string[FlatClass], codec)

    pickler.toCodec.encode(FlatClass(654, "field_b_value")) shouldBe "\"654|field_b_value\""
    pickler.toCodec.decode("\"654|field_b_value\"") shouldBe Value(FlatClass(654, "field_b_value"))
    pickler.schema.schemaType shouldBe SchemaType.SString()
  }

  it should "work with `derives`" in {
    // when
    val bookPickler: Pickler[Book] = summon[Pickler[Book]]
    val bookShelfPickler: Pickler[BookShelf] = summon[Pickler[BookShelf]]

    // then
    bookPickler.toCodec.encode(Book("John", "Hello")) shouldBe """{"author":"John","title":"Hello"}"""
    bookShelfPickler.toCodec.encode(BookShelf(List(Book("Alice", "Goodbye")))) shouldBe
      """{"books":[{"author":"Alice","title":"Goodbye"}]}"""
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
    import generic.auto.* // for Pickler auto-derivation

    // when
    val derived = Pickler.derived[TopClass]
    val jsonStr = derived.toCodec.encode(TopClass("field_a_value", InnerClass(7954)))
    val inputJson = """{"fieldA":"field_a_value_2","fieldB":{"fieldA11":-321}}"""
    val resultObj = derived.toCodec.decode(inputJson)

    // then
    jsonStr shouldBe """{"fieldA":"field_a_value","fieldB":{"fieldA11":7954}}"""
    resultObj shouldBe Value(TopClass("field_a_value_2", InnerClass(-321)))
  }

  it should "work with a user-provided codec for a nested type" in {
    // was: "work with provided own readers and writers" (a custom uPickle Writer for FlatClass)
    val customCodec: JsonValueCodec[FlatClass] = new JsonValueCodec[FlatClass] {
      def nullValue: FlatClass = null
      def decodeValue(in: JsonReader, default: FlatClass): FlatClass = FlatClass(in.readString(null).stripPrefix("custom-").toInt, "")
      def encodeValue(x: FlatClass, out: JsonWriter): Unit = out.writeVal(s"custom-${x.fieldA}")
    }
    given Pickler[FlatClass] = Pickler.fromSchemaAndCodec(Schema.string[FlatClass], customCodec)
    case class Wrapper(f: FlatClass)

    val pickler = Pickler.derived[Wrapper]
    pickler.toCodec.encode(Wrapper(FlatClass(5, "txt"))) shouldBe """{"f":"custom-5"}"""
    pickler.toCodec.decode("""{"f":"custom-7"}""") shouldBe Value(Wrapper(FlatClass(7, "")))
    // the schema follows the pickler too
    pickler.schema.schemaType.asInstanceOf[SchemaType.SProduct[Wrapper]].fields.head.schema.schemaType shouldBe SchemaType.SString()
  }

  it should "work with a user-provided codec for a non-structural type" in {
    // was: "work with provider uPickle ReadWriter on a non-mirrored type" (TimeZone via readwriter[String].bimap)
    val tzCodec: JsonValueCodec[TimeZone] = new JsonValueCodec[TimeZone] {
      def nullValue: TimeZone = null
      def decodeValue(in: JsonReader, default: TimeZone): TimeZone = TimeZone.getTimeZone(in.readString(null))
      def encodeValue(x: TimeZone, out: JsonWriter): Unit = out.writeVal(x.getID)
    }
    given ptz: Pickler[TimeZone] = Pickler.fromSchemaAndCodec(Schema(SchemaType.SString()), tzCodec)

    ptz.toCodec.encode(TimeZone.getTimeZone("America/Los_Angeles")) shouldBe "\"America/Los_Angeles\""

    // and, nested, jsoniter picks the codec up through the given pickler
    case class Meeting(tz: TimeZone)
    Pickler.derived[Meeting].toCodec.encode(Meeting(TimeZone.getTimeZone("UTC"))) shouldBe """{"tz":"UTC"}"""
  }

  it should "derive structurally even when a Schema for the class is in scope" in {
    // was: "fail to derive a Pickler when there's a Schema but missing ReadWriter". A bare Schema is not an override
    // here (a Pickler is -- see the test above); a bare JsonValueCodec for a nested class is refused (CodecDerivationTest).
    given givenSchemaForCc: Schema[FlatClass] = Schema.string[FlatClass]
    Pickler.derived[FlatClass].toCodec.encode(FlatClass(1, "a")) shouldBe """{"fieldA":1,"fieldB":"a"}"""
    Pickler.derived[FlatClass].schema.schemaType shouldBe a[SchemaType.SProduct[?]]
  }

  it should "derive picklers for Option fields" in {
    import generic.auto.* // for Pickler auto-derivation

    // when
    val pickler1 = Pickler.derived[FlatClassWithOption]
    val pickler2 = Pickler.derived[NestedClassWithOption]
    val jsonStr1 = pickler1.toCodec.encode(FlatClassWithOption("fieldA value", Some(-4018), true))
    val jsonStr2 = pickler2.toCodec.encode(NestedClassWithOption(Some(FlatClassWithOption("fieldA value2", None, true))))
    val jsonStr3 = pickler1.toCodec.encode(FlatClassWithOption("fieldA value", None, true))

    // then
    {
      given derivedFlatClassSchema: Schema[FlatClassWithOption] = Schema.derived[FlatClassWithOption]
      pickler1.schema shouldBe derivedFlatClassSchema
      pickler2.schema shouldBe Schema.derived[NestedClassWithOption]
      jsonStr1 shouldBe """{"fieldA":"fieldA value","fieldB":-4018,"fieldC":true}"""
      jsonStr2 shouldBe """{"innerField":{"fieldA":"fieldA value2","fieldC":true}}"""
      jsonStr3 shouldBe """{"fieldA":"fieldA value","fieldC":true}"""
      pickler1.toCodec.decode("""{"fieldA":"fieldA value3","fieldC":true}""") shouldBe Value(
        FlatClassWithOption("fieldA value3", None, true)
      )
      pickler1.toCodec.decode("""{"fieldA":"fieldA value4", "fieldB": null, "fieldC": true}""") shouldBe Value(
        FlatClassWithOption("fieldA value4", None, true)
      )
    }
  }

  it should "serialize Options to nulls if transientNone = false" in {
    import generic.auto.* // for Pickler auto-derivation

    // when
    given PicklerConfiguration = PicklerConfiguration.default.withTransientNone(false)
    val pickler = Pickler.derived[FlatClassWithOption]
    val jsonStr1 = pickler.toCodec.encode(FlatClassWithOption("fieldA value", Some(-2545), true))
    val jsonStr2 = pickler.toCodec.encode(FlatClassWithOption("fieldA value2", None, true))

    // then
    {
      jsonStr1 shouldBe """{"fieldA":"fieldA value","fieldB":-2545,"fieldC":true}"""
      jsonStr2 shouldBe """{"fieldA":"fieldA value2","fieldB":null,"fieldC":true}"""
    }
  }

  it should "derive picklers for List fields" in {
    import generic.auto.* // for Pickler auto-derivation

    // when
    val pickler1 = Pickler.derived[FlatClassWithList]
    val codec1 = pickler1.toCodec
    val pickler2 = Pickler.derived[NestedClassWithList]
    val codec2 = pickler2.toCodec
    val obj1 = FlatClassWithList("fieldA value", List(64, -5))
    val obj2 = NestedClassWithList(List(FlatClassWithList("a2", Nil), FlatClassWithList("a3", List(8, 9))))
    val jsonStr1 = codec1.encode(obj1)
    val jsonStr2 = codec2.encode(obj2)

    // then
    jsonStr1 shouldBe """{"fieldA":"fieldA value","fieldB":[64,-5]}"""
    codec1.decode(jsonStr1) shouldBe Value(obj1)
    jsonStr2 shouldBe """{"innerField":[{"fieldA":"a2","fieldB":[]},{"fieldA":"a3","fieldB":[8,9]}]}"""
    codec2.decode(jsonStr2) shouldBe Value(obj2)
    {
      import sttp.tapir.generic.auto.*
      pickler2.schema shouldBe Schema.derived[NestedClassWithList]
    }
  }

  it should "derive picklers for Array fields" in {
    import generic.auto.* // for Pickler auto-derivation

    // when
    val pickler1 = Pickler.derived[FlatClassWithArray]
    val codec1 = pickler1.toCodec
    val pickler2 = Pickler.derived[NestedClassWithArray]
    val codec2 = pickler2.toCodec
    val obj1 = FlatClassWithArray("fieldA value 50", Array(8, 8, 107))
    val obj2 = NestedClassWithArray(Array(FlatClassWithArray("a2", Array()), FlatClassWithArray("a3", Array(-10))))
    val jsonStr1 = codec1.encode(obj1)
    val jsonStr2 = codec2.encode(obj2)

    // then
    jsonStr1 shouldBe """{"fieldA":"fieldA value 50","fieldB":[8,8,107]}"""
    jsonStr2 shouldBe """{"innerField":[{"fieldA":"a2","fieldB":[]},{"fieldA":"a3","fieldB":[-10]}]}"""
    {
      import sttp.tapir.generic.auto.*
      pickler2.schema shouldBe Schema.derived[NestedClassWithArray]
    }
  }

  it should "derive picklers for Either fields" in {
    import generic.auto.* // for Pickler auto-derivation

    // when
    val pickler = Pickler.derived[ClassWithEither]
    val codec = pickler.toCodec
    val obj1 = ClassWithEither("fieldA 1", Left("err1"))
    val obj2 = ClassWithEither("fieldA 2", Right(SimpleTestResult("it is fine")))
    val jsonStr1 = codec.encode(obj1)
    val jsonStr2 = codec.encode(obj2)

    // then -- untagged (plan §5.3); the uPickle-based module wrote [0,"err1"] / [1,{"msg":"it is fine"}]
    jsonStr1 shouldBe """{"fieldA":"fieldA 1","fieldB":"err1"}"""
    jsonStr2 shouldBe """{"fieldA":"fieldA 2","fieldB":{"msg":"it is fine"}}"""
    codec.decode(jsonStr1) shouldBe Value(obj1)
    codec.decode(jsonStr2) shouldBe Value(obj2)
    {
      import sttp.tapir.generic.auto.*
      pickler.schema shouldBe Schema.derived[ClassWithEither]
    }
  }

  it should "derive picklers for Map with String key" in {
    import generic.auto.* // for Pickler auto-derivation

    // when
    val pickler = Pickler.derived[ClassWithMap]
    val codec = pickler.toCodec
    val obj = ClassWithMap(Map(("keyB", SimpleTestResult("result1")), ("keyA", SimpleTestResult("result2"))))
    val jsonStr = codec.encode(obj)

    // then -- order-insensitive (plan §7.2)
    jsonStr should (be("""{"field":{"keyB":{"msg":"result1"},"keyA":{"msg":"result2"}}}""") or
      be("""{"field":{"keyA":{"msg":"result2"},"keyB":{"msg":"result1"}}}"""))
    codec.decode(jsonStr) shouldBe Value(obj)
    {
      import sttp.tapir.generic.auto.*
      pickler.schema shouldBe Schema.derived[ClassWithMap]
    }
  }

  it should "derive picklers for Map with non-String key" in {
    import generic.auto.* // for Pickler auto-derivation

    // when -- `picklerForMap` also takes the key parser (D6.2)
    given picklerMap: Pickler[Map[UUID, SimpleTestResult]] = Pickler.picklerForMap(_.toString, UUID.fromString)
    val pickler = Pickler.derived[ClassWithMapCustomKey]
    // fixed rather than random: `UUID.randomUUID()` has no Scala.js implementation (SecureRandom)
    val uuid1: UUID = UUID.fromString("2c2b1cf3-5f2e-4a0b-9d3a-7d1a4e0b1c01")
    val uuid2: UUID = UUID.fromString("9e0e3b8d-1d51-4c3f-8f0e-6a2c1b7d2f02")
    val codec = pickler.toCodec
    val obj = ClassWithMapCustomKey(Map((uuid1, SimpleTestResult("result3")), (uuid2, SimpleTestResult("result4"))))
    val jsonStr = codec.encode(obj)

    // then -- order-insensitive (plan §7.2)
    jsonStr should (be(s"""{"field":{"$uuid1":{"msg":"result3"},"$uuid2":{"msg":"result4"}}}""") or
      be(s"""{"field":{"$uuid2":{"msg":"result4"},"$uuid1":{"msg":"result3"}}}"""))
    codec.decode(jsonStr) shouldBe Value(obj)
    {
      import sttp.tapir.generic.auto.*
      picklerMap.schema shouldBe Schema.schemaForMap[UUID, SimpleTestResult](_.toString)
      given Schema[Map[UUID, SimpleTestResult]] = picklerMap.schema
      pickler.schema shouldBe Schema.derived[ClassWithMapCustomKey]
    }
  }

  it should "handle value classes" in {
    // when
    val pickler = Pickler.derived[ClassWithValues]
    val codec = pickler.toCodec
    val inputObj = ClassWithValues(UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")), UserName("Alan"), age = 65)
    val encoded = codec.encode(inputObj)

    // then
    encoded shouldBe """{"id":"550e8400-e29b-41d4-a716-446655440000","name":"Alan","age":65}"""
    codec.decode(encoded) shouldBe Value(inputObj)
  }
}
