package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiModels.{
  OpenapiDocument,
  OpenapiParameter,
  OpenapiPath,
  OpenapiPathMethod,
  OpenapiResponse,
  OpenapiResponseContent
}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaArray, OpenapiSchemaString}
import sttp.tapir.codegen.testutils.CompileCheckTestBase

class EndpointGeneratorSpec extends CompileCheckTestBase {

  it should "generate the endpoint defs" in {
    val doc = OpenapiDocument(
      "",
      null,
      Seq(
        OpenapiPath(
          "test/{asd-id}",
          Seq(
            OpenapiPathMethod(
              methodType = "get",
              parameters = Seq(OpenapiParameter("asd-id", "path", true, None, OpenapiSchemaString(false))),
              responses = Seq(
                OpenapiResponse(
                  "200",
                  "",
                  Seq(OpenapiResponseContent("application/json", OpenapiSchemaArray(OpenapiSchemaString(false), false)))
                ),
                OpenapiResponse("default", "", Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false))))
              ),
              requestBody = None,
              summary = None,
              tags = Some(Seq("Tag 1", "Tag 2", "Tag 1"))
            )
          )
        )
      ),
      null
    )
    val generatedCode = BasicGenerator.imports ++ new EndpointGenerator().endpointDefs(doc)
    generatedCode should include ("val getTestAsdId =")
    generatedCode shouldCompile ()
  }

}
