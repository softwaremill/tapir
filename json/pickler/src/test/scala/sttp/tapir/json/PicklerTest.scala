package sttp.tapir.json

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.Schema
import sttp.tapir.generic.Configuration
import sttp.tapir.SchemaType
import sttp.tapir.Schema.annotations.encodedName
import sttp.tapir.Schema.annotations.default
import java.util.UUID

class PicklerTest extends AnyFlatSpec with Matchers {
  behavior of "Pickler derivation"

  case class FlatClass(fieldA: Int, fieldB: String)
  case class TopClass(fieldA: String, fieldB: InnerClass)
  case class InnerClass(fieldA11: Int)

  case class TopClass2(fieldA: String, fieldB: AnnotatedInnerClass)
  case class AnnotatedInnerClass(@encodedName("encoded_field-a") fieldA: String, fieldB: String)

  it should "build from an existing Schema and upickle.default.ReadWriter" in {
    // given schema and reader / writer in scope
    given givenSchemaForCc: Schema[FlatClass] = Schema.derived[FlatClass]
    given rw: _root_.upickle.default.ReadWriter[FlatClass] = _root_.upickle.default.macroRW[FlatClass]

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

  it should "fail to derive a Pickler when there's a Schema but missing ReadWriter" in {
    assertDoesNotCompile("""
      given givenSchemaForCc: Schema[FlatClass] = Schema.derived[FlatClass]
      Pickler.derived[FlatClass]
    """)
  }

  it should "use encodedName from configuration" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    given schemaConfig: Configuration = Configuration.default.withSnakeCaseMemberNames

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
    given schemaConfig: Configuration = Configuration.default.withSnakeCaseMemberNames

    // when
    val derived = Pickler.derived[TopClass]
    val jsonStr = """{"field_a":"field_a_value","field_b":{"field_a11":7954}}"""
    val obj = derived.toCodec.decode(jsonStr)

    // then
    obj shouldBe Value(TopClass("field_a_value", InnerClass(7954)))
  }

