package sttp.tapir.json.pickler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.Schema.annotations.{default, encodedName}
import sttp.tapir.SchemaType.{SCoproduct, SProduct, SString}
import sttp.tapir.{DecodeResult, Schema, Validator}

import java.util.UUID

/** The codec half of a derived `Pickler`: the wire format for products, coproducts, enumerations, recursive types and non-structural roots,
  * plus the interaction with user-supplied instances and the agreement between the codec and the schema.
  */
class CodecDerivationTest extends AnyFlatSpec with Matchers {
  import CodecFixtures.*

  private def roundTrip[T](pickler: Pickler[T], value: T, expectedJson: String): Unit = {
    val codec = pickler.toCodec
    codec.encode(value) shouldBe expectedJson
    val _ = codec.decode(expectedJson) shouldBe Value(value)
  }

  behavior of "codec derivation for products"

  it should "encode and decode a flat case class" in {
    roundTrip(Pickler.derived[FlatClass], FlatClass(44, "b_value"), """{"fieldA":44,"fieldB":"b_value"}""")
  }

  it should "encode and decode nested case classes" in {
    roundTrip(
      Pickler.derived[TopClass],
      TopClass("field_a_value", InnerClass(7954)),
      """{"fieldA":"field_a_value","fieldB":{"fieldA11":7954}}"""
    )
  }

  it should "apply a member-name transformation given as a local given" in {
    // note `fieldA11 -> field_a11`, not `field_a_11`
    given config: PicklerConfiguration = PicklerConfiguration.default.withSnakeCaseMemberNames
    roundTrip(
      Pickler.derived[TopClass],
      TopClass("field_a_value", InnerClass(7954)),
      """{"field_a":"field_a_value","field_b":{"field_a11":7954}}"""
    )
  }

  it should "apply a member-name transformation given as an arbitrary evaluable function" in {
    given PicklerConfiguration = PicklerConfiguration.default.withToEncodedName(_.toUpperCase())
    roundTrip(Pickler.derived[FlatClass], FlatClass(1, "x"), """{"FIELDA":1,"FIELDB":"x"}""")
  }

  it should "honour @encodedName on a field, which beats the configured transformation" in {
    given PicklerConfiguration = PicklerConfiguration.default.withSnakeCaseMemberNames
    roundTrip(
      Pickler.derived[TopClass2],
      TopClass2("field_a_value", AnnotatedInnerClass("f-a-value", "f-b-value")),
      """{"field_a":"field_a_value","field_b":{"encoded_field-a":"f-a-value","field_b":"f-b-value"}}"""
    )
  }

  it should "keep field renames local to their class (design: one JsonCodecMaker.make per class)" in {
    // `Outer.name` and `Inner.name` share a Scala name but not an encoding. A single graph-wide
    // `fieldNameMapper` could not express this; per-class configurations can.
    roundTrip(
      Pickler.derived[SameFieldNameOuter],
      SameFieldNameOuter("o", SameFieldNameInner("i")),
      """{"name":"o","inner":{"inner_name":"i"}}"""
    )
  }

  it should "encode and decode Options, omitting None by default" in {
    val flat = Pickler.derived[FlatClassWithOption].toCodec
    val nested = Pickler.derived[NestedClassWithOption].toCodec
    flat.encode(FlatClassWithOption("fieldA value", Some(-4018), true)) shouldBe
      """{"fieldA":"fieldA value","fieldB":-4018,"fieldC":true}"""
    nested.encode(NestedClassWithOption(Some(FlatClassWithOption("fieldA value2", None, true)))) shouldBe
      """{"innerField":{"fieldA":"fieldA value2","fieldC":true}}"""
    flat.encode(FlatClassWithOption("fieldA value", None, true)) shouldBe """{"fieldA":"fieldA value","fieldC":true}"""
    flat.decode("""{"fieldA":"x","fieldC":false}""") shouldBe Value(FlatClassWithOption("x", None, false))
    flat.decode("""{"fieldA":"x","fieldB":null,"fieldC":false}""") shouldBe Value(FlatClassWithOption("x", None, false))
  }

