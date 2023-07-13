package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiComponent
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaArray, OpenapiSchemaMap, OpenapiSchemaObject, OpenapiSchemaString}
import sttp.tapir.codegen.testutils.CompileCheckTestBase

class ClassDefinitionGeneratorSpec extends CompileCheckTestBase {

  it should "generate the example class defs" in {
    new ClassDefinitionGenerator().classDefs(TestHelpers.myBookshopDoc) shouldCompile ()
  }

  it should "generate simple class" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
      OpenapiComponent(
        Map(
          "Test" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(false)), Seq("text"), false)
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc) shouldCompile ()
  }

  it should "generate simple class with reserved propName" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
      OpenapiComponent(
        Map(
          "Test" -> OpenapiSchemaObject(Map("type" -> OpenapiSchemaString(false)), Seq("type"), false)
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc) shouldCompile ()
  }

  it should "generate class with array" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
      OpenapiComponent(
        Map(
          "Test" -> OpenapiSchemaObject(Map("texts" -> OpenapiSchemaArray(OpenapiSchemaString(false), false)), Seq("texts"), false)
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc) shouldCompile ()
  }

  it should "generate class with map" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
      OpenapiComponent(
        Map(
          "Test" -> OpenapiSchemaObject(Map("texts" -> OpenapiSchemaMap(OpenapiSchemaString(false), false)), Seq("texts"), false)
        )
      )
    )

    new ClassDefinitionGenerator().classDefs(doc) shouldCompile ()
  }

  it should "generate class with inner class" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
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

    new ClassDefinitionGenerator().classDefs(doc) shouldCompile ()
  }

  it should "generate class with array with inner class" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
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

    new ClassDefinitionGenerator().classDefs(doc) shouldCompile ()
  }

  it should "generate class with map with inner class" in {
    val doc = OpenapiDocument(
      "",
      null,
      null,
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

    new ClassDefinitionGenerator().classDefs(doc) shouldCompile ()
  }

  it should "nonrequired and required are not the same" in {
    val doc1 = OpenapiDocument(
      "",
      null,
      null,
      OpenapiComponent(
        Map(
          "Test" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(false)), Seq.empty, false)
        )
      )
    )
    val doc2 = OpenapiDocument(
      "",
      null,
      null,
      OpenapiComponent(
        Map(
          "Test" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(false)), Seq("text"), false)
        )
      )
    )
    val gen = new ClassDefinitionGenerator()
    val res1 = gen.classDefs(doc1)
    val res2 = gen.classDefs(doc2)
    res1 shouldNot be(res2)
    res1 shouldCompile ()
    res2 shouldCompile ()
  }
  it should "nonrequired and nullable are the same" in {
    val doc1 = OpenapiDocument(
      "",
      null,
      null,
      OpenapiComponent(
        Map(
          "Test" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(false)), Seq.empty, false)
        )
      )
    )
    val doc2 = OpenapiDocument(
      "",
      null,
      null,
      OpenapiComponent(
        Map(
          "Test" -> OpenapiSchemaObject(Map("text" -> OpenapiSchemaString(true)), Seq("text"), false)
        )
      )
    )
    val gen = new ClassDefinitionGenerator()
    val res1 = gen.classDefs(doc1)
    val res2 = gen.classDefs(doc2)
    res1 shouldBe res2
  }

}
