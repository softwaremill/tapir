package sttp.tapir.codegen

import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.testutils.{CompileCheckTestBase, VersionCheck}

class RootGeneratorSpec extends CompileCheckTestBase {
  def genMap(
      doc: OpenapiDocument,
      useHeadTagForObjectNames: Boolean,
      jsonSerdeLib: String
  ) = {
    RootGenerator
      .generateObjects(
        doc,
        "sttp.tapir.generated",
        "TapirGeneratedEndpoints",
        targetScala3 = isScala3,
        useHeadTagForObjectNames = useHeadTagForObjectNames,
        jsonSerdeLib = jsonSerdeLib,
        xmlSerdeLib = "cats-xml",
        validateNonDiscriminatedOneOfs = true,
        maxSchemasPerFile = 400,
        streamingImplementation = "fs2",
        generateEndpointTypes = false,
        generateValidators = true,
        useCustomJsoniterSerdes = true,
        packageReuse = PackageReuseContext.none,
        seperateFilesForModels = false,
        alwaysGenerateParamSupport = false
      )
      .allFiles
  }
  def gen(
      doc: OpenapiDocument,
      useHeadTagForObjectNames: Boolean,
      jsonSerdeLib: String
  ) = {
    val genned = genMap(
      doc,
      useHeadTagForObjectNames = useHeadTagForObjectNames,
      jsonSerdeLib = jsonSerdeLib
    )
    val main = genned("TapirGeneratedEndpoints")
    val schemaKeys = genned.keys.filter(_.startsWith("TapirGeneratedEndpointsSchemas")).toSeq.sorted
    val maybeExtra = (schemaKeys.map(genned) ++ genned.get("TapirGeneratedEndpointsJsonSerdes")).mkString("\n")
    main + "\n" + maybeExtra
  }
  def testJsonLib(jsonSerdeLib: String) = {
    it should s"generate the bookshop example using ${jsonSerdeLib} serdes" in {
      gen(TestHelpers.myBookshopDoc, useHeadTagForObjectNames = false, jsonSerdeLib = jsonSerdeLib).shouldCompile()
    }

    it should s"split outputs by tag if useHeadTagForObjectNames = true using ${jsonSerdeLib} serdes" in {
      val generated = genMap(
        TestHelpers.myBookshopDoc,
        useHeadTagForObjectNames = true,
        jsonSerdeLib = jsonSerdeLib
      )
      val models = generated("TapirGeneratedEndpoints")
      val serdes = generated("TapirGeneratedEndpointsJsonSerdes")
      val schemas = generated("TapirGeneratedEndpointsSchemas")
      val endpoints = generated("Bookshop")
      // schema file on its own should compile
      models.shouldCompile()
      // schema file should contain no endpoint definitions
      models.linesIterator.count(_.matches("""^\s*endpoint""")) shouldEqual 0
      // schema file with serde file should compile
      (models + "\n" + serdes).shouldCompile()
      // schema file with serde file & schema file should compile
      (models + "\n" + serdes + "\n" + schemas).shouldCompile()
      // Bookshop file should contain all endpoint definitions
      endpoints.linesIterator.count(_.matches("""^\s*endpoint""")) shouldEqual 4
      // endpoint file depends on models, serdes & schemas
      (models + "\n" + serdes + "\n" + schemas + "\n" + endpoints).shouldCompile()
    }

    it should s"compile endpoints with enum query params using ${jsonSerdeLib} serdes" in {
      gen(TestHelpers.enumQueryParamDocs, useHeadTagForObjectNames = false, jsonSerdeLib = jsonSerdeLib).shouldCompile()
    }

    VersionCheck.runTest(jsonSerdeLib)(it should s"compile endpoints with default params using ${jsonSerdeLib} serdes" in {
      val genWithParams = gen(TestHelpers.withDefaultsDocs, useHeadTagForObjectNames = false, jsonSerdeLib = jsonSerdeLib)

      val expectedDefaultDeclarations = Seq(
        """f1: String = "default string"""",
        """f2: Option[Int] = Some(1977)""",
        """g1: Option[java.util.UUID] = Some(java.util.UUID.fromString("default string"))""",
        """g2: Float = 1977.0f""",
        """g3: Option[AnEnum] = Some(AnEnum.v1)""",
        """g4: Option[Seq[AnEnum]] = Some(Vector(AnEnum.v1, AnEnum.v2, AnEnum.v3))""",
        """sub: Option[SubObject] = Some(SubObject(subsub = SubSubObject(value = "hi there", value2 = Some(java.util.UUID.fromString("ac8113ed-6105-4f65-a393-e88be2c5d585")))))"""
      )
      expectedDefaultDeclarations foreach (decln => genWithParams should include(decln))

      genWithParams.shouldCompile()
    })

    VersionCheck.runTest(jsonSerdeLib)(it should s"compile endpoints with date and duration types using ${jsonSerdeLib} serdes" in {
      val doc = TestHelpers.parseYamlDocument(TestHelpers.dateAndDurationYaml).fold(err => fail(err.getMessage), identity)
      val generated = gen(doc, useHeadTagForObjectNames = false, jsonSerdeLib = jsonSerdeLib)

      generated should include(
        """  case class Event (
          |    name: String,
          |    eventDate: java.time.LocalDate,
          |    optionalDate: Option[java.time.LocalDate] = None,
          |    duration: java.time.Duration,
          |    optionalDuration: Option[java.time.Duration] = None,
          |    scheduledAt: Option[java.time.Instant] = None
          |  )""".stripMargin
      )
      generated should include(
        """  lazy val getEventsByDate =
          |    endpoint
          |      .name("getEventsByDate")
          |      .get
          |      .in(("events" / path[java.time.LocalDate]("date")))
          |      .in(query[Option[java.time.Duration]]("minDuration"))
          |      .out(jsonBody[List[Event]].description(""))""".stripMargin
      )

      generated.shouldCompile()
    })

    VersionCheck.runTest(jsonSerdeLib)(
      it should s"compile endpoints with date and duration default values using ${jsonSerdeLib} serdes" in {
        val doc = TestHelpers.parseYamlDocument(TestHelpers.dateAndDurationDefaultsYaml).fold(err => fail(err.getMessage), identity)
        val generated = gen(doc, useHeadTagForObjectNames = false, jsonSerdeLib = jsonSerdeLib)

        generated should include("""java.time.LocalDate.parse("2024-01-15")""")
        generated should include("""java.time.Duration.parse("PT1H30M")""")

        generated.shouldCompile()
      }
    )
  }