  it should "encode None as null when transientNone is off" in {
    given PicklerConfiguration = PicklerConfiguration.default.withTransientNone(false)
    Pickler.derived[FlatClassWithOption].toCodec.encode(FlatClassWithOption("fieldA value2", None, true)) shouldBe
      """{"fieldA":"fieldA value2","fieldB":null,"fieldC":true}"""
  }

  it should "encode empty collections as [] and read a missing collection as empty" in {
    val flat = Pickler.derived[FlatClassWithList].toCodec
    val nested = Pickler.derived[NestedClassWithList].toCodec
    flat.encode(FlatClassWithList("fieldA value", List(64, -5))) shouldBe """{"fieldA":"fieldA value","fieldB":[64,-5]}"""
    nested.encode(NestedClassWithList(List(FlatClassWithList("a2", Nil), FlatClassWithList("a3", List(8, 9))))) shouldBe
      """{"innerField":[{"fieldA":"a2","fieldB":[]},{"fieldA":"a3","fieldB":[8,9]}]}"""
    flat.decode("""{"fieldA":"a"}""") shouldBe Value(FlatClassWithList("a", Nil))
  }

  it should "encode and decode a Map with String keys" in {
    // insertion order is preserved by an immutable Map with <= 4 entries
    roundTrip(
      Pickler.derived[ClassWithMap],
      ClassWithMap(Map("keyB" -> SimpleTestResult("result1"), "keyA" -> SimpleTestResult("result2"))),
      """{"field":{"keyB":{"msg":"result1"},"keyA":{"msg":"result2"}}}"""
    )
  }

  it should "unwrap AnyVal value classes, in the codec and in the schema alike" in {
    val pickler = Pickler.derived[ClassWithValues]
    val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    roundTrip(
      pickler,
      ClassWithValues(UserId(id), UserName("Alan"), 65),
      """{"id":"550e8400-e29b-41d4-a716-446655440000","name":"Alan","age":65}"""
    )
    val fields = pickler.schema.schemaType.asInstanceOf[SProduct[ClassWithValues]].fields
    fields.find(_.name.name == "name").get.schema.schemaType shouldBe SString()
  }

  it should "honour Scala default parameters when decoding, and still write fields equal to them" in {
    val codec = Pickler.derived[ClassWithScalaDefault].toCodec
    codec.encode(ClassWithScalaDefault("field-a-user-value", "msg104")) shouldBe """{"fieldA":"field-a-user-value","fieldB":"msg104"}"""
    codec.encode(ClassWithScalaDefault("field-a-default", "text b")) shouldBe """{"fieldA":"field-a-default","fieldB":"text b"}"""
    codec.decode("""{"fieldB":"msg205"}""") shouldBe Value(ClassWithScalaDefault("field-a-default", "msg205"))
  }

  it should "NOT fill a missing field from tapir's @default annotation (documented limitation)" in {
    // jsoniter has no hook for anything but Scala default parameters, so a missing field with only a tapir @default
    // is a required-field error. The annotation is still reflected in the schema.
    val pickler = Pickler.derived[ClassWithDefault]
    pickler.toCodec.decode("""{"fieldB":"x"}""") shouldBe a[DecodeResult.Error]
    pickler.schema.schemaType.asInstanceOf[SProduct[ClassWithDefault]].fields.head.schema.default.map(_._1) shouldBe
      Some("field-a-default")
  }

  it should "report an unknown field as skipped rather than failing" in {
    Pickler.derived[FlatClass].toCodec.decode("""{"fieldA":1,"fieldB":"x","extra":true}""") shouldBe Value(FlatClass(1, "x"))
  }

  behavior of "codec derivation for coproducts"

