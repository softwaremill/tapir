package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaBoolean,
  OpenapiSchemaDouble,
  OpenapiSchemaFloat,
  OpenapiSchemaInt,
  OpenapiSchemaLong,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType,
  OpenapiSchemaString,
  OpenapiSchemaUUID
}

object BasicGenerator {

  val classGenerator = new ClassDefinitionGenerator()
  val endpointGenerator = new EndpointGenerator()

  def generateObjects(doc: OpenapiDocument, packagePath: String, objName: String): String = {
    s"""|
        |package $packagePath
        |
        |object $objName {
        |
        |${indent(2)(imports)}
        |
        |${indent(2)(classGenerator.classDefs(doc).getOrElse(""))}
        |
        |${indent(2)(endpointGenerator.endpointDefs(doc))}
        |
        |}
        |""".stripMargin
  }

  private[codegen] def imports: String =
    """import sttp.tapir._
      |import sttp.tapir.json.circe._
      |import sttp.tapir.generic.auto._
      |import io.circe.generic.auto._
      |""".stripMargin

  def indent(i: Int)(str: String): String = {
    str.linesIterator.map(" " * i + _).mkString("\n")
  }

  def mapSchemaSimpleTypeToType(osst: OpenapiSchemaSimpleType): (String, Boolean) = {
    osst match {
      case OpenapiSchemaDouble(nb) =>
        ("Double", nb)
      case OpenapiSchemaFloat(nb) =>
        ("Float", nb)
      case OpenapiSchemaInt(nb) =>
        ("Int", nb)
      case OpenapiSchemaLong(nb) =>
        ("Long", nb)
      case OpenapiSchemaUUID(nb) =>
        ("java.util.UUID", nb)
      case OpenapiSchemaString(nb) =>
        ("String", nb)
      case OpenapiSchemaBoolean(nb) =>
        ("Boolean", nb)
      case OpenapiSchemaRef(t) =>
        (t.split('/').last, false)
      case _ => throw new NotImplementedError("Not all simple types supported!")
    }
  }
}
