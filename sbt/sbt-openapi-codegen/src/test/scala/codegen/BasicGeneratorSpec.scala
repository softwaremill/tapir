package codegen

import codegen.testutils.CompileCheckTestBase

class BasicGeneratorSpec extends CompileCheckTestBase {

  it should "generate the bookshop example" in {
    BasicGenerator.generateObjects(TestHelpers.myBookshopDoc, "sttp.tapir.generated", "TapirGeneratedEndpoints") shouldCompile ()
  }

}