  it should "handle a mixed sealed hierarchy with the default $type discriminator" in {
    val codec = Pickler.derived[MyCaseClass].toCodec
    codec.encode(MyCaseClass(ErrorTimeout, "msg18")) shouldBe """{"fieldA":{"$type":"ErrorTimeout"},"fieldB":"msg18"}"""
    codec.encode(MyCaseClass(CustomError("customErrMsg"), "msg18")) shouldBe
      """{"fieldA":{"$type":"CustomError","msg":"customErrMsg"},"fieldB":"msg18"}"""
    codec.decode("""{"fieldA":{"$type":"CustomError","msg":"customErrMsg"},"fieldB":"msg18"}""") shouldBe
      Value(MyCaseClass(CustomError("customErrMsg"), "msg18"))
    codec.decode("""{"fieldA":{"$type":"ErrorTimeout"},"fieldB":"msg18"}""") shouldBe Value(MyCaseClass(ErrorTimeout, "msg18"))
  }

  it should "apply the member-name transformation to leaf fields too" in {
    given PicklerConfiguration = PicklerConfiguration.default.withToEncodedName(_.toUpperCase())
    val codec = Pickler.derived[MyCaseClass].toCodec
    codec.encode(MyCaseClass(ErrorTimeout, "msg18")) shouldBe """{"FIELDA":{"$type":"ErrorTimeout"},"FIELDB":"msg18"}"""
    codec.encode(MyCaseClass(CustomError("customErrMsg"), "msg18")) shouldBe
      """{"FIELDA":{"$type":"CustomError","MSG":"customErrMsg"},"FIELDB":"msg18"}"""
  }

  it should "apply a custom discriminator name" in {
    given PicklerConfiguration = PicklerConfiguration.default.withDiscriminator("kind")
    val codec = Pickler.derived[MyCaseClass].toCodec
    roundTrip(
      Pickler.derived[MyCaseClass],
      MyCaseClass(CustomError("customErrMsg2"), "msg19"),
      """{"fieldA":{"kind":"CustomError","msg":"customErrMsg2"},"fieldB":"msg19"}"""
    )
    codec.encode(MyCaseClass(ErrorNotFound, "")) shouldBe """{"fieldA":{"kind":"ErrorNotFound"},"fieldB":""}"""
  }

  it should "accept the discriminator anywhere in the object" in {
    Pickler.derived[MyCaseClass].toCodec.decode("""{"fieldA":{"msg":"late","$type":"CustomError"},"fieldB":"b"}""") shouldBe
      Value(MyCaseClass(CustomError("late"), "b"))
  }

  it should "use full kebab-case discriminator values" in {
    given PicklerConfiguration = PicklerConfiguration.default.withFullKebabCaseDiscriminatorValues
    roundTrip(
      Pickler.derived[StatusResponse],
      StatusResponse(StatusBadRequest(65)),
      """{"status":{"$type":"sttp.tapir.json.pickler.codec-fixtures.status-bad-request","bF":65}}"""
    )
  }

  it should "use short snake-case discriminator values" in {
    given PicklerConfiguration = PicklerConfiguration.default.withSnakeCaseDiscriminatorValues
    roundTrip(Pickler.derived[StatusResponse], StatusResponse(StatusBadRequest(1)), """{"status":{"$type":"status_bad_request","bF":1}}""")
    roundTrip(Pickler.derived[StatusResponse], StatusResponse(StatusInternalError), """{"status":{"$type":"status_internal_error"}}""")
  }

  it should "encode a leaf on its own with its discriminator" in {
    // `alwaysEmitDiscriminator`: a member of a sealed hierarchy carries its tag whenever it is written, not only
    // through the parent, so that the two encodings agree.
    roundTrip(Pickler.derived[StatusBadRequest], StatusBadRequest(7), """{"$type":"StatusBadRequest","bF":7}""")
    Pickler.derived[StatusBadRequest].toCodec.decode("""{"bF":7}""") shouldBe Value(StatusBadRequest(7))
  }

  it should "encode an all-singleton sealed trait as a bare string" in {
    roundTrip(Pickler.derived[SealedVariantContainer], SealedVariantContainer(VariantA), """{"v":"VariantA"}""")
  }

  it should "encode a Scala 3 enum as a bare string, dropping parameters" in {
    roundTrip(Pickler.derived[Response], Response(ColorEnum.Pink, "pink!!"), """{"color":"Pink","description":"pink!!"}""")
    roundTrip(Pickler.derived[RichColorResponse], RichColorResponse(RichColorEnum.Cyan), """{"color":"Cyan"}""")
    // no alphabetical reordering of cases
    Pickler.derived[NotAlphabetical].toCodec.encode(NotAlphabetical.Xyz) shouldBe "\"Xyz\""
  }

