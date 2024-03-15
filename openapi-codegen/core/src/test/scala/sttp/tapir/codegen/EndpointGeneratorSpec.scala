package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiComponent
import sttp.tapir.codegen.openapi.models.OpenapiModels.{
  OpenapiDocument,
  OpenapiParameter,
  OpenapiPath,
  OpenapiPathMethod,
  OpenapiRequestBody,
  OpenapiRequestBodyContent,
  OpenapiResponse,
  OpenapiResponseContent,
  Resolved
}
import sttp.tapir.codegen.openapi.models.OpenapiSecuritySchemeType.{
  OpenapiSecuritySchemeBearerType,
  OpenapiSecuritySchemeBasicType,
  OpenapiSecuritySchemeApiKeyType
}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaBinary,
  OpenapiSchemaObject,
  OpenapiSchemaRef,
  OpenapiSchemaString
}
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
              parameters = Seq(Resolved(OpenapiParameter("asd-id", "path", Some(true), None, OpenapiSchemaString(false)))),
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
    val generatedCode =
      BasicGenerator.imports ++ new EndpointGenerator().endpointDefs(doc, useHeadTagForObjectNames = false).endpointDecls(None)
    generatedCode should include("val getTestAsdId =")
    generatedCode shouldCompile ()
  }

  it should "generete endpoints defs with security" in {
    val doc = OpenapiDocument(
      "",
      null,
      Seq(
        OpenapiPath(
          "test",
          Seq(
            OpenapiPathMethod(
              methodType = "get",
              parameters = Seq(),
              responses = Seq(),
              requestBody = None,
              security = Seq(Seq("httpBearer")),
              summary = None,
              tags = None
            ),
            OpenapiPathMethod(
              methodType = "post",
              parameters = Seq(),
              responses = Seq(),
              requestBody = None,
              security = Seq(Seq("httpBasic")),
              summary = None,
              tags = None
            ),
            OpenapiPathMethod(
              methodType = "put",
              parameters = Seq(),
              responses = Seq(),
              requestBody = None,
              security = Seq(Seq("apiKeyHeader")),
              summary = None,
              tags = None
            ),
            OpenapiPathMethod(
              methodType = "patch",
              parameters = Seq(),
              responses = Seq(),
              requestBody = None,
              security = Seq(Seq("apiKeyCookie")),
              summary = None,
              tags = None
            ),
            OpenapiPathMethod(
              methodType = "delete",
              parameters = Seq(),
              responses = Seq(),
              requestBody = None,
              security = Seq(Seq("apiKeyQuery")),
              summary = None,
              tags = None
            )
          )
        )
      ),
      Some(
        OpenapiComponent(
          Map(),
          Map(
            "httpBearer" -> OpenapiSecuritySchemeBearerType,
            "httpBasic" -> OpenapiSecuritySchemeBasicType,
            "apiKeyHeader" -> OpenapiSecuritySchemeApiKeyType("header", "X-API-KEY"),
            "apiKeyCookie" -> OpenapiSecuritySchemeApiKeyType("cookie", "api_key"),
            "apiKeyQuery" -> OpenapiSecuritySchemeApiKeyType("query", "api-key")
          )
        )
      )
    )
    BasicGenerator.imports ++
      new EndpointGenerator().endpointDefs(doc, useHeadTagForObjectNames = false).endpointDecls(None) shouldCompile ()
  }

  it should "handle status codes" in {
    val doc = OpenapiDocument(
      "",
      null,
      Seq(
        OpenapiPath(
          "find/{id}",
          Seq(
            OpenapiPathMethod(
              methodType = "get",
              parameters = Seq(Resolved(OpenapiParameter("id", "path", Some(true), None, OpenapiSchemaString(true)))),
              responses = Seq(
                OpenapiResponse("202", "Processing", Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false)))),
                OpenapiResponse("404", "couldn't find thing", Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false))))
              ),
              requestBody = None,
              summary = None,
              tags = Some(Seq("Tag 1", "Tag 2", "Tag 1"))
            )
          )
        ),
        OpenapiPath(
          "find_v2/{id}",
          Seq(
            OpenapiPathMethod(
              methodType = "get",
              parameters = Seq(Resolved(OpenapiParameter("id", "path", Some(true), None, OpenapiSchemaString(true)))),
              responses = Seq(
                OpenapiResponse("204", "No body", Nil),
                OpenapiResponse("403", "Not authorised", Nil)
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
    val generatedCode =
      BasicGenerator.imports ++ new EndpointGenerator().endpointDefs(doc, useHeadTagForObjectNames = false).endpointDecls(None)
    generatedCode should include(
      """.out(stringBody.description("Processing").and(statusCode(sttp.model.StatusCode(202))))"""
    ) // status code with body
    generatedCode should include(
      """.errorOut(stringBody.description("couldn't find thing").and(statusCode(sttp.model.StatusCode(404))))"""
    ) // error status code with body
    generatedCode should include(
      """.errorOut(statusCode(sttp.model.StatusCode(403)).description("Not authorised"))"""
    ) // error status code, no body
    generatedCode should include(""".out(statusCode(sttp.model.StatusCode(204)).description("No body"))""") // status code, no body
    generatedCode shouldCompile ()
  }

  it should "support multipart body" in {
    val doc = OpenapiDocument(
      "",
      null,
      Seq(
        OpenapiPath(
          "file",
          Seq(
            OpenapiPathMethod(
              methodType = "post",
              parameters = Seq(),
              responses = Seq(OpenapiResponse("204", "No body", Nil)),
              requestBody = Some(
                OpenapiRequestBody(
                  required = true,
                  description = None,
                  content = Seq(
                    OpenapiRequestBodyContent(
                      contentType = "multipart/form-data",
                      schema = OpenapiSchemaRef("#/components/schemas/FileUpload")
                    )
                  )
                )
              )
            )
          )
        )
      ),
      Some(
        OpenapiComponent(
          schemas = Map(
            "FileUpload" -> OpenapiSchemaObject(
              properties = Map(
                "file" -> OpenapiSchemaBinary(false)
              ),
              required = Seq("file"),
              nullable = false
            )
          )
        )
      )
    )
    val generatedCode = BasicGenerator.generateObjects(
      doc,
      "sttp.tapir.generated",
      "TapirGeneratedEndpoints",
      targetScala3 = false,
      useHeadTagForObjectNames = false
    )("TapirGeneratedEndpoints")
    generatedCode should include(
      """file: sttp.model.Part[java.io.File]"""
    )
    generatedCode should include(
      """.in(multipartBody[FileUpload])"""
    )
    generatedCode shouldCompile ()
  }
}
