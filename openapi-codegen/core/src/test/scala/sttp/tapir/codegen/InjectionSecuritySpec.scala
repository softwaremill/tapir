package sttp.tapir.codegen

import io.circe.Json
import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.endpoints.{EndpointGenerator, FS2}
import sttp.tapir.codegen.json.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels._
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._
import sttp.tapir.codegen.openapi.models.OpenapiSecuritySchemeType.{
  OAuth2Flow,
  OAuth2FlowType,
  OpenapiSecuritySchemeApiKeyType,
  OpenapiSecuritySchemeOAuth2Type
}
import sttp.tapir.codegen.openapi.models.{
  OpenapiComponent,
  OpenapiSchemaType,
  OpenapiSecuritySchemeType,
  OpenapiServer,
  OpenapiServerEnum,
  OpenapiXml
}
import sttp.tapir.codegen.testutils.CompileCheckTestBase
import sttp.tapir.codegen.util.NameValidation
import sttp.tapir.codegen.validation.{ValidationDefns, ValidationGenerator}
import sttp.tapir.codegen.xml.XmlSerdeLib

import scala.collection.mutable

/** Regression tests for GHSA-gpcc-36pq-8qxr: names/values taken from an (untrusted) OpenAPI document must not be able to inject Scala code
  * into the generated source. Names in raw identifier positions are rejected if unsafe; names that are only backtick-quoted, and values in
  * string-literal positions, must survive as inert data.
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

  // Every injection guard's message mentions the advisory id. Some guards throw directly (NameValidation, before the
  // class-generation try/catch); others (safeVariableName/safeEnumMemberName inside generateClass) are re-wrapped in a
  // NotImplementedError that carries the original message. So we catch Throwable and assert on the message, which
  // distinguishes "an injection guard fired" from an unrelated failure.
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

  it should "reject an object-typed property name that reaches a derived nested class identifier via addName" in {
    val evil = """items){ System.exit(0) }; case class X("""
    val nestedObj = OpenapiSchemaObject(mutable.LinkedHashMap("a" -> noDefault(OpenapiSchemaString(false))), Seq("a"), false)
    rejected(docWithObject("Ok", evil -> noDefault(nestedObj)))
  }

  it should "reject a $ref-typed property name that reaches a derived val identifier in the XML serde" in {
    // OpenapiSchemaRef IS a simple type, but ref-typed property names still reach a raw `${n.capitalize}` XML val name.
    val evil = """x = { System.exit(0) }; val y"""
    val doc = OpenapiDocument(
      "",
      Nil,
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map(
            "Color" -> OpenapiSchemaEnum("string", Seq(OpenapiSchemaConstantString("red")), false),
            "Foo" -> OpenapiSchemaObject(
              mutable.LinkedHashMap(evil -> noDefault(OpenapiSchemaRef("#/components/schemas/Color"))),
              Seq(evil),
              false
            )
          )
        )
      ),
      Nil
    )
    rejected(doc)
  }

  it should "reject an array-of-scalar property name that reaches a derived val identifier in the XML serde" in {
    // array/map-of-scalar property names are emitted raw as `${n.capitalize}` codec val names in the XML generator.
    val evil = """items; System.exit(0); val x = "y"""
    rejected(docWithObject("Ok", evil -> noDefault(OpenapiSchemaArray(OpenapiSchemaString(false), false))))
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

  it should "backtick-quote in-charset non-identifier property names (reserved words, dots, hyphens, +, $) rather than reject them" in {
    // Names within the permitted [A-Za-z0-9._$+-] set that are not plain Scala identifiers are backtick-quoted, not
    // rejected. `+1` is from GitHub's official REST spec (reaction-rollup); `$type` from .NET-emitted specs.
    val out = classRepr(
      docWithObject(
        "Ok",
        "type" -> noDefault(OpenapiSchemaString(false)),
        "x-trace" -> noDefault(OpenapiSchemaString(false)),
        "a.b" -> noDefault(OpenapiSchemaString(false)),
        "+1" -> noDefault(OpenapiSchemaString(false)),
        "$type" -> noDefault(OpenapiSchemaString(false))
      )
    )
    out should include("`type`")
    out should include("`x-trace`")
    out should include("`a.b`")
    out should include("`+1`")
    out should include("$type") // `$` is a valid Scala identifier char, so this one is emitted unquoted
    out.shouldCompile()
  }

  it should "accept in-charset non-identifier names on object-typed and validated properties (addName / validator paths)" in {
    // Covers the raw-identifier accept paths (not just the backtick-quoted field): an object-typed property reaches a
    // derived nested class name via addName, and a restricted scalar reaches a validator val name. addName must turn
    // every in-charset name (incl. `.`/`-`/`+`) into a valid identifier so generation doesn't emit non-compiling code.
    def nested = OpenapiSchemaObject(mutable.LinkedHashMap("a" -> noDefault(OpenapiSchemaString(false))), Seq("a"), false)
    val doc = docWithObject(
      "Order",
      "shipping-address" -> noDefault(nested),
      "meta.data" -> noDefault(nested),
      "score+1" -> noDefault(nested),
      "order-ref" -> noDefault(OpenapiSchemaString(false, minLength = Some(1)))
    )
    classRepr(doc).shouldCompile()
    ValidationGenerator.mkValidators(doc).render(PackageReuseContext.none) should include("OrderOrderRefValidator")
  }

  it should "reject property names with characters outside the safe set (space, '@', non-ASCII, injection chars)" in {
    // Fail-closed: any property name reaches a raw identifier somewhere (nested class / XML val / validator val), so
    // every property name is restricted at ingestion — even ones that would only ever be a backtick-quoted field.
    rejected(docWithObject("Ok", "@odata.type" -> noDefault(OpenapiSchemaString(false))))
    rejected(docWithObject("Ok", "first name" -> noDefault(OpenapiSchemaString(false))))
    rejected(docWithObject("Ok", "名前" -> noDefault(OpenapiSchemaString(false))))
    rejected(docWithObject("Ok", """x = ""); sys.error("PWNED"); val y = ((""" -> noDefault(OpenapiSchemaString(false))))
  }

  it should "not let a validated scalar property name inject into the generated validator" in {
    // Defense-in-depth: NameValidation would reject this name in the full generateObjects/classDefs flow, but the
    // ValidationGenerator itself must also neutralise the raw `val` name and `obj.<field>` access it emits.
    val evil = "evil ; System.exit(0) ; val boom"
    val doc = docWithObject("Obj", evil -> noDefault(OpenapiSchemaString(false, minLength = Some(1))))
    val validators = ValidationGenerator.mkValidators(doc).render(PackageReuseContext.none)
    // The validator `val` name is a single stripped identifier — no statement break-out.
    validators should include("ObjEvilSystemExit0ValBoomValidator")
    // The field access is backtick-quoted to match the (safe) case-class field, not spliced raw.
    validators should include("""obj.`evil ; System.exit(0) ; val boom`""")
  }

  it should "reject a $ref target spliced raw as a type identifier, whichever path schema it comes from" in {
    // mapSchemaSimpleTypeToType splices a $ref target raw as a type; it is the single choke point through which every
    // ref (method- and path-level parameters, resolved or component-indirected, bodies, headers) becomes a type, and
    // it validates the target. Assert via the FULL generator (endpointDecls) so the guard is exercised at the sink,
    // not just at ingestion — this covers routes the NameValidation walk does not enumerate (e.g. path-shared params).
    val evil = """Int]("z") ; System.exit(0) ; val x = query[Int"""
    def paramWithRef = Resolved(OpenapiParameter("q", "query", Some(false), None, OpenapiSchemaRef("#/components/schemas/" + evil)))
    val comps = Some(OpenapiComponent(Map.empty))
    // method-level parameter
    intercept[Throwable](
      endpointDecls(singlePathDoc(getMethod(parameters = Seq(paramWithRef)), components = comps))
    ).getMessage should include(
      "GHSA-gpcc"
    )
    // path-item-level (shared) parameter — merged into methods only at generation time, after ingestion validation
    val pathShared = OpenapiDocument("", Nil, null, Seq(OpenapiPath("p", Seq(getMethod()), Seq(paramWithRef))), comps, Nil)
    intercept[Throwable](endpointDecls(pathShared)).getMessage should include("GHSA-gpcc")
  }

  // --- string-literal positions: escape values ---

  it should "escape a query parameter name so it survives as data and cannot break out of the string literal" in {
    val evil = """q") ; sys.error("PWNED") ; val _z = query[String]("z"""
    val out = endpointDecls(endpointWithParam(OpenapiParameter(evil, "query", Some(false), None, OpenapiSchemaString(false))))
    out should include("""q\") ; sys.error(\"PWNED\")""") // escaped form present
    out should not include """"q") ; sys.error("PWNED")""" // but not as live code
    out.shouldCompile()
  }

  it should "escape discriminator mapping values in generated serdes" in {
    // The discriminator propertyName is also a property name (rejected at ingestion if unsafe), so the injectable
    // wire value here is the mapping key/value, which is a string literal escaped at each serde/schema emit site.
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
         |        propertyName: kind
         |        mapping:
         |          '$evilValue': '#/components/schemas/Dog'
         |    Dog:
         |      type: object
         |      required: ['kind']
         |      properties:
         |        kind:
         |          type: string
         |""".stripMargin
    val doc = YamlParser.parseFile(yaml).fold(e => fail(e.getMessage), identity).resolveAllOfSchemas
    // Each JSON serde lib has its own hand-escaped discriminator emit site; the tapir Schema (SchemaGenerator) is a
    // fourth. Exercise all of them.
    List(JsonSerdeLib.Circe, JsonSerdeLib.Jsoniter, JsonSerdeLib.Zio).foreach { lib =>
      val gen = new ClassDefinitionGenerator()
        .classDefs(doc, targetScala3 = isScala3, jsonSerdeLib = lib, jsonParamRefs = Set("Animal"))
        .get
      val escaped = """\"); System.exit(0); (\""""
      // The payload's quotes are escaped (`\"`), so it stays a single inert string literal, rather than breaking out
      // into code. Assert on EACH emit site independently (not the concatenation) so a regression confined to one
      // serde/schema site is caught rather than masked by the lib-independent class body.
      withClue(s"serde lib $lib serde: ") { gen.jsonSerdeRepr.getOrElse("") should include(escaped) }
      withClue(s"serde lib $lib schema: ") { gen.schemaRepr.map(_._2).mkString("\n") should include(escaped) }
      withClue(s"serde lib $lib class: ") { gen.classRepr should include(escaped) }
    }
    // The class defn (incl. the discriminator field body `def ... = "<escaped value>"`) compiles, proving that sink
    // holds the payload as inert data rather than injected code.
    new ClassDefinitionGenerator().classDefs(doc, targetScala3 = isScala3, jsonSerdeLib = JsonSerdeLib.Circe).get.classRepr.shouldCompile()
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
    val body = OpenapiRequestBodyDefn(
      required = true,
      description = None,
      content = Seq(
        OpenapiRequestBodyContent(
          "application/json",
          OpenapiSchemaObject(mutable.LinkedHashMap(evil -> noDefault(OpenapiSchemaString(false))), Nil, false)
        )
      )
    )
    val doc = singlePathDoc(getMethod(requestBody = Some(body), methodType = "post"))
    intercept[Throwable](endpointDecls(doc)).getMessage should include("GHSA-gpcc")
  }

  it should "escape endpoint tags so they cannot break out of the .tags(List(...)) literal" in {
    val evil = """pwned")); System.exit(0); //"""
    val out = endpointDecls(singlePathDoc(getMethod(tags = Some(Seq(evil)))))
    out should include("""pwned\")); System.exit(0); //""") // escaped form present
    out should not include """.tags(List("pwned")); System.exit(0)""" // not a live break-out
    out.shouldCompile()
  }

  it should "escape a literal URL path segment so it cannot break out of the .in(\"...\") literal" in {
    val evil = """foo") ; System.exit(0) ; ("bar"""
    val out = endpointDecls(singlePathDoc(getMethod(), path = evil))
    out should include("""foo\") ; System.exit(0) ; (\"bar""") // escaped form present
    out should not include """("foo") ; System.exit(0) ; ("bar""" // not a live break-out
    out.shouldCompile()
  }

  it should "reject a content type that would break out of the generated CodecFormat identifier" in {
    val evilCt = "application/x`;System.exit(0);`json"
    val doc = singlePathDoc(
      getMethod(responses = Seq(OpenapiResponseDef("200", "", Seq(OpenapiResponseContent(evilCt, OpenapiSchemaString(false))))))
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

  it should "reject a security-scheme name that reaches a raw class identifier" in {
    val evil = """A(v:String)extends AnyRef}; object Evil{ System.exit(0) }; case class B"""
    val doc = OpenapiDocument(
      "",
      Nil,
      null,
      Nil,
      Some(
        OpenapiComponent(
          Map("Ok" -> OpenapiSchemaObject(mutable.LinkedHashMap("f" -> noDefault(OpenapiSchemaString(false))), Seq("f"), false)),
          Map(evil -> OpenapiSecuritySchemeApiKeyType("header", "X-A"))
        )
      ),
      Nil
    )
    rejected(doc)
  }

  it should "escape OAuth2 flow URLs so they cannot break out of the string literal" in {
    val evil = """https://x") ; sys.error("PWNED") ; auth.oauth2.implicitFlow("z"""
    val flows = Map[OAuth2FlowType.OAuth2FlowType, OAuth2Flow](
      OAuth2FlowType.authorizationCode -> OAuth2Flow(Some(evil), Some("https://token"), None, Map.empty)
    )
    val doc = singlePathDoc(
      getMethod(security = Some(Seq(Map("oauth2" -> Seq())))),
      components = Some(OpenapiComponent(Map(), Map("oauth2" -> OpenapiSecuritySchemeOAuth2Type(flows))))
    )
    val out = endpointDecls(doc)
    out should include("""https://x\") ; sys.error(\"PWNED\")""") // escaped form present
    out should not include """authorizationCodeFlow("https://x") ; sys.error("PWNED")""" // not a live break-out
    out.shouldCompile()
  }

  it should "escape a server-variable default so it cannot break out of the string literal" in {
    val evil = """v" ; System.exit(0) ; val x = " """
    val server = OpenapiServer("https://{env}.example.com", variables = Map("env" -> OpenapiServerEnum(Nil, Some(evil))))
    val out = ServersGenerator.genServerDefinitions(Seq(server), isScala3).get
    out should include("""v\" ; System.exit(0)""") // escaped form present
    out should not include """= "v" ; System.exit(0)""" // not a live break-out
  }

  it should "escape an apiKey header name so it cannot break out of the string literal" in {
    val evil = """k") ; sys.error("PWNED") ; auth.apiKey(query[String]("z"""
    val doc = singlePathDoc(
      getMethod(security = Some(Seq(Map("apiKeyHeader" -> Seq())))),
      components = Some(OpenapiComponent(Map(), Map("apiKeyHeader" -> OpenapiSecuritySchemeApiKeyType("header", evil))))
    )
    val out = endpointDecls(doc)
    out should include("""k\") ; sys.error(\"PWNED\")""") // escaped form present
    out should not include """auth.apiKey(header[String]("k") ; sys.error("PWNED")""" // not a live break-out
    out.shouldCompile()
  }

  it should "escape XML element and item names so they cannot break out of the string literal" in {
    val evilName = """el") ; sys.error("NAME") ; seqDecoder[String]("z"""
    val evilItem = """it") ; sys.error("ITEM") ; seqEncoder[String]("z"""
    val arr =
      OpenapiSchemaArray(
        OpenapiSchemaString(false),
        false,
        Some(OpenapiXml.XmlArrayConfiguration(name = Some(evilName), itemName = Some(evilItem)))
      )
    val gen = new ClassDefinitionGenerator()
      .classDefs(docWithObject("Widget", "items" -> noDefault(arr)), targetScala3 = isScala3, xmlParamRefs = Set("Widget"))
      .get
    val out = gen.xmlSerdeRepr.getOrElse("")
    out should include("""el\") ; sys.error(\"NAME\")""") // name escaped
    out should include("""it\") ; sys.error(\"ITEM\")""") // itemName escaped
    out should not include """seqDecoder[String]("el") ; sys.error("NAME")""" // not a live break-out
  }

  it should "escape a specification-extension string value so it cannot break out of the string literal" in {
    val evil = """v" ; sys.error("PWNED") ; val x = " """
    val doc = singlePathDoc(getMethod(specificationExtensions = Map("x-thing" -> Json.fromString(evil))))
    val out = endpointDecls(doc)
    // The `.attribute(...)` value goes through SpecificationExtensionRenderer; its quotes must be escaped so it stays
    // an inert string literal. (The .attribute references model-level extension keys, so the isolated endpoint decls
    // are not self-contained enough to compile here; the escaped-form assertion is the proof.)
    out should include("""v\" ; sys.error(\"PWNED\")""")
    out should not include """v" ; sys.error("PWNED")""" // the unescaped break-out form must not appear
  }

  it should "reject a server URL containing injection characters" in {
    val ex = intercept[IllegalArgumentException](
      ServersGenerator.genServerDefinitions(Seq(OpenapiServer("""https://x"+System.exit(0)+"""")), isScala3)
    )
    ex.getMessage should include("GHSA-gpcc")
  }

  // --- helpers for endpoint generation ---

  private def okResponse: OpenapiResponseDef =
    OpenapiResponseDef("200", "", Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false))))

  private def getMethod(
      parameters: Seq[Resolvable[OpenapiParameter]] = Nil,
      requestBody: Option[OpenapiRequestBody] = None,
      responses: Seq[OpenapiResponse] = Seq(okResponse),
      security: Option[Seq[Map[String, Seq[String]]]] = None,
      tags: Option[Seq[String]] = None,
      specificationExtensions: Map[String, Json] = Map.empty,
      methodType: String = "get"
  ): OpenapiPathMethod =
    OpenapiPathMethod(
      methodType,
      parameters,
      responses,
      requestBody,
      security = security,
      tags = tags,
      specificationExtensions = specificationExtensions
    )

  private def singlePathDoc(method: OpenapiPathMethod, components: Option[OpenapiComponent] = null, path: String = "p"): OpenapiDocument =
    OpenapiDocument("", Nil, null, Seq(OpenapiPath(path, Seq(method))), components, Nil)

  private def endpointWithParam(param: OpenapiParameter): OpenapiDocument =
    singlePathDoc(getMethod(parameters = Seq(Resolved(param))))

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