  it should "encode a Scala 3 enum with case-class cases as discriminated objects" in {
    roundTrip(Pickler.derived[Entity], Entity.Business("221B Baker Street"), """{"$type":"Business","address":"221B Baker Street"}""")
  }

  it should "treat a hierarchy mixing objects and case classes as discriminated objects" in {
    val codec = Pickler.derived[NotAllSealedVariant].toCodec
    codec.encode(NotAllSealedVariantA) shouldBe """{"$type":"NotAllSealedVariantA"}"""
    codec.encode(NotAllSealedVariantB(3)) shouldBe """{"$type":"NotAllSealedVariantB","innerField":3}"""
  }

  it should "flatten nested sealed hierarchies to their leaves" in {
    val codec = Pickler.derived[Animal].toCodec
    codec.encode(Hamster("h")) shouldBe """{"$type":"Hamster","name":"h"}"""
    codec.decode("""{"$type":"Hamster","name":"h"}""") shouldBe Value(Hamster("h"))
  }

  behavior of "codec derivation for recursive types"

  it should "handle self-recursion through a collection" in {
    roundTrip(
      Pickler.derived[Tree],
      Tree(1, List(Tree(2, Nil), Tree(3, List(Tree(4, Nil))))),
      """{"value":1,"children":[{"value":2,"children":[]},{"value":3,"children":[{"value":4,"children":[]}]}]}"""
    )
  }

  it should "handle mutual recursion between two case classes" in {
    roundTrip(
      Pickler.derived[MutualA],
      MutualA(Some(MutualB(Some(MutualA(None, 3)), 2)), 1),
      """{"b":{"a":{"id":3},"id":2},"id":1}"""
    )
  }

  it should "handle recursion through a sealed hierarchy" in {
    roundTrip(
      Pickler.derived[Node],
      Edge(1, Edge(2, SimpleNode(3))),
      """{"$type":"Edge","id":1,"source":{"$type":"Edge","id":2,"source":{"$type":"SimpleNode","id":3}}}"""
    )
  }

  behavior of "codec derivation for non-structural roots"

  it should "derive codecs for primitives, Options, collections and Maps at the root" in {
    Pickler.derived[Int].toCodec.encode(5) shouldBe "5"
    Pickler.derived[String].toCodec.encode("x") shouldBe "\"x\""
    Pickler.derived[Option[Int]].toCodec.encode(Some(1)) shouldBe "1"
    Pickler.derived[List[FlatClass]].toCodec.encode(List(FlatClass(1, "a"))) shouldBe """[{"fieldA":1,"fieldB":"a"}]"""
    Pickler.derived[Map[String, Int]].toCodec.encode(Map("a" -> 1)) shouldBe """{"a":1}"""
  }

  behavior of "user-supplied instances for nested types"

  it should "use a given Pickler for a nested type, for both the schema and the codec" in {
    given Pickler[SimpleTestResult] =
      Pickler.derived[SimpleTestResult](using PicklerConfiguration.default.withScreamingSnakeCaseMemberNames)
    val pickler = Pickler.derived[ClassWithMap]

    pickler.toCodec.encode(ClassWithMap(Map("k" -> SimpleTestResult("r")))) shouldBe """{"field":{"k":{"MSG":"r"}}}"""
    val valueSchema = pickler.schema.schemaType
      .asInstanceOf[SProduct[ClassWithMap]]
      .fields
      .head
      .schema
      .schemaType
      .asInstanceOf[sttp.tapir.SchemaType.SOpenProduct[?, SimpleTestResult]]
      .valueSchema
    valueSchema.schemaType.asInstanceOf[SProduct[SimpleTestResult]].fields.map(_.name.encodedName) shouldBe List("MSG")
  }

