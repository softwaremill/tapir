package codegen

import codegen.BasicGenerator.{indent, mapSchemaSimpleTypeToType}
import codegen.openapi.models.OpenapiModels.OpenapiDocument
import codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaObject, OpenapiSchemaSimpleType}

class ClassDefinitionGenerator {
  def classDefs(doc: OpenapiDocument): String = {
    val classes = doc.components.schemas.map {
      case (name, OpenapiSchemaObject(fields, required, _)) =>
        val fs = fields.map {
          case (k, st: OpenapiSchemaSimpleType) =>
            val t = mapSchemaSimpleTypeToType(st)
            innerTypePrinter(k, t._1, t._2)
          case _ => throw new NotImplementedError("Only nonnested simple types supported!")
        }
        s"""|case class $name (
            |${indent(2)(fs.mkString(",\n"))}
            |)""".stripMargin
      case _ => throw new NotImplementedError("Only objects supported!")
    }
    classes.mkString("\n")
  }

  private def innerTypePrinter(key: String, tp: String, optional: Boolean) = {
    val fixedType = if (optional) s"Option[$tp]" else tp
    s"$key: $fixedType"
  }
}
