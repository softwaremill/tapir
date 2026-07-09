package sttp.tapir.codegen

import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.endpoints.{EndpointGenerator, FS2}
import sttp.tapir.codegen.json.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels._
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._
import sttp.tapir.codegen.openapi.models.{OpenapiComponent, OpenapiSchemaType}
import sttp.tapir.codegen.testutils.CompileCheckTestBase
import sttp.tapir.codegen.validation.ValidationDefns
import sttp.tapir.codegen.xml.XmlSerdeLib

import scala.collection.mutable
import scala.util.Try

/** Regression tests for GHSA-gpcc-36pq-8qxr: names/values taken from an (untrusted) OpenAPI document must not be able
  * to inject Scala code into the generated source. Names in identifier positions are rejected if unsafe; values in
  * string-literal positions are escaped.
  */
class InjectionSecuritySpec extends CompileCheckTestBase {
  private def noDefault(f: OpenapiSchemaType): OpenapiSchemaField = OpenapiSchemaField(f, None)

  private def docWithObject(schemaName: String, props: (String, OpenapiSchemaField)*): OpenapiDocument =
    OpenapiDocument(
      "",
      Nil,
      null,
      Nil,
      Some(OpenapiComponent(Map(schemaName -> OpenapiSchemaObject(mutable.LinkedHashMap(props: _*), props.map(_._1), false)))),
      Nil
    )

  private def classDefs(doc: OpenapiDocument) =
    new ClassDefinitionGenerator().classDefs(doc, targetScala3 = isScala3)

  it should "reject a schema name that is not a safe identifier" in {
    val doc = docWithObject("""Ok{Runtime.getRuntime().exec("x");0}""", "field" -> noDefault(OpenapiSchemaString(false)))
    Try(classDefs(doc)).isFailure shouldBe true
  }

  it should "reject a property name that breaks out of backtick quoting" in {
    val evil = """name`: String = {Runtime.getRuntime().exec("x");null}, pwned"""
    val doc = docWithObject("Ok", evil -> noDefault(OpenapiSchemaString(false)))
    Try(classDefs(doc)).isFailure shouldBe true
  }

  it should "reject a property name containing injection characters" in {
    val doc = docWithObject("Ok", """x: String = ""); sys.exit(0); val y = ((""" -> noDefault(OpenapiSchemaString(false)))
    Try(classDefs(doc)).isFailure shouldBe true
  }

  it should "reject an enum value that breaks out of backtick quoting" in {
    val evilEnum = OpenapiSchemaEnum("string", Seq(OpenapiSchemaConstantString("""a`; sys.exit(0); val x = `b""")), false)
    val doc = docWithObject("Ok", "color" -> noDefault(evilEnum))
    Try(classDefs(doc)).isFailure shouldBe true
  }

  it should "accept an ordinary schema with a reserved-word and hyphenated property name (quoted, not rejected)" in {
    val doc = docWithObject("Ok", "type" -> noDefault(OpenapiSchemaString(false)), "x-trace" -> noDefault(OpenapiSchemaString(false)))
    val out = classDefs(doc).get.classRepr
    out should include("`type`")
    out should include("`x-trace`")
    out.shouldCompile()
  }

  private def endpointWithParam(param: OpenapiParameter): OpenapiDocument =
    OpenapiDocument(
      "",
      Nil,
      null,
      Seq(
        OpenapiPath(
          "evil",
          Seq(
            OpenapiPathMethod(
              methodType = "get",
              parameters = Seq(Resolved(param)),
              responses = Seq(OpenapiResponseDef("200", "", Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false))))),
              requestBody = None,
              summary = None
            )
          )
        )
      ),
      null,
      Nil
    )

  private def endpointDecls(doc: OpenapiDocument): String =
    RootGenerator.imports(JsonSerdeLib.Circe) +
      new EndpointGenerator()
        .endpointDefs(
          doc,
          useHeadTagForObjectNames = false,
          targetScala3 = isScala3,
          jsonSerdeLib = JsonSerdeLib.Circe,
          xmlSerdeLib = XmlSerdeLib.CatsXml,
          streamingImplementation = FS2(),
          generateEndpointTypes = false,
          validators = ValidationDefns.empty,
          generateValidators = true,
          packageReuse = PackageReuseContext.none,
          seperateFilesForModels = false
        )
        .endpointDecls(None)

  it should "escape a query parameter name so it cannot break out of the string literal" in {
    val evil = """q") ; sys.error("PWNED") ; val _z = query[String]("z"""
    val out = endpointDecls(endpointWithParam(OpenapiParameter(evil, "query", Some(false), None, OpenapiSchemaString(false))))
    out should not include "sys.error(\"PWNED\")"
    out.shouldCompile()
  }

  it should "reject a parameter with an unsupported 'in' location" in {
    val doc = endpointWithParam(OpenapiParameter("q", """query[String]("x")) ; sys.exit(0) ; endpoint.in(query[String]("y""", Some(false), None, OpenapiSchemaString(false)))
    Try(endpointDecls(doc)).isFailure shouldBe true
  }
}