  it should "still derive structurally when only generic.auto is in scope (the re-entrancy guard)" in {
    // With `auto.*` imported, `summon[Pickler[InnerClass]]` inside the macro has a candidate: our own macro. It must
    // abort quietly so that the search fails and the type is derived structurally -- not loop, not error.
    import sttp.tapir.json.pickler.generic.auto.*
    val pickler = summon[Pickler[TopClass]]
    pickler.toCodec.encode(TopClass("a", InnerClass(1))) shouldBe """{"fieldA":"a","fieldB":{"fieldA11":1}}"""

    // and the same with a recursive type, where a nested derivation would never terminate
    summon[Pickler[Tree]].toCodec.encode(Tree(1, List(Tree(2, Nil)))) shouldBe """{"value":1,"children":[{"value":2,"children":[]}]}"""
  }

  it should "refuse a given JsonValueCodec for a nested case class when no Pickler accompanies it" in {
    // jsoniter would honour the codec while the schema is still derived from the class -- exactly the drift the
    // single-expansion design exists to prevent. The user has to supply a Pickler, which carries both.
    assertDoesNotCompile("""
      given com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[SimpleTestResult] =
        new com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[SimpleTestResult] {
          def nullValue: SimpleTestResult = null
          def decodeValue(in: com.github.plokhotnyuk.jsoniter_scala.core.JsonReader, default: SimpleTestResult): SimpleTestResult =
            SimpleTestResult(in.readString(null))
          def encodeValue(x: SimpleTestResult, out: com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter): Unit = out.writeVal(x.msg)
        }
      Pickler.derived[ClassWithMap]
    """)
  }

  it should "refuse a given JsonValueCodec for the root type too" in {
    // `JsonCodecMaker.make[A]` never looks its own type up, so such a codec would be silently ignored.
    assertDoesNotCompile("""
      given com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[FlatClass] = null
      Pickler.derived[FlatClass]
    """)
  }

  behavior of "oneOfUsingField"

  it should "set discriminator values using oneOfUsingField" in {
    val picklerOk = Pickler.derived[StatusOk]
    val picklerBadRequest = Pickler.derived[StatusBadRequest]
    val picklerInternalError = Pickler.derived[StatusInternalError.type]

    given statusPickler: Pickler[Status] = Pickler.oneOfUsingField[Status, Int](_.code, codeInt => s"code-$codeInt")(
      200 -> picklerOk,
      400 -> picklerBadRequest,
      500 -> picklerInternalError
    )
    val picklerResponse = Pickler.derived[StatusResponse]

    roundTrip(picklerResponse, StatusResponse(StatusBadRequest(54)), """{"status":{"$type":"code-400","bF":54}}""")
    roundTrip(picklerResponse, StatusResponse(StatusInternalError), """{"status":{"$type":"code-500"}}""")

    // The schema documents the same values, on the discriminator field the codec actually writes (`$type`, not the
    // extractor's `code`, which core's `Schema.oneOfUsingField` would have documented).
    val discriminator = statusPickler.schema.schemaType.asInstanceOf[SCoproduct[Status]].discriminator.get
    discriminator.name.encodedName shouldBe "$type"
    discriminator.mapping.keySet shouldBe Set("code-200", "code-400", "code-500")
    statusPickler.schema.schemaType.asInstanceOf[SCoproduct[Status]].subtypes.flatMap(_.name) should contain(
      Pickler.derived[StatusOk].schema.name.get
    )
  }

  it should "set discriminator values with oneOfUsingField for a deeper hierarchy" in {
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

    given statusPickler: Pickler[Status] = Pickler.oneOfUsingField[Status, Int](_.code, codeInt => s"code-$codeInt")(
      200 -> picklerOk,
      400 -> picklerBadRequest
    )
    roundTrip(Pickler.derived[Response], Response(StatusOk(818)), """{"status":{"$type":"code-200","oF":818}}""")
  }

  it should "reject oneOfUsingField on a case class" in {
    assertDoesNotCompile("""Pickler.oneOfUsingField[FlatClass, Int](_.fieldA, _.toString)(1 -> Pickler.derived[FlatClass])""")
  }

