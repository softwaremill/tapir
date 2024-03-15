package sttp.tapir.codegen

import sttp.tapir.codegen.testutils.CompileCheckTestBase

class BasicGeneratorSpec extends CompileCheckTestBase {
  def testJsonLib(jsonSerdeLib: String) = {
    it should s"generate the bookshop example using ${jsonSerdeLib} serdes" in {
      BasicGenerator.generateObjects(
        TestHelpers.myBookshopDoc,
        "sttp.tapir.generated",
        "TapirGeneratedEndpoints",
        targetScala3 = false,
        useHeadTagForObjectNames = false,
        jsonSerdeLib = jsonSerdeLib
      )("TapirGeneratedEndpoints") shouldCompile ()
    }

    it should s"split outputs by tag if useHeadTagForObjectNames = true using ${jsonSerdeLib} serdes" in {
      val generated = BasicGenerator.generateObjects(
        TestHelpers.myBookshopDoc,
        "sttp.tapir.generated",
        "TapirGeneratedEndpoints",
        targetScala3 = false,
        useHeadTagForObjectNames = true,
        jsonSerdeLib = jsonSerdeLib
      )
      val schemas = generated("TapirGeneratedEndpoints")
      val endpoints = generated("Bookshop")
      // schema file on its own should compile
      schemas shouldCompile ()
      // schema file should contain no endpoint definitions
      schemas.linesIterator.count(_.matches("""^\s*endpoint""")) shouldEqual 0
      // Bookshop file should contain all endpoint definitions
      endpoints.linesIterator.count(_.matches("""^\s*endpoint""")) shouldEqual 3
      // endpoint file depends on schema file. For simplicity of testing, just strip the package declaration from the
      // endpoint file, and concat the two, before testing for compilation
      (schemas + "\n" + (endpoints.linesIterator.filterNot(_ startsWith "package").mkString("\n"))) shouldCompile ()
    }

    it should s"compile endpoints with enum query params using ${jsonSerdeLib} serdes" in {
      BasicGenerator.generateObjects(
        TestHelpers.enumQueryParamDocs,
        "sttp.tapir.generated",
        "TapirGeneratedEndpoints",
        targetScala3 = false,
        useHeadTagForObjectNames = false,
        jsonSerdeLib = jsonSerdeLib
      )("TapirGeneratedEndpoints") shouldCompile ()
    }

    it should s"compile endpoints with default params using ${jsonSerdeLib} serdes" in {
      val genWithParams = BasicGenerator.generateObjects(
        TestHelpers.withDefaultsDocs,
        "sttp.tapir.generated",
        "TapirGeneratedEndpoints",
        targetScala3 = false,
        useHeadTagForObjectNames = false,
        jsonSerdeLib = jsonSerdeLib
      )("TapirGeneratedEndpoints")

      val expectedDefaultDeclarations = Seq(
        """f1: String = "default string"""",
        """f2: Option[Int] = Some(1977)""",
        """g1: Option[java.util.UUID] = Some(java.util.UUID.fromString("default string"))""",
        """g2: Float = 1977f""",
        """g3: Option[AnEnum] = Some(AnEnum.v1)""",
        """g4: Option[Seq[AnEnum]] = Some(Vector(AnEnum.v1, AnEnum.v2, AnEnum.v3))"""
      )
      expectedDefaultDeclarations foreach (decln => genWithParams should include(decln))

      genWithParams shouldCompile ()
    }

  }
  Seq("circe", "jsoniter") foreach testJsonLib

}
