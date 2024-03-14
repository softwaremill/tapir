package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaAny,
  OpenapiSchemaBoolean,
  OpenapiSchemaBinary,
  OpenapiSchemaDateTime,
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

  def generateObjects(
      doc: OpenapiDocument,
      packagePath: String,
      objName: String,
      targetScala3: Boolean,
      useHeadTagForObjectNames: Boolean
  ): Map[String, String] = {
    val EndpointDefs(endpointsByTag, queryParamRefs) = endpointGenerator.endpointDefs(doc, useHeadTagForObjectNames)
    val taggedObjs = endpointsByTag.collect {
      case (Some(headTag), body) if body.nonEmpty =>
        val taggedObj =
          s"""package $packagePath
           |
           |import $objName._
           |
           |object $headTag {
           |
           |${indent(2)(imports)}
           |
           |${indent(2)(body)}
           |
           |}""".stripMargin
        headTag -> taggedObj
    }
    val mainObj = s"""|
        |package $packagePath
        |
        |object $objName {
        |
        |${indent(2)(imports)}
        |
        |${indent(2)(classGenerator.classDefs(doc, targetScala3, queryParamRefs).getOrElse(""))}
        |
        |${indent(2)(endpointsByTag.getOrElse(None, ""))}
        |
        |}
        |""".stripMargin
    taggedObjs + (objName -> mainObj)
  }

  private[codegen] def imports: String =
    """import sttp.tapir._
      |import sttp.tapir.model._
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
      case OpenapiSchemaDateTime(nb) =>
        ("java.time.Instant", nb)
      case OpenapiSchemaUUID(nb) =>
        ("java.util.UUID", nb)
      case OpenapiSchemaString(nb) =>
        ("String", nb)
      case OpenapiSchemaBoolean(nb) =>
        ("Boolean", nb)
      case OpenapiSchemaBinary(nb) =>
        ("sttp.model.Part[java.io.File]", nb)
      case OpenapiSchemaAny(nb) =>
        ("io.circe.Json", nb)
      case OpenapiSchemaRef(t) =>
        (t.split('/').last, false)
      case x => throw new NotImplementedError(s"Not all simple types supported! Found $x")
    }
  }
}