  it should "derive the children of oneOfUsingField under the outer configuration, whatever the mapped picklers were derived with" in {
    // The mapped picklers only say which leaf a value selects. Had their schemas been taken as-is, the schema would
    // document `o_f` while the codec writes `oF`.
    val snake = Pickler.derived[StatusOk](using PicklerConfiguration.default.withSnakeCaseMemberNames)
    val pickler = Pickler.oneOfUsingField[Status, Int](_.code, code => s"code-$code")(
      200 -> snake,
      400 -> Pickler.derived[StatusBadRequest],
      500 -> Pickler.derived[StatusInternalError.type]
    )
    pickler.toCodec.encode(StatusOk(1)) shouldBe """{"$type":"code-200","oF":1}"""
    val ok = pickler.schema.schemaType.asInstanceOf[SCoproduct[Status]].subtypes.find(_.name.exists(_.fullName.endsWith("StatusOk"))).get
    ok.schemaType.asInstanceOf[SProduct[?]].fields.map(_.name.encodedName) shouldBe List("oF", "$type")
    SchemaJsonAgreement.mismatches(pickler.schema, ujson.read(pickler.toCodec.encode(StatusOk(1)))) shouldBe Nil
  }

  it should "reject an incomplete or ambiguous oneOfUsingField mapping" in {
    scala.compiletime.testing
      .typeCheckErrors("""Pickler.oneOfUsingField[Status, Int](_.code, code => s"code-$code")(200 -> Pickler.derived[StatusOk])""")
      .map(_.message)
      .mkString should include("does not map every case of the hierarchy; missing: ")
    scala.compiletime.testing
      .typeCheckErrors("""Pickler.oneOfUsingField[Status, Int](_.code, code => s"code-$code")(
        200 -> Pickler.derived[StatusOk], 200 -> Pickler.derived[StatusBadRequest], 500 -> Pickler.derived[StatusInternalError.type])""")
      .map(_.message)
      .mkString should include("several cases map to 'code-200'")
  }

  behavior of "schema/codec agreement"

  it should "document the discriminator values the codec writes" in {
    given PicklerConfiguration = PicklerConfiguration.default.withFullKebabCaseDiscriminatorValues
    val pickler = Pickler.derived[Status]
    val documented = pickler.schema.schemaType.asInstanceOf[SCoproduct[Status]].discriminator.get.mapping.keySet
    documented shouldBe Set(
      "sttp.tapir.json.pickler.codec-fixtures.status-ok",
      "sttp.tapir.json.pickler.codec-fixtures.status-bad-request",
      "sttp.tapir.json.pickler.codec-fixtures.status-internal-error"
    )
    pickler.toCodec.encode(StatusInternalError) shouldBe
      """{"$type":"sttp.tapir.json.pickler.codec-fixtures.status-internal-error"}"""
  }

  it should "name every kind of leaf the way jsoniter does (canary for jsoniter upgrades)" in {
    // The leaf-name mapper has no fallback: a leaf whose jsoniter name we predicted wrongly makes `JsonCodecMaker` fail
    // compilation. Nested case classes/objects, enum cases with and without parameters, and leaves below an
    // intermediate trait are all covered here.
    given PicklerConfiguration = PicklerConfiguration.default.withFullDiscriminatorValues
    val prefix = "sttp.tapir.json.pickler.CodecFixtures"
    roundTrip(Pickler.derived[Status], StatusInternalError, s"""{"$$type":"$prefix.StatusInternalError"}""")
    roundTrip(Pickler.derived[Status], StatusOk(1), s"""{"$$type":"$prefix.StatusOk","oF":1}""")
    roundTrip(Pickler.derived[Entity], Entity.Person("a", 1), s"""{"$$type":"$prefix.Entity.Person","first":"a","age":1}""")
    roundTrip(Pickler.derived[NotAllSealedVariant], NotAllSealedVariantA, s"""{"$$type":"$prefix.NotAllSealedVariantA"}""")
    roundTrip(Pickler.derived[Animal], Hamster("h"), s"""{"$$type":"$prefix.Hamster","name":"h"}""")
    roundTrip(Pickler.derived[RichColorEnum], RichColorEnum.Cyan, "\"Cyan\"")
    roundTrip(Pickler.derived[ColorEnum], ColorEnum.Pink, "\"Pink\"")
  }