  it should "derive picklers for Option fields" in {
    import generic.auto.* // for Pickler auto-derivation

    // when
    val pickler1 = Pickler.derived[FlatClassWithOption]
    val pickler2 = Pickler.derived[NestedClassWithOption]
    val jsonStr1 = pickler1.toCodec.encode(FlatClassWithOption("fieldA value", Some(-4018)))
    val jsonStr2 = pickler2.toCodec.encode(NestedClassWithOption(Some(FlatClassWithOption("fieldA value2", Some(-3014)))))
    val jsonStr3 = pickler1.toCodec.encode(FlatClassWithOption("fieldA value", None))

    // then
    {
      given derivedFlatClassSchema: Schema[FlatClassWithOption] = Schema.derived[FlatClassWithOption]
      pickler1.schema shouldBe derivedFlatClassSchema
      pickler2.schema shouldBe Schema.derived[NestedClassWithOption]
      jsonStr1 shouldBe """{"fieldA":"fieldA value","fieldB":-4018}"""
      jsonStr2 shouldBe """{"innerField":{"fieldA":"fieldA value2","fieldB":-3014}}"""
      jsonStr3 shouldBe """{"fieldA":"fieldA value","fieldB":null}"""
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
  it should "handle a simple ADT (no customizations)" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    case class MyCaseClass(fieldA: ErrorCode, fieldB: String)

    // when
    val derived = Pickler.derived[MyCaseClass]
    val jsonStr1 = derived.toCodec.encode(MyCaseClass(ErrorTimeout, "msg18"))
    val jsonStr2 = derived.toCodec.encode(MyCaseClass(CustomError("customErrMsg"), "msg18"))

    // then
    jsonStr1 shouldBe """{"fieldA":{"$type":"sttp.tapir.json.ErrorTimeout"},"fieldB":"msg18"}"""
    jsonStr2 shouldBe """{"fieldA":{"$type":"sttp.tapir.json.CustomError","msg":"customErrMsg"},"fieldB":"msg18"}"""
  }

  it should "apply custom field name encoding to a simple ADT" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    given schemaConfig: Configuration = Configuration.default.copy(toEncodedName = _.toUpperCase())
    case class MyCaseClass(fieldA: ErrorCode, fieldB: String)

    // when
    val derived = Pickler.derived[MyCaseClass]
    val jsonStr1 = derived.toCodec.encode(MyCaseClass(ErrorTimeout, "msg18"))
    val jsonStr2 = derived.toCodec.encode(MyCaseClass(CustomError("customErrMsg"), "msg18"))

    // then
    jsonStr1 shouldBe """{"FIELDA":{"$type":"sttp.tapir.json.ErrorTimeout"},"FIELDB":"msg18"}"""
    jsonStr2 shouldBe """{"FIELDA":{"$type":"sttp.tapir.json.CustomError","MSG":"customErrMsg"},"FIELDB":"msg18"}"""
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
      codecCc3.decode("""{"fieldA":{"$type":"sttp.tapir.json.ErrorNotFound"}, "fieldC": {"fieldInner": "deeper field inner"}}""")

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

  it should "apply custom discriminator name to a simple ADT" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    given schemaConfig: Configuration = Configuration.default.withDiscriminator("kind")
    case class MyCaseClass(fieldA: ErrorCode, fieldB: String)
    val inputObj1 = MyCaseClass(CustomError("customErrMsg2"), "msg19")
    val inputObj2 = MyCaseClass(ErrorNotFound, "")

    // when
    val derived = Pickler.derived[MyCaseClass]
    val codec = derived.toCodec
    val jsonStr1 = codec.encode(inputObj1)
    val jsonStr2 = codec.encode(inputObj2)

    // then
    jsonStr1 shouldBe """{"fieldA":{"kind":"sttp.tapir.json.CustomError","msg":"customErrMsg2"},"fieldB":"msg19"}"""
    jsonStr2 shouldBe """{"fieldA":{"kind":"sttp.tapir.json.ErrorNotFound"},"fieldB":""}"""
    codec.decode(jsonStr1) shouldBe Value(inputObj1)
    codec.decode(jsonStr2) shouldBe Value(inputObj2)
  }

  it should "Set discriminator value using class name" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    sealed trait Status:
      def code: Int

    case class StatusOk(oF: Int) extends Status {
      def code = 200
    }
    case class StatusBadRequest(bF: Int) extends Status {
      def code = 400
    }

    case class Response(status: Status)

    // when
    val picklerResponse = Pickler.derived[Response]
    val inputObject = Response(StatusBadRequest(55))
    val codec = picklerResponse.toCodec
    val jsonStr = codec.encode(inputObject)
    val decoded = codec.decode(jsonStr)

