package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.{OpenapiComponent, OpenapiSchemaType}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._
import sttp.tapir.codegen.testutils.CompileCheckTestBase

import scala.util.Try

class ClassDefinitionGeneratorSpec extends CompileCheckTestBase {
  def noDefault(f: OpenapiSchemaType): OpenapiSchemaField = OpenapiSchemaField(f, None)

  it should "generate the example class defs" in {
    new ClassDefinitionGenerator().classDefs(TestHelpers.myBookshopDoc).get.classRepr shouldCompile ()
  }

  it should "generate simple class" in {
    val doc = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(false))), Seq("text"), false)
          )
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc).get.classRepr shouldCompile ()
  }

  it should "generate simple enum" in {
    val doc = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaEnum(
              "string",
              Seq(OpenapiSchemaConstantString("paperback"), OpenapiSchemaConstantString("hardback")),
              false
            )
          )
        )
      )
    )
    // the enumeratum import should be included by the BasicGenerator iff we generated enums
    new ClassDefinitionGenerator().classDefs(doc).get.classRepr shouldCompile ()
  }

  it should "generate simple class with reserved propName" in {
    val doc = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("type" -> noDefault(OpenapiSchemaString(false))), Seq("type"), false)
          )
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc).get.classRepr shouldCompile ()
  }

  it should "generate class with array" in {
    val doc = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(
              Map("texts" -> noDefault(OpenapiSchemaArray(OpenapiSchemaString(false), false))),
              Seq("texts"),
              false
            )
          )
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc).get.classRepr shouldCompile ()
  }

  it should "generate class with map" in {
    val doc = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(
              Map("texts" -> noDefault(OpenapiSchemaMap(OpenapiSchemaString(false), false))),
              Seq("texts"),
              false
            )
          )
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc).get.classRepr shouldCompile ()
  }

  it should "generate class with any type" in {
    val doc = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("anyType" -> noDefault(OpenapiSchemaAny(false))), Seq("anyType"), false)
          )
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc).get.classRepr shouldCompile ()
  }

  it should "generate class with inner class" in {
    val doc = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(
              Map(
                "inner" -> noDefault(OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(false))), Seq("text"), false))
              ),
              Seq("inner"),
              false
            )
          )
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc).get.classRepr shouldCompile ()
  }

  it should "generate class with array with inner class" in {
    val doc = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" ->
              OpenapiSchemaObject(
                Map(
                  "objects" -> noDefault(
                    OpenapiSchemaArray(
                      OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(false))), Seq("text"), false),
                      false
                    )
                  )
                ),
                Seq("objects"),
                false
              )
          )
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc).get.classRepr shouldCompile ()
  }

  it should "generate class with map with inner class" in {
    val doc = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" ->
              OpenapiSchemaObject(
                Map(
                  "objects" -> noDefault(
                    OpenapiSchemaMap(
                      OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(false))), Seq("text"), false),
                      false
                    )
                  )
                ),
                Seq("objects"),
                false
              )
          )
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc).get.classRepr shouldCompile ()
  }

  it should "nonrequired and required are not the same" in {
    val doc1 = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(false))), Seq.empty, false)
          )
        )
      )
    )
    val doc2 = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(false))), Seq("text"), false)
          )
        )
      )
    )
    val gen = new ClassDefinitionGenerator()
    val res1 = gen.classDefs(doc1).map(_.classRepr)
    val res2 = gen.classDefs(doc2).map(_.classRepr)
    res1 shouldNot be(res2)
    res1.get shouldCompile ()
    res2.get shouldCompile ()
  }
  it should "nonrequired and nullable are the same" in {
    val doc1 = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(false))), Seq.empty, false)
          )
        )
      )
    )
    val doc2 = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(true))), Seq("text"), false)
          )
        )
      )
    )
    val gen = new ClassDefinitionGenerator()
    val res1 = gen.classDefs(doc1).map(_.classRepr)
    val res2 = gen.classDefs(doc2).map(_.classRepr)
    res1 shouldBe res2
  }

  it should "generate legal scala 3 enums when instructed to" in {
    val doc = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaEnum("string", Seq(OpenapiSchemaConstantString("enum1"), OpenapiSchemaConstantString("enum2")), false)
          )
        )
      )
    )

    val gen = new ClassDefinitionGenerator()
    def concatted(res: GeneratedClassDefinitions): String = {
      (res.classRepr + res.serdeRepr.fold("")("\n" + _)).linesIterator.filterNot(_.trim.isEmpty).mkString("\n")
    }
    val res = gen
      .classDefs(doc, true, jsonParamRefs = Set("Test"))
      .map(concatted)
    val resWithQueryParamCodec = gen
      .classDefs(doc, true, queryOrPathParamRefs = Set("Test"), jsonParamRefs = Set("Test"))
      .map(concatted)
    // can't just check whether these compile, because our tests only run on scala 2.12 - so instead just eyeball it...
    res shouldBe Some("""enum Test derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec {
      |  case enum1, enum2
      |}""".stripMargin)
    resWithQueryParamCodec shouldBe Some("""def enumMap[E: enumextensions.EnumMirror]: Map[String, E] =
      |  Map.from(
      |    for e <- enumextensions.EnumMirror[E].values yield e.name.toUpperCase -> e
      |  )
      |case class EnumExtraParamSupport[T: enumextensions.EnumMirror](eMap: Map[String, T]) extends ExtraParamSupport[T] {
      |  // Case-insensitive mapping
      |  def decode(s: String): sttp.tapir.DecodeResult[T] =
      |    scala.util
      |      .Try(eMap(s.toUpperCase))
      |      .fold(
      |        _ =>
      |          sttp.tapir.DecodeResult.Error(
      |            s,
      |            new NoSuchElementException(
      |              s"Could not find value $s for enum ${enumextensions.EnumMirror[T].mirroredName}, available values: ${enumextensions.EnumMirror[T].values.mkString(", ")}"
      |            )
      |          ),
      |        sttp.tapir.DecodeResult.Value(_)
      |      )
      |  def encode(t: T): String = t.name
      |}
      |def extraCodecSupport[T: enumextensions.EnumMirror]: ExtraParamSupport[T] =
      |  EnumExtraParamSupport(enumMap[T](using enumextensions.EnumMirror[T]))
      |object Test {
      |  given enumCodecSupportTest: ExtraParamSupport[Test] =
      |    extraCodecSupport[Test]
      |}
      |enum Test derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec, enumextensions.EnumMirror {
      |  case enum1, enum2
      |}""".stripMargin)
  }

  it should "generate named maps" in {
    val doc = OpenapiDocument(
      "",
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "MyObject" -> OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(true))), Seq("text"), false),
            "MyEnum" -> OpenapiSchemaEnum("string", Seq(OpenapiSchemaConstantString("enum1"), OpenapiSchemaConstantString("enum2")), false),
            "MyMapPrimitive" -> OpenapiSchemaMap(OpenapiSchemaString(false), false),
            "MyMapObject" -> OpenapiSchemaMap(OpenapiSchemaRef("#/components/schemas/MyObject"), false),
            "MyMapEnum" -> OpenapiSchemaMap(OpenapiSchemaRef("#/components/schemas/MyEnum"), false)
          )
        )
      )
    )

    val gen = new ClassDefinitionGenerator()
    gen.classDefs(doc, false).get.classRepr shouldCompile ()
  }

  import cats.implicits._
  import io.circe._
  import io.circe.yaml.parser

  it should "correctly handle quoted strings" in {
    val quotedString = """a "quoted" string"""
    val yaml =
      s"""
        |openapi: 3.0.0
        |info:
        |  version: required field, not relevant for test
        |  title: required field, not relevant for test
        |paths:
        |  /foo:
        |    get:
        |      description: hello
        |      parameters:
        |        - name: search
        |          in: query
        |          required: true
        |          description: $quotedString
        |          schema:
        |            type: string
        |      responses:
        |        '200':
        |          description: required field, not relevant for test
        |""".stripMargin

    val parserRes: Either[Error, OpenapiDocument] = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])

    val res: String = parserRes match {
      case Left(value) => throw new Exception(value)
      case Right(doc) =>
        new EndpointGenerator()
          .endpointDefs(
            doc,
            useHeadTagForObjectNames = false,
            targetScala3 = false,
            jsonSerdeLib = JsonSerdeLib.Circe,
            streamingImplementation = StreamingImplementation.FS2,
            generateEndpointTypes = false
          )
          .endpointDecls(None)
    }

    val compileUnit =
      s"""
         |import sttp.tapir._
         |
         |object TapirGeneratedEndpoints {
         |  $res
         |}
         |  """.stripMargin

    compileUnit shouldCompile ()

  }

  it should "be able to find all types referenced by schema" in {
    val allSchemas = Map(
      "TopMap" -> OpenapiSchemaMap(OpenapiSchemaRef("#/components/schemas/MapType"), false),
      "MapType" -> OpenapiSchemaRef("#/components/schemas/MapValue"),
      "MapValue" -> OpenapiSchemaArray(OpenapiSchemaDateTime(false), false),
      "TopArray" -> OpenapiSchemaMap(OpenapiSchemaRef("#/components/schemas/ArrayType"), false),
      "ArrayType" -> OpenapiSchemaRef("#/components/schemas/ArrayValue"),
      "ArrayValue" -> OpenapiSchemaArray(OpenapiSchemaByte(false), false),
      "TopOneOf" -> OpenapiSchemaOneOf(
        Seq(
          OpenapiSchemaRef("#/components/schemas/TopMap"),
          OpenapiSchemaRef("#/components/schemas/TopArray"),
          OpenapiSchemaRef("#/components/schemas/OneOfValue")
        )
      ),
      "OneOfValue" -> OpenapiSchemaArray(OpenapiSchemaBinary(false), false),
      "TopObject" -> OpenapiSchemaObject(
        Map(
          "innerMap" -> OpenapiSchemaField(OpenapiSchemaRef("#/components/schemas/TopMap"), None),
          "innerArray" -> OpenapiSchemaField(OpenapiSchemaRef("#/components/schemas/TopArray"), None),
          "innerOneOf" -> OpenapiSchemaField(OpenapiSchemaRef("#/components/schemas/TopOneOf"), None),
          "innerBoolean" -> OpenapiSchemaField(OpenapiSchemaBoolean(false), None),
          "recursiveEntry" -> OpenapiSchemaField(OpenapiSchemaRef("#/components/schemas/TopObject"), None)
        ),
        Nil,
        false
      )
    )
    def fetchJsonParamRefs(initialSet: Set[String], toCheck: Seq[OpenapiSchemaType]): Set[String] = toCheck match {
      case Nil          => initialSet
      case head +: tail => new ClassDefinitionGenerator().recursiveFindAllReferencedSchemaTypes(allSchemas)(head, initialSet, tail)
    }
    fetchJsonParamRefs(Set("MapType"), Seq(allSchemas("MapType"))) shouldEqual Set("MapType", "MapValue")
    fetchJsonParamRefs(Set("TopMap"), Seq(allSchemas("TopMap"))) shouldEqual Set("TopMap", "MapType", "MapValue")
    fetchJsonParamRefs(Set("TopArray"), Seq(allSchemas("TopArray"))) shouldEqual Set("TopArray", "ArrayType", "ArrayValue")
    fetchJsonParamRefs(Set("TopOneOf"), Seq(allSchemas("TopOneOf"))) shouldEqual Set(
      "TopOneOf",
      "OneOfValue",
      "TopMap",
      "MapType",
      "MapValue",
      "TopArray",
      "ArrayType",
      "ArrayValue"
    )
    fetchJsonParamRefs(Set("TopObject"), Seq(allSchemas("TopObject"))) shouldEqual allSchemas.keySet
    fetchJsonParamRefs(
      Set("TopMap", "TopArray", "TopObject"),
      Seq(allSchemas("TopMap"), allSchemas("TopArray"), allSchemas("TopObject"))
    ) shouldEqual allSchemas.keySet

  }

  it should "error on illegal default declarations" in {
    val yaml =
      """
         |schemas:
         |  ReqWithDefaults:
         |    required:
         |    - f1
         |    type: object
         |    properties:
         |      f1:
         |        type: string
         |        default: 1977
         |""".stripMargin

    val doc: OpenapiComponent = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiComponent])
      .toTry
      .get
    val gen = new ClassDefinitionGenerator()
    val res1 = Try(gen.classDefs(OpenapiDocument("", null, Nil, Some(doc)))).toEither

    res1.left.get.getMessage shouldEqual "Generating class for ReqWithDefaults: Cannot render a number as type sttp.tapir.codegen.openapi.models.OpenapiSchemaType$OpenapiSchemaString."

  }

  it should "generate ADTs for oneOf schemas (jsoniter)" in {
    val imports =
      """import sttp.tapir.generic.auto._
        |""".stripMargin
    val gen = new ClassDefinitionGenerator()
    def testOK(doc: OpenapiDocument) = {
      val GeneratedClassDefinitions(res, jsonSerdes, _) =
        gen
          .classDefs(
            doc,
            false,
            jsonSerdeLib = JsonSerdeLib.Jsoniter,
            jsonParamRefs = Set("ReqWithVariants"),
            fullModelPath = "foo.bar.baz"
          )
          .get

      val fullRes = imports + res + "\n" + jsonSerdes.get
      res shouldCompile ()
      fullRes shouldCompile ()
      jsonSerdes.get should include(
        """implicit lazy val reqWithVariantsCodec: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[ReqWithVariants] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make(com.github.plokhotnyuk.jsoniter_scala.macros.CodecMakerConfig.withAllowRecursiveTypes(true).withTransientEmpty(false).withTransientDefault(false).withRequireCollectionFields(true).withRequireDiscriminatorFirst(false).withDiscriminatorFieldName(Some("type")))"""
      )
    }
    testOK(TestHelpers.oneOfDocsWithMapping)
    testOK(TestHelpers.oneOfDocsWithDiscriminatorNoMapping)
    val failed = Try(
      gen.classDefs(TestHelpers.oneOfDocsNoDiscriminator, false, jsonSerdeLib = JsonSerdeLib.Circe, jsonParamRefs = Set("ReqWithVariants"))
    )
    failed.isFailure shouldEqual true
    failed.failed.get.getMessage shouldEqual "Problems in non-discriminated oneOf declaration (#/components/schemas/ReqSubtype2 appears before #/components/schemas/ReqSubtype3, but a #/components/schemas/ReqSubtype3 can be a valid #/components/schemas/ReqSubtype2)"
  }

  it should "generate ADTs for oneOf schemas (circe)" in {
    val imports =
      """import sttp.tapir.generic.auto._
        |""".stripMargin
    val gen = new ClassDefinitionGenerator()
    def testOK(doc: OpenapiDocument) = {
      val GeneratedClassDefinitions(res, jsonSerdes, _) =
        gen.classDefs(doc, false, jsonSerdeLib = JsonSerdeLib.Circe, jsonParamRefs = Set("ReqWithVariants")).get

      val fullRes = (res + "\n" + jsonSerdes.get)
      (imports + fullRes) shouldCompile ()
      val expectedLines = Seq(
        """implicit lazy val reqWithVariantsJsonEncoder: io.circe.Encoder[ReqWithVariants]""",
        """case x: ReqSubtype1 => io.circe.Encoder[ReqSubtype1].apply(x).mapObject(_.add("type", io.circe.Json.fromString("ReqSubtype1")))""",
        """implicit lazy val reqWithVariantsJsonDecoder: io.circe.Decoder[ReqWithVariants]""",
        """discriminator <- c.downField("type").as[String]""",
        """case "ReqSubtype1" => c.as[ReqSubtype1]"""
      )
      expectedLines.foreach(line => fullRes should include(line))
    }
    testOK(TestHelpers.oneOfDocsWithMapping)
    testOK(TestHelpers.oneOfDocsWithDiscriminatorNoMapping)
    val failed = Try(
      gen.classDefs(TestHelpers.oneOfDocsNoDiscriminator, false, jsonSerdeLib = JsonSerdeLib.Circe, jsonParamRefs = Set("ReqWithVariants"))
    )
    failed.isFailure shouldEqual true
    failed.failed.get.getMessage shouldEqual "Problems in non-discriminated oneOf declaration (#/components/schemas/ReqSubtype2 appears before #/components/schemas/ReqSubtype3, but a #/components/schemas/ReqSubtype3 can be a valid #/components/schemas/ReqSubtype2)"
  }
}