  it should "document an all-singleton hierarchy as a string enumeration, matching the bare-string encoding" in {
    val pickler = Pickler.derived[SealedVariant]
    pickler.schema.schemaType shouldBe SString()
    pickler.schema.validator shouldBe a[Validator.Enumeration[?]]
    pickler.schema.validator.asInstanceOf[Validator.Enumeration[SealedVariant]].encode.flatMap(_(VariantB)) shouldBe Some("VariantB")
    pickler.toCodec.encode(VariantB) shouldBe "\"VariantB\""
  }

  it should "document field names the codec writes, with @encodedName and the configured transformation" in {
    given PicklerConfiguration = PicklerConfiguration.default.withKebabCaseMemberNames
    val pickler = Pickler.derived[AnnotatedInnerClass]
    val documented = pickler.schema.schemaType.asInstanceOf[SProduct[AnnotatedInnerClass]].fields.map(_.name.encodedName)
    documented shouldBe List("encoded_field-a", "field-b")
    pickler.toCodec.encode(AnnotatedInnerClass("a", "b")) shouldBe """{"encoded_field-a":"a","field-b":"b"}"""
  }

  it should "treat a type alias and the aliased type as one type" in {
    // Memoisation, user-pickler lookup and the codec vals are keyed by the *dealiased* type: a `given Pickler[UUID]`
    // must apply to a field typed with an alias of it, and a hierarchy referenced both ways gets one codec.
    given Pickler[UUID] = Pickler.fromSchemaAndCodec(
      Schema.string[UUID],
      new com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[UUID] {
        def nullValue: UUID = null
        def decodeValue(in: com.github.plokhotnyuk.jsoniter_scala.core.JsonReader, default: UUID): UUID =
          UUID.fromString(in.readString(null).stripPrefix("id:"))
        def encodeValue(x: UUID, out: com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter): Unit = out.writeVal(s"id:$x")
      }
    )
    val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    roundTrip(
      Pickler.derived[WithAliases],
      WithAliases(id, id, List(StatusOk(1)), StatusOk(2)),
      s"""{"a":"id:$id","b":"id:$id","statuses":[{"$$type":"StatusOk","oF":1}],"status":{"$$type":"StatusOk","oF":2}}"""
    )
    // the schema names the aliased types, not the aliases
    Pickler.derived[List[Id]].schema.name shouldBe Pickler.derived[List[UUID]].schema.name
  }

  it should "name parameterised types with flattened, fully qualified type arguments" in {
    Pickler.derived[Map[String, List[Option[FlatClass]]]].schema.name shouldBe Some(
      Schema.SName("Map", List("scala.collection.immutable.List", "scala.Option", "sttp.tapir.json.pickler.CodecFixtures.FlatClass"))
    )
    Pickler.derived[Boxed[List[Int]]].schema.name shouldBe Some(
      Schema.SName("sttp.tapir.json.pickler.CodecFixtures.Boxed", List("scala.collection.immutable.List", "scala.Int"))
    )
  }

  it should "agree with the schema-only entry point" in {
    Pickler.schemaFor[Status] shouldBe Pickler.derived[Status].schema
  }

  it should "compute every name once, so an arbitrary name function cannot give the schema and the JSON different names" in {
    // Both halves splice the same literal, computed at expansion time; neither re-evaluates the function at runtime.
    given PicklerConfiguration =
      PicklerConfiguration.default
        .withToEncodedName(n => n.toUpperCase.concat("_"))
        .withDiscriminator("k")
        .withFullSnakeCaseDiscriminatorValues
    val pickler = Pickler.derived[Status]
    val leaf = pickler.schema.schemaType.asInstanceOf[SCoproduct[Status]].subtypes.find(_.name.exists(_.fullName.endsWith("StatusOk"))).get
    leaf.schemaType.asInstanceOf[SProduct[?]].fields.map(_.name.encodedName) shouldBe List("OF_", "k")
    val discriminator = pickler.schema.schemaType.asInstanceOf[SCoproduct[Status]].discriminator.get
    discriminator.name.encodedName shouldBe "k"
    pickler.toCodec.encode(StatusOk(1)) shouldBe """{"k":"sttp.tapir.json.pickler.codec_fixtures.status_ok","OF_":1}"""
    discriminator.mapping.keySet should contain("sttp.tapir.json.pickler.codec_fixtures.status_ok")
    Pickler.schemaFor[Status] shouldBe pickler.schema
  }

