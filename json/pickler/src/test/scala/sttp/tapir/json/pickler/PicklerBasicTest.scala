package sttp.tapir.json.pickler

import _root_.upickle.{default => udefault}
import magnolia1.SealedTrait
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.Schema.annotations.{default, encodedName}
import sttp.tapir.{Schema, SchemaType}
import upickle.AttributeTagged
import upickle.core.{ObjVisitor, Visitor}

import java.util.{TimeZone, UUID}

import Fixtures.*

class PicklerBasicTest extends AnyFlatSpec with Matchers {

  behavior of "Pickler derivation"

  it should "build from an existing Schema and upickle.default.ReadWriter" in {
    // given schema and reader / writer in scope
    given Schema[FlatClass] = Schema.derived[FlatClass]
    given rw: _root_.upickle.default.ReadWriter[FlatClass] = _root_.upickle.default.macroRW[FlatClass]

    // when
    val derived = Pickler.derived[FlatClass]
    val obj = derived.toCodec.decode("""{"fieldA": 654, "fieldB": "field_b_value"}""")

    // then
    obj shouldBe Value(FlatClass(654, "field_b_value"))
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

  object CustomPickle extends AttributeTagged {
    def getReader: udefault.Reader[FlatClass] = udefault.macroR[FlatClass]
    def getWriter: this.Writer[FlatClass] = new Writer[FlatClass] {
      override def write0[V](out: Visitor[?, V], v: FlatClass): V = out.visitString(s"custom-${v.fieldA}", 1)
    }
  }

  it should "work with provided own readers and writers" in {
    given Schema[FlatClass] = Schema.derived[FlatClass]
    given udefault.Reader[FlatClass] = CustomPickle.getReader
    given CustomPickle.Writer[FlatClass] = CustomPickle.getWriter

    Pickler.derived[FlatClass].toCodec.encode(FlatClass(5, "txt")) shouldBe """"custom-5""""
  }

  it should "work with provider uPickle ReadWriter on a non-mirrored type" in {
    given Schema[TimeZone] = Schema(SchemaType.SString())
    given udefault.ReadWriter[TimeZone] = upickle.default.readwriter[String].bimap[TimeZone](_.getID, TimeZone.getTimeZone)
    val ptz: Pickler[TimeZone] = Pickler.derived

    ptz.toCodec.encode(TimeZone.getTimeZone("America/Los_Angeles")) shouldBe "\"America/Los_Angeles\""
  }

  it should "fail to derive a Pickler when there's a Schema but missing ReadWriter" in {
    assertDoesNotCompile("""
      given givenSchemaForCc: Schema[FlatClass] = Schema.derived[FlatClass]
      Pickler.derived[FlatClass]
    """)
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

    // then
    jsonStr1 shouldBe """{"fieldA":"fieldA 1","fieldB":[0,"err1"]}"""
    jsonStr2 shouldBe """{"fieldA":"fieldA 2","fieldB":[1,{"msg":"it is fine"}]}"""
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

    // then
    jsonStr shouldBe """{"field":{"keyB":{"msg":"result1"},"keyA":{"msg":"result2"}}}"""
    {
      import sttp.tapir.generic.auto.*
      pickler.schema shouldBe Schema.derived[ClassWithMap]
    }
  }

  it should "derive picklers for Map with non-String key" in {
    import generic.auto.* // for Pickler auto-derivation

    // when
    given picklerMap: Pickler[Map[UUID, SimpleTestResult]] = Pickler.picklerForMap(_.toString)
    val pickler = Pickler.derived[ClassWithMapCustomKey]
    val uuid1: UUID = UUID.randomUUID()
    val uuid2: UUID = UUID.randomUUID()
    val codec = pickler.toCodec
    val obj = ClassWithMapCustomKey(Map((uuid1, SimpleTestResult("result3")), (uuid2, SimpleTestResult("result4"))))
    val jsonStr = codec.encode(obj)

    // then
    jsonStr shouldBe s"""{"field":{"$uuid1":{"msg":"result3"},"$uuid2":{"msg":"result4"}}}"""
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