    // then
    jsonStr shouldBe """{"status":{"$type":"sttp.tapir.json.PicklerTest._StatusBadRequest","bF":55}}"""
    decoded shouldBe Value(inputObject)
  }
  it should "Set discriminator value using oneOfUsingField" in {
    // given
    sealed trait Status:
      def code: Int

    case class StatusOk(oF: Int) extends Status {
      def code = 200
    }
    case class StatusBadRequest(bF: Int) extends Status {
      def code = 400
    }

    case object StatusInternalError extends Status {
      def code = 500
    }

    case class Response(status: Status)
    val picklerOk = Pickler.derived[StatusOk]
    val picklerBadRequest = Pickler.derived[StatusBadRequest]
    val picklerInternalError = Pickler.derived[StatusInternalError.type]

    // when
    given statusPickler: Pickler[Status] = Pickler.oneOfUsingField[Status, Int](_.code, codeInt => s"code-$codeInt")(
      200 -> picklerOk,
      400 -> picklerBadRequest,
      500 -> picklerInternalError
    )
    val picklerResponse = Pickler.derived[Response]
    val codec = picklerResponse.toCodec
    val inputObject1 = Response(StatusBadRequest(54))
    val jsonStr1 = codec.encode(inputObject1)
    val decoded1 = codec.decode(jsonStr1)
    val inputObject2 = Response(StatusInternalError)
    val jsonStr2 = codec.encode(inputObject2)
    val decoded2 = codec.decode(jsonStr2)

    // then
    jsonStr1 shouldBe """{"status":{"$type":"code-400","bF":54}}"""
    decoded1 shouldBe Value(inputObject1)
    jsonStr2 shouldBe """{"status":{"$type":"code-500"}}"""
    decoded2 shouldBe Value(inputObject2)
  }

  it should "Set discriminator value with oneOfUsingField for a deeper hierarchy" in {
    // given
    sealed trait Status:
      def code: Int

    sealed trait DeeperStatus extends Status
    sealed trait DeeperStatus2 extends Status

    case class StatusOk(oF: Int) extends DeeperStatus {
      def code = 200
    }
    case class StatusBadRequest(bF: Int) extends DeeperStatus2 {
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
    val inputObject = Response(StatusOk(818))
    val codec = picklerResponse.toCodec
    val encoded = codec.encode(inputObject)
    val decoded = codec.decode(encoded)

    // then
    encoded shouldBe """{"status":{"$type":"code-200","oF":818}}"""
    decoded shouldBe Value(inputObject)
  }

  it should "support simple enums" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    import Fixtures.*

    // when
    val picklerResponse = Pickler.derived[Response]
    val codec = picklerResponse.toCodec
    val inputObj = Response(ColorEnum.Pink, "pink!!")
    val encoded = codec.encode(inputObj)

    // then
    encoded shouldBe """{"color":"Pink","description":"pink!!"}"""
    codec.decode(encoded) shouldBe Value(inputObj)
  }

  it should "handle enums with ordinal encoding" in {
    // given
    import Fixtures.*
    given picklerColorEnum: Pickler[ColorEnum] = Pickler.derivedEnumeration[ColorEnum].customStringBased(_.ordinal.toString)

    // when
    val picklerResponse = Pickler.derived[Response]
    val codec = picklerResponse.toCodec
    val inputObj = Response(ColorEnum.Pink, "pink!!")
    val encoded = codec.encode(inputObj)

    // then
    encoded shouldBe """{"color":"1","description":"pink!!"}"""
    codec.decode(encoded) shouldBe Value(inputObj)
  }

  it should "handle enums with custom function encoding" in {
    // given
    import Fixtures.*
    given picklerColorEnum: Pickler[RichColorEnum] =
      Pickler.derivedEnumeration[RichColorEnum].customStringBased(enumValue => s"color-number-${enumValue.code}")

    // when
    val picklerResponse = Pickler.derived[RichColorResponse]
    val codec = picklerResponse.toCodec
    val inputObj = RichColorResponse(RichColorEnum.Cyan)
    val encoded = codec.encode(inputObj)

    // then
    encoded shouldBe """{"color":"color-number-3"}"""
    codec.decode(encoded) shouldBe Value(inputObj)
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

  it should "Reject oneOfUsingField for enums" in {
    // given
    assertCompiles("""
      import Fixtures.*
      val picklerCyan = Pickler.derived[RichColorEnum.Cyan.type]
      val picklerMagenta = Pickler.derived[RichColorEnum.Magenta.type]""")
    // when
    assertDoesNotCompile("""
      import Fixtures.*
      val picklerCyan = Pickler.derived[RichColorEnum.Cyan.type]
      val picklerMagenta = Pickler.derived[RichColorEnum.Magenta.type]

      given picklerRichColor: Pickler[RichColorEnum] = 
        Pickler.oneOfUsingField[RichColorEnum, Int](_.code, codeInt => s"code-$codeInt")(
          3 -> picklerCyan,
          18 -> picklerMagenta
        )""")
  }
}
