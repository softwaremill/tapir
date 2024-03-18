package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.{OpenapiComponent, OpenapiSchemaType}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._
import sttp.tapir.codegen.testutils.CompileCheckTestBase

class ClassDefinitionGeneratorSpec extends CompileCheckTestBase {
  def noDefault(f: OpenapiSchemaType): OpenapiSchemaField = OpenapiSchemaField(f, None)

  it should "generate the example class defs" in {
    new ClassDefinitionGenerator().classDefs(TestHelpers.myBookshopDoc).get shouldCompile ()
  }

  it should "generate simple class" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(false))), Seq("text"), false)
          )
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc).get shouldCompile ()
  }

  it should "generate simple enum" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
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
    new ClassDefinitionGenerator().classDefs(doc).get shouldCompile ()
  }

  it should "generate simple class with reserved propName" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("type" -> noDefault(OpenapiSchemaString(false))), Seq("type"), false)
          )
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc).get shouldCompile ()
  }

  it should "generate class with array" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
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

    new ClassDefinitionGenerator().classDefs(doc).get shouldCompile ()
  }

  it should "generate class with map" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
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

    new ClassDefinitionGenerator().classDefs(doc).get shouldCompile ()
  }

  it should "generate class with any type" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("anyType" -> noDefault(OpenapiSchemaAny(false))), Seq("anyType"), false)
          )
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc).get shouldCompile ()
  }

  it should "generate class with inner class" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
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

    new ClassDefinitionGenerator().classDefs(doc).get shouldCompile ()
  }

  it should "generate class with array with inner class" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
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

    new ClassDefinitionGenerator().classDefs(doc).get shouldCompile ()
  }

  it should "generate class with map with inner class" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
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

    new ClassDefinitionGenerator().classDefs(doc).get shouldCompile ()
  }

  it should "nonrequired and required are not the same" in {
    val doc1 = OpenapiDocument(
      "",
      null,
      null,
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
      null,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(false))), Seq("text"), false)
          )
        )
      )
    )
    val gen = new ClassDefinitionGenerator()
    val res1 = gen.classDefs(doc1)
    val res2 = gen.classDefs(doc2)
    res1 shouldNot be(res2)
    res1.get shouldCompile ()
    res2.get shouldCompile ()
  }
  it should "nonrequired and nullable are the same" in {
    val doc1 = OpenapiDocument(
      "",
      null,
      null,
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
      null,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("text" -> noDefault(OpenapiSchemaString(true))), Seq("text"), false)
          )
        )
      )
    )
    val gen = new ClassDefinitionGenerator()
    val res1 = gen.classDefs(doc1)
    val res2 = gen.classDefs(doc2)
    res1 shouldBe res2
  }

  it should "generate legal scala 3 enums when instructed to" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaEnum("string", Seq(OpenapiSchemaConstantString("enum1"), OpenapiSchemaConstantString("enum2")), false)
          )
        )
      )
    )

    val gen = new ClassDefinitionGenerator()
    val res = gen.classDefs(doc, true, jsonParamRefs = Set("Test"))
    val resWithQueryParamCodec = gen.classDefs(doc, true, queryParamRefs = Set("Test"), jsonParamRefs = Set("Test"))
    // can't just check whether these compile, because our tests only run on scala 2.12 - so instead just eyeball it...
    res shouldBe Some("""
      |
      |enum Test derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec {
      |  case enum1, enum2
      |}""".stripMargin)
    resWithQueryParamCodec shouldBe Some("""
      |
      |def enumMap[E: enumextensions.EnumMirror]: Map[String, E] =
      |  Map.from(
      |    for e <- enumextensions.EnumMirror[E].values yield e.name.toUpperCase -> e
      |  )
      |
      |def makeQueryCodecForEnum[T: enumextensions.EnumMirror]: sttp.tapir.Codec[List[String], T, sttp.tapir.CodecFormat.TextPlain] =
      |  sttp.tapir.Codec
      |    .listHead[String, String, sttp.tapir.CodecFormat.TextPlain]
      |    .mapDecode(s =>
      |      // Case-insensitive mapping
      |      scala.util
      |        .Try(enumMap[T](using enumextensions.EnumMirror[T])(s.toUpperCase))
      |        .fold(
      |          _ =>
      |            sttp.tapir.DecodeResult.Error(
      |              s,
      |              new NoSuchElementException(
      |                s"Could not find value $s for enum ${enumextensions.EnumMirror[T].mirroredName}, available values: ${enumextensions.EnumMirror[T].values.mkString(", ")}"
      |              )
      |            ),
      |          sttp.tapir.DecodeResult.Value(_)
      |        )
      |    )(_.name)
      |
      |object Test {
      |  given stringListTestCodec: sttp.tapir.Codec[List[String], Test, sttp.tapir.CodecFormat.TextPlain] =
      |    makeQueryCodecForEnum[Test]
      |}
      |enum Test derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec, enumextensions.EnumMirror {
      |  case enum1, enum2
      |}""".stripMargin)
  }

  it should "generate named maps" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
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
    gen.classDefs(doc, false).get shouldCompile ()
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
      case Right(doc)  => new EndpointGenerator().endpointDefs(doc, useHeadTagForObjectNames = false).endpointDecls(None)
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
}
