package sttp.tapir.codegen

import sttp.tapir.codegen.testutils.CompileCheckTestBase

class BasicGeneratorSpec extends CompileCheckTestBase {

  it should "generate the bookshop example" in {
    BasicGenerator.generateObjects(
      TestHelpers.myBookshopDoc,
      "sttp.tapir.generated",
      "TapirGeneratedEndpoints",
      targetScala3 = false,
      useHeadTagForObjectNames = false
    )("TapirGeneratedEndpoints") shouldCompile ()
  }

  it should "split outputs by tag if useHeadTagForObjectNames = true" in {
    val generated = BasicGenerator.generateObjects(
      TestHelpers.myBookshopDoc,
      "sttp.tapir.generated",
      "TapirGeneratedEndpoints",
      targetScala3 = false,
      useHeadTagForObjectNames = true
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

  it should "compile endpoints with enum query params" in {
    BasicGenerator.generateObjects(
      TestHelpers.enumQueryParamDocs,
      "sttp.tapir.generated",
      "TapirGeneratedEndpoints",
      targetScala3 = false,
      useHeadTagForObjectNames = false
    )("TapirGeneratedEndpoints") shouldCompile ()
  }

}
