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

}
