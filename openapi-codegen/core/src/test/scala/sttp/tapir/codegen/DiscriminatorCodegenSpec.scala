package sttp.tapir.codegen

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.codegen.json.JsonSerdeLib.Circe
import sttp.tapir.codegen.testutils.CompileCheckTestBase

class DiscriminatorCodegenSpec extends AnyFlatSpec with Matchers with CompileCheckTestBase {

  private val animalYaml =
    """openapi: 3.1.0
      |info:
      |  title: Animal oneOf
      |  version: '1.0'
      |paths: {}
      |components:
      |  schemas:
      |    Animal:
      |      oneOf:
      |        - $ref: '#/components/schemas/Dog'
      |        - $ref: '#/components/schemas/Cat'
      |      discriminator:
      |        propertyName: kind
      |        mapping:
      |          dog: '#/components/schemas/Dog'
      |          cat: '#/components/schemas/Cat'
      |    Dog:
      |      type: object
      |      required:
      |        - kind
      |        - barks
      |      properties:
      |        kind:
      |          type: string
      |        barks:
      |          type: boolean
      |    Cat:
      |      type: object
      |      required:
      |        - kind
      |        - lives
      |      properties:
      |        kind:
      |          type: string
      |        lives:
      |          type: integer
      |          format: int32
      |""".stripMargin

  "ClassDefinitionGenerator" should "omit discriminator fields from oneOf variant constructors" in {
    val doc = YamlParser.parseFile(animalYaml).fold(err => fail(err.getMessage), identity).resolveAllOfSchemas
    val gen = new ClassDefinitionGenerator()
    val out = gen.classDefs(doc, targetScala3 = false, jsonSerdeLib = Circe).get.classRepr
    out should include("case class Dog (")
    out should include("barks: Boolean")
    out should include(" { def kind: String }")
    out should include("""def kind: String = "dog"""")
    out should not include regex("case class Dog \\([^)]*kind".r)
    out.shouldCompile()
  }

  it should "omit discriminator fields when schema key differs from generated class name" in {
    val yaml =
      """openapi: 3.1.0
        |info:
        |  title: underscored schema keys
        |  version: '1.0'
        |paths: {}
        |components:
        |  schemas:
        |    my_animal:
        |      oneOf:
        |        - $ref: '#/components/schemas/my_dog'
        |      discriminator:
        |        propertyName: kind
        |        mapping:
        |          dog: '#/components/schemas/my_dog'
        |    my_dog:
        |      type: object
        |      required:
        |        - kind
        |        - barks
        |      properties:
        |        kind:
        |          type: string
        |        barks:
        |          type: boolean
        |""".stripMargin
    val doc = YamlParser.parseFile(yaml).fold(err => fail(err.getMessage), identity).resolveAllOfSchemas
    val out = new ClassDefinitionGenerator().classDefs(doc, targetScala3 = false, jsonSerdeLib = Circe).get.classRepr
    out should include("case class Mydog (")
    out should include("barks: Boolean")
    out should include(" { def kind: String }")
    out should include("""def kind: String = "dog"""")
    out.shouldCompile()
  }
}
