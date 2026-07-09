package sttp.tapir.codegen

import io.circe.Json
import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.endpoints.{EndpointGenerator, FS2}
import sttp.tapir.codegen.json.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels._
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._
import sttp.tapir.codegen.openapi.models.{OpenapiComponent, OpenapiSchemaType, OpenapiServer}
import sttp.tapir.codegen.testutils.CompileCheckTestBase
import sttp.tapir.codegen.validation.ValidationDefns
import sttp.tapir.codegen.xml.XmlSerdeLib

import scala.collection.mutable

/** Regression tests for GHSA-gpcc-36pq-8qxr: names/values taken from an (untrusted) OpenAPI document must not be able
  * to inject Scala code into the generated source. Names in raw identifier positions are rejected if unsafe; names
  * that are only backtick-quoted, and values in string-literal positions, must survive as inert data.
  *
  * Rejection tests assert the specific `IllegalArgumentException` (all our guards mention the advisory id) rather than
  * `Try(...).isFailure`, so they cannot pass because of an unrelated failure.
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

  private def classRepr(doc: OpenapiDocument): String =
    new ClassDefinitionGenerator().classDefs(doc, targetScala3 = isScala3, jsonSerdeLib = JsonSerdeLib.Circe).get.classRepr

  // Every injection guard's message mentions the advisory id; class-generation wraps it in a NotImplementedError, so
  // we catch Throwable and assert the guard fired (rather than an unrelated failure) via the message.
  private def rejected(doc: => OpenapiDocument): Unit =
    intercept[Throwable](classRepr(doc)).getMessage should include("GHSA-gpcc")

  // --- identifier positions: reject unsafe names ---

  it should "reject a schema name that is not a safe identifier" in {
    rejected(docWithObject("""Ok{Runtime.getRuntime().exec("x");0}""", "field" -> noDefault(OpenapiSchemaString(false))))
  }

  it should "reject a property name that would break out of backtick quoting" in {
    val evil = """name`: String = {Runtime.getRuntime().exec("x");null}, pwned"""
    rejected(docWithObject("Ok", evil -> noDefault(OpenapiSchemaString(false))))
  }

  it should "reject a container-typed property name that reaches a derived class identifier" in {
    val evil = """items: Seq[String] = Nil){ System.exit(0) }; case class X("""
    val arr = OpenapiSchemaArray(OpenapiSchemaString(false), false)
    rejected(docWithObject("Ok", evil -> noDefault(arr)))
  }

  it should "reject an enum value that would break out of backtick quoting" in {
    val evilEnum = OpenapiSchemaEnum("string", Seq(OpenapiSchemaConstantString("""a`; sys.exit(0); val x = `b""")), false)
    rejected(docWithObject("Ok", "color" -> noDefault(evilEnum)))
  }

  it should "reject a parameter with an unsupported 'in' location" in {
    val evilIn = """query[String]("x")) ; sys.exit(0) ; endpoint.in(query[String]("y"""
    val ex = intercept[Throwable](
      endpointDecls(endpointWithParam(OpenapiParameter("q", evilIn, Some(false), None, OpenapiSchemaString(false))))
    )
    ex.getMessage should include("GHSA-gpcc")
  }

  // --- identifier positions: legitimate but non-trivial names still work (regression guards) ---

  it should "backtick-quote reserved-word and hyphenated property names rather than reject them" in {
    val out = classRepr(docWithObject("Ok", "type" -> noDefault(OpenapiSchemaString(false)), "x-trace" -> noDefault(OpenapiSchemaString(false))))
    out should include("`type`")
    out should include("`x-trace`")
    out.shouldCompile()
  }

  it should "accept simple-typed property names with '@', dots, spaces and non-ASCII letters (backtick-quoted, not executed)" in {
    val out = classRepr(
      docWithObject(
        "Ok",
        "@odata.type" -> noDefault(OpenapiSchemaString(false)),
        "first name" -> noDefault(OpenapiSchemaString(false)),
        "名前" -> noDefault(OpenapiSchemaString(false))
      )
    )
    out should include("`@odata.type`")
    out.shouldCompile()
  }

  it should "safely quote (not execute) a simple-typed property name containing injection characters" in {
    val evil = """x: String = ""); sys.error("PWNED"); val y = (("""
    val out = classRepr(docWithObject("Ok", evil -> noDefault(OpenapiSchemaString(false))))
    // The payload survives only as the content of a single backtick-quoted field identifier, so it is inert.
    out should include("`" + evil + "`")
    out.shouldCompile()
  }

  // --- string-literal positions: escape values ---

  it should "escape a query parameter name so it survives as data and cannot break out of the string literal" in {
    val evil = """q") ; sys.error("PWNED") ; val _z = query[String]("z"""
    val out = endpointDecls(endpointWithParam(OpenapiParameter(evil, "query", Some(false), None, OpenapiSchemaString(false))))
    out should include("""q\") ; sys.error(\"PWNED\")""") // escaped form present
    out should not include """"q") ; sys.error("PWNED")""" // but not as live code
    out.shouldCompile()
  }

  it should "escape discriminator property names and mapping values in generated serdes" in {
    val evilProp = """k"); System.exit(0); ("""" // discriminator propertyName
    val evilValue = """d"); System.exit(0); ("""" // discriminator mapping value (wire tag)
    val yaml =
      s"""openapi: 3.1.0
         |info: {title: t, version: '1.0'}
         |paths: {}
         |components:
         |  schemas:
         |    Animal:
         |      oneOf:
         |        - $$ref: '#/components/schemas/Dog'
         |      discriminator:
         |        propertyName: '$evilProp'
         |        mapping:
         |          '$evilValue': '#/components/schemas/Dog'
         |    Dog:
         |      type: object
         |      required: ['$evilProp']
         |      properties:
         |        '$evilProp':
         |          type: string
         |""".stripMargin
    val doc = YamlParser.parseFile(yaml).fold(e => fail(e.getMessage), identity).resolveAllOfSchemas
    val gen = new ClassDefinitionGenerator().classDefs(doc, targetScala3 = isScala3, jsonSerdeLib = JsonSerdeLib.Circe, jsonParamRefs = Set("Animal", "Dog")).get
    val out = gen.classRepr + "\n" + gen.jsonSerdeRepr.getOrElse("")
    // The payload's quotes are escaped (`\"`), so it stays a single inert string literal in both the discriminator
    // field body and the circe decoder's downField(...) rather than breaking out into code.
    out should include("""\"); System.exit(0); (\"""")
  }

  it should "escape a string default value so it cannot inject at model construction" in {
    val evilDefault = OpenapiSchemaField(OpenapiSchemaString(false), Some(Json.fromString("""d"; sys.error("PWNED"); "x""")))
    val out = classRepr(docWithObject("Ok", "field" -> evilDefault))
    out should not include """sys.error("PWNED")"""
    out.shouldCompile()
  }

  it should "guard an inline request-body property name (rejecting a backtick break-out) rather than emit it raw" in {
    // A backtick in the name cannot be safely quoted, so the inline-body path must reject it like the component path.
    val evil = """x`: String = {System.exit(0)}, y"""
    val doc = OpenapiDocument(
      "",
      Nil,
      null,
      Seq(
        OpenapiPath(
          "post-it",
          Seq(
            OpenapiPathMethod(
              methodType = "post",
              parameters = Nil,
              responses = Seq(OpenapiResponseDef("200", "", Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false))))),
              requestBody = Some(
                OpenapiRequestBodyDefn(
                  required = true,
                  description = None,
                  content = Seq(
                    OpenapiRequestBodyContent(
                      "application/json",
                      OpenapiSchemaObject(mutable.LinkedHashMap(evil -> noDefault(OpenapiSchemaString(false))), Nil, false)
                    )
                  )
                )
              ),
              summary = None
            )
          )
        )
      ),
      null,
      Nil
    )
    intercept[Throwable](endpointDecls(doc)).getMessage should include("GHSA-gpcc")
  }

  it should "not let a server description close the generated block comment" in {
    val out = ServersGenerator
      .genServerDefinitions(Seq(OpenapiServer("https://example.com", description = Some("""*/ ; System.exit(0) ; /*"""))), isScala3)
      .get
    // The description lives inside a /* ... */ block; its `*/` must be broken so it cannot close the comment early.
    out should not include "*/ ; System.exit(0)"
    out should include("* / ; System.exit(0)")
  }

  it should "reject a server URL containing injection characters" in {
    val ex = intercept[IllegalArgumentException](
      ServersGenerator.genServerDefinitions(Seq(OpenapiServer("""https://x"+System.exit(0)+"""")), isScala3)
    )
    ex.getMessage should include("GHSA-gpcc")
  }

  // --- helpers for endpoint generation ---

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
}
