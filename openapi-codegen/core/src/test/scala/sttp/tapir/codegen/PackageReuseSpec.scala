package sttp.tapir.codegen

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.codegen.dedup.{GenerationMeta, PackageReuseContext, SchemaComparer}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{NumericRestrictions, OpenapiSchemaConstantString, OpenapiSchemaEnum, OpenapiSchemaField, OpenapiSchemaInt, OpenapiSchemaObject, OpenapiSchemaRef, OpenapiSchemaString}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiComponent
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiInfo

import scala.collection.mutable

class SchemaComparerSpec extends AnyFlatSpec with Matchers {

  "SchemaComparer" should "find identical schemas by name and structure" in {
    val pet = OpenapiSchemaObject(
      mutable.LinkedHashMap("name" -> OpenapiSchemaField(OpenapiSchemaString(false), None)),
      Seq("name"),
      false
    )
    val current = Map("Pet" -> pet, "Owner" -> pet)
    val dependency = Map("Pet" -> pet, "Owner" -> pet.copy(nullable = true))
    SchemaComparer.findIdenticalSchemaNames(current, dependency) shouldBe Set("Pet")
  }

  it should "compare transitive refs" in {
    val address = OpenapiSchemaObject(
      mutable.LinkedHashMap("city" -> OpenapiSchemaField(OpenapiSchemaString(false), None)),
      Seq("city"),
      false
    )
    val person = OpenapiSchemaObject(
      mutable.LinkedHashMap("home" -> OpenapiSchemaField(OpenapiSchemaRef("#/components/schemas/Address"), None)),
      Seq.empty,
      false
    )
    val current = Map("Address" -> address, "Person" -> person)
    val dependency = Map("Address" -> address, "Person" -> person)
    SchemaComparer.findIdenticalSchemaNames(current, dependency) shouldBe Set("Address", "Person")
  }

  it should "detect enum differences" in {
    val e1 = OpenapiSchemaEnum("string", Seq(OpenapiSchemaConstantString("A")), false)
    val e2 = OpenapiSchemaEnum("string", Seq(OpenapiSchemaConstantString("B")), false)
    SchemaComparer.findIdenticalSchemaNames(Map("S" -> e1), Map("S" -> e2)) shouldBe Set.empty
  }
}

class PackageReuseContextSpec extends AnyFlatSpec with Matchers {

  private def petDoc(nameRequired: Boolean = true) = {
    val pet = OpenapiSchemaObject(
      mutable.LinkedHashMap("name" -> OpenapiSchemaField(OpenapiSchemaString(false), None)),
      if (nameRequired) Seq("name") else Seq.empty,
      false
    )
    OpenapiDocument(
      "3.0.0",
      Nil,
      OpenapiInfo("t", "1"),
      Nil,
      Some(OpenapiComponent(Map("Pet" -> pet))),
      Nil
    )
  }

  "PackageReuseContext" should "build reuse context from documents" in {
    val ctx = PackageReuseContext.fromDocuments(
      petDoc(),
      petDoc(),
      "sttp.tapir.generated.core",
      "TapirGeneratedEndpoints",
      GenerationMeta.default
    )
    ctx.reusedSchemas shouldBe Set("Pet")
    ctx.dependencyModelPath shouldBe "sttp.tapir.generated.core.TapirGeneratedEndpoints"
    PackageReuseContext.aliasType("Pet", ctx, false) shouldBe
      "type Pet = sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet"
    PackageReuseContext.aliasType("Pet", ctx, true) shouldBe
      "type Pet = sttp.tapir.generated.core.models.Pet"
  }

  it should "reuse Pet from schema_inheritance sbt-test specs" in {
    val v2Yaml =
      """openapi: 3.1.0
        |info:
        |  title: Pets API v2
        |  version: '2.0'
        |paths: {}
        |components:
        |  schemas:
        |    Pet:
        |      type: object
        |      required:
        |        - name
        |      properties:
        |        name:
        |          type: string
        |        species:
        |          type: string
        |""".stripMargin
    val v1Yaml =
      """openapi: 3.1.0
        |info:
        |  title: Pets API v1
        |  version: '1.0'
        |paths: {}
        |components:
        |  schemas:
        |    Pet:
        |      type: object
        |      required:
        |        - name
        |      properties:
        |        name:
        |          type: string
        |        species:
        |          type: string
        |    Order:
        |      type: object
        |      required:
        |        - id
        |        - pet
        |      properties:
        |        id:
        |          type: integer
        |          format: int64
        |        pet:
        |          $ref: '#/components/schemas/Pet'
        |""".stripMargin
    val v1 = YamlParser.parseFile(v1Yaml).fold(err => fail(err.getMessage), identity).resolveAllOfSchemas
    val v2 = YamlParser.parseFile(v2Yaml).fold(err => fail(err.getMessage), identity).resolveAllOfSchemas
    val ctx =
      PackageReuseContext.fromDocuments(v1, v2, "sttp.tapir.generated.v2", "TapirGeneratedEndpoints", GenerationMeta.default)
    ctx.reusedSchemas shouldBe Set("Pet")
  }
}

class OpenApiMergerSpec extends AnyFlatSpec with Matchers {

  private def minimalDoc(title: String, schemas: Map[String, OpenapiSchemaString]) =
    OpenapiDocument("3.0.0", Nil, OpenapiInfo(title, "1"), Nil, Some(OpenapiComponent(schemas)), Nil)

  "OpenApiMerger" should "merge schemas from multiple documents" in {
    val a = minimalDoc("a", Map("A" -> OpenapiSchemaString(false)))
    val b = minimalDoc("b", Map("B" -> OpenapiSchemaString(false)))
    val merged = OpenApiMerger.merge(Seq(a, b))
    merged.components.get.schemas.keySet shouldBe Set("A", "B")
  }

  it should "reject conflicting schema definitions" in {
    val a = minimalDoc("a", Map("Shared" -> OpenapiSchemaString(false)))
    val b = OpenapiDocument(
      "3.0.0",
      Nil,
      OpenapiInfo("b", "1"),
      Nil,
      Some(OpenapiComponent(Map("Shared" -> OpenapiSchemaInt(false, NumericRestrictions())))),
      Nil
    )
    intercept[IllegalArgumentException](OpenApiMerger.merge(Seq(a, b)))
  }
}