  it should "split models into separate files when seperateFilesForModels is true" in {
    val info = RootGenerator.generateObjects(
      TestHelpers.myBookshopDoc,
      "sttp.tapir.generated",
      "TapirGeneratedEndpoints",
      targetScala3 = isScala3,
      useHeadTagForObjectNames = false,
      jsonSerdeLib = "circe",
      xmlSerdeLib = "none",
      streamingImplementation = "fs2",
      validateNonDiscriminatedOneOfs = true,
      maxSchemasPerFile = 400,
      generateEndpointTypes = false,
      generateValidators = true,
      useCustomJsoniterSerdes = true,
      packageReuse = PackageReuseContext.none,
      seperateFilesForModels = true,
      alwaysGenerateParamSupport = false
    )
    val files = info.allFiles
    files.keys.exists(_.startsWith("models.")) shouldBe true
    files.keys should contain("models.Book")
    files("TapirGeneratedEndpoints") should not include "case class Book"
    files("models.Book") should include("case class Book")
    val modelFiles = files.filter(_._1.startsWith("models."))
    val packageFile = files.find(_._1.endsWith("package")).get
    modelFiles.filterNot(_._1.endsWith("package")).values.foreach(_ should include("package sttp.tapir.generated.models"))
    packageFile._2 should not include ("package sttp.tapir.generated.models")
    packageFile._2 should include("package sttp.tapir.generated")
    packageFile._2 should include("package object models")
  }

  Seq("circe", "jsoniter", "zio") foreach testJsonLib
}
