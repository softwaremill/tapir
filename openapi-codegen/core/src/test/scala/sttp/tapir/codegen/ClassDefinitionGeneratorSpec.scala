package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiComponent
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaConstantString,
  OpenapiSchemaEnum,
  OpenapiSchemaMap,
  OpenapiSchemaObject,
  OpenapiSchemaRef,
  OpenapiSchemaString
}
import sttp.tapir.codegen.testutils.CompileCheckTestBase

class ClassDefinitionGeneratorSpec extends CompileCheckTestBase {

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
            "Test" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(false)), Seq("text"), false)
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
            "Test" -> OpenapiSchemaEnum("string", Seq(OpenapiSchemaConstantString("paperback"), OpenapiSchemaConstantString("hardback")), false)
          )
        )
      )
    )
    // the enumeratum import should be included by the BasicGenerator iff we generated enums
    "import enumeratum._;" + (new ClassDefinitionGenerator().classDefs(doc).get) shouldCompile ()
  }

  it should "generate simple class with reserved propName" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
      Some(
        OpenapiComponent(
          Map(
            "Test" -> OpenapiSchemaObject(Map("type" -> OpenapiSchemaString(false)), Seq("type"), false)
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
            "Test" -> OpenapiSchemaObject(Map("texts" -> OpenapiSchemaArray(OpenapiSchemaString(false), false)), Seq("texts"), false)
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
            "Test" -> OpenapiSchemaObject(Map("texts" -> OpenapiSchemaMap(OpenapiSchemaString(false), false)), Seq("texts"), false)
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
                "inner" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(false)), Seq("text"), false)
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
            "Test" -> OpenapiSchemaObject(
              Map(
                "objects" -> OpenapiSchemaArray(
                  OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(false)), Seq("text"), false),
                  false
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
            "Test" -> OpenapiSchemaObject(
              Map(
                "objects" -> OpenapiSchemaMap(
                  OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(false)), Seq("text"), false),
                  false
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
            "Test" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(false)), Seq.empty, false)
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
            "Test" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(false)), Seq("text"), false)
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
            "Test" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(false)), Seq.empty, false)
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
            "Test" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(true)), Seq("text"), false)
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
    val res = gen.classDefs(doc, true)
    // can't just check whether this compiles, because our tests only run on scala 2.12 - so instead just eyeball it...
    res shouldBe Some(
      """enum Test derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec {
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
            "MyObject" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(true)), Seq("text"), false),
            "MyEnum" -> OpenapiSchemaEnum("string", Seq(OpenapiSchemaConstantString("enum1"), OpenapiSchemaConstantString("enum2")), false),
            "MyMapPrimitive" -> OpenapiSchemaMap(OpenapiSchemaString(false), false),
            "MyMapObject" -> OpenapiSchemaMap(OpenapiSchemaRef("#/components/schemas/MyObject"), false),
            "MyMapEnum" -> OpenapiSchemaMap(OpenapiSchemaRef("#/components/schemas/MyEnum"), false)
          )
        )
      )
    )

    val gen = new ClassDefinitionGenerator()
    val res = gen.classDefs(doc, false)
    "import enumeratum._;" + res.get shouldCompile ()
  }
}
