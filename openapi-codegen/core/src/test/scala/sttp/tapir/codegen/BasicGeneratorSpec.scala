package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.testutils.CompileCheckTestBase

class BasicGeneratorSpec extends CompileCheckTestBase {
  def genMap(
      doc: OpenapiDocument,
      useHeadTagForObjectNames: Boolean,
      jsonSerdeLib: String
  ) = {
    BasicGenerator.generateObjects(
      doc,
      "sttp.tapir.generated",
      "TapirGeneratedEndpoints",
      targetScala3 = false,
      useHeadTagForObjectNames = useHeadTagForObjectNames,
      jsonSerdeLib = jsonSerdeLib,
      validateNonDiscriminatedOneOfs = true
    )
  }
  def gen(
      doc: OpenapiDocument,
      useHeadTagForObjectNames: Boolean,
      jsonSerdeLib: String
  ) = {
    val genned = genMap(
      doc,
      useHeadTagForObjectNames = useHeadTagForObjectNames,
      jsonSerdeLib = jsonSerdeLib
    )
    val main = genned("TapirGeneratedEndpoints")
    val maybeJson = genned.get("TapirGeneratedEndpointsJsonSerdes")
    main + maybeJson.map("\n" + _).getOrElse("")
  }
  def testJsonLib(jsonSerdeLib: String) = {
    it should s"generate the bookshop example using ${jsonSerdeLib} serdes" in {
      val res = gen(TestHelpers.myBookshopDoc, useHeadTagForObjectNames = false, jsonSerdeLib = jsonSerdeLib)
//      println(res)
      res shouldCompile ()
    }

    it should s"split outputs by tag if useHeadTagForObjectNames = true using ${jsonSerdeLib} serdes" in {
      val generated = genMap(
        TestHelpers.myBookshopDoc,
        useHeadTagForObjectNames = true,
        jsonSerdeLib = jsonSerdeLib
      )
      val schemas = generated("TapirGeneratedEndpoints")
      val serdes = generated("TapirGeneratedEndpointsJsonSerdes")
      val endpoints = generated("Bookshop")
      // schema file on its own should compile
      schemas shouldCompile ()
      // schema file should contain no endpoint definitions
      schemas.linesIterator.count(_.matches("""^\s*endpoint""")) shouldEqual 0
      // schema file with serde file should compile
      (schemas + "\n" + serdes) shouldCompile ()
      // Bookshop file should contain all endpoint definitions
      endpoints.linesIterator.count(_.matches("""^\s*endpoint""")) shouldEqual 3
      // endpoint file depends on schema file. For simplicity of testing, just strip the package declaration from the
      // endpoint file, and concat the two, before testing for compilation
      (schemas + "\n" + serdes + "\n" + endpoints) shouldCompile ()
    }

    it should s"compile endpoints with enum query params using ${jsonSerdeLib} serdes" in {
      gen(TestHelpers.enumQueryParamDocs, useHeadTagForObjectNames = false, jsonSerdeLib = jsonSerdeLib) shouldCompile ()
    }

    // jsoniter fails this test with `Internal error: unable to find the outer accessor symbol of object TapirGeneratedEndpointsJsonSerdes`
    if (jsonSerdeLib != "jsoniter") it should s"compile endpoints with default params using ${jsonSerdeLib} serdes" in {
      val genWithParams = gen(TestHelpers.withDefaultsDocs, useHeadTagForObjectNames = false, jsonSerdeLib = jsonSerdeLib)

      val expectedDefaultDeclarations = Seq(
        """f1: String = "default string"""",
        """f2: Option[Int] = Some(1977)""",
        """g1: Option[java.util.UUID] = Some(java.util.UUID.fromString("default string"))""",
        """g2: Float = 1977.0f""",
        """g3: Option[AnEnum] = Some(AnEnum.v1)""",
        """g4: Option[Seq[AnEnum]] = Some(Vector(AnEnum.v1, AnEnum.v2, AnEnum.v3))""",
        """sub: Option[SubObject] = Some(SubObject(subsub = SubSubObject(value = "hi there", value2 = Some(java.util.UUID.fromString("ac8113ed-6105-4f65-a393-e88be2c5d585")))))"""
      )
      expectedDefaultDeclarations foreach (decln => genWithParams should include(decln))

      genWithParams shouldCompile ()
    }

  }
  Seq("circe", "jsoniter") foreach testJsonLib

}