  it should "explain a name function that cannot be evaluated at compile time" in {
    // `.reverse` goes through the `StringOps` implicit conversion, which Hearth's evaluator cannot interpret.
    scala.compiletime.testing
      .typeCheckErrors("""
      given reversing: PicklerConfiguration = PicklerConfiguration.default.withToEncodedName(_.reverse)
      Pickler.derived[FlatClass]
    """)
      .map(_.message)
      .mkString should include("`toEncodedName` could not be evaluated at compile time")
  }
}

object CodecFixtures {
  case class FlatClass(fieldA: Int, fieldB: String)
  case class TopClass(fieldA: String, fieldB: InnerClass)
  case class InnerClass(fieldA11: Int)
  case class TopClass2(fieldA: String, fieldB: AnnotatedInnerClass)
  case class AnnotatedInnerClass(@encodedName("encoded_field-a") fieldA: String, fieldB: String)
  case class SameFieldNameInner(@encodedName("inner_name") name: String)
  case class SameFieldNameOuter(name: String, inner: SameFieldNameInner)
  case class FlatClassWithOption(fieldA: String, fieldB: Option[Int], fieldC: Boolean)
  case class NestedClassWithOption(innerField: Option[FlatClassWithOption])
  case class FlatClassWithList(fieldA: String, fieldB: List[Int])
  case class NestedClassWithList(innerField: List[FlatClassWithList])
  case class SimpleTestResult(msg: String)
  case class ClassWithMap(field: Map[String, SimpleTestResult])
  case class UserId(value: UUID) extends AnyVal
  case class UserName(name: String) extends AnyVal
  case class ClassWithValues(id: UserId, name: UserName, age: Int)
  case class ClassWithScalaDefault(fieldA: String = "field-a-default", fieldB: String)
  case class ClassWithDefault(@default("field-a-default") fieldA: String, fieldB: String)

  sealed trait ErrorCode
  case object ErrorNotFound extends ErrorCode
  case object ErrorTimeout extends ErrorCode
  case class CustomError(msg: String) extends ErrorCode
  case class MyCaseClass(fieldA: ErrorCode, fieldB: String)

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
  case class StatusResponse(status: Status)

  sealed trait SealedVariant
  case object VariantA extends SealedVariant
  case object VariantB extends SealedVariant
  case object VariantC extends SealedVariant
  case class SealedVariantContainer(v: SealedVariant)

  sealed trait NotAllSealedVariant
  case object NotAllSealedVariantA extends NotAllSealedVariant
  case class NotAllSealedVariantB(innerField: Int) extends NotAllSealedVariant

  enum ColorEnum:
    case Green, Pink
  case class Response(color: ColorEnum, description: String)

  enum RichColorEnum(val code: Int):
    case Cyan extends RichColorEnum(3)
    case Magenta extends RichColorEnum(18)
  case class RichColorResponse(color: RichColorEnum)

  enum NotAlphabetical:
    case Xyz
    case Fgh

  enum Entity:
    case Person(first: String, age: Int)
    case Business(address: String)

  sealed trait Animal
  sealed trait Rodent extends Animal
  case class Hamster(name: String) extends Rodent
  case class Dog(name: String) extends Animal

  case class Tree(value: Int, children: List[Tree])
  case class MutualA(b: Option[MutualB], id: Int)
  case class MutualB(a: Option[MutualA], id: Int)

  sealed trait Node
  case class Edge(id: Long, source: Node) extends Node
  case class SimpleNode(id: Long) extends Node

  type Id = UUID
  type Statuses = List[Status]
  case class WithAliases(a: Id, b: UUID, statuses: Statuses, status: Status)
  case class Boxed[T](value: T)
}
