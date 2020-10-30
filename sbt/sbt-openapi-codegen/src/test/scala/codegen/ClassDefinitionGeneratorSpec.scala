package codegen

import codegen.testutils.CompileCheckTestBase

class ClassDefinitionGeneratorSpec extends CompileCheckTestBase {

  it should "generate the class defs" in {
    new ClassDefinitionGenerator().classDefs(TestHelpers.myBookshopDoc) shouldCompile ()
  }

}
