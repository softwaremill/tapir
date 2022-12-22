package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.{indent, mapSchemaSimpleTypeToType}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaArray, OpenapiSchemaObject, OpenapiSchemaSimpleType}

class ClassDefinitionGenerator {

  def classDefs(doc: OpenapiDocument): String = {
    val classes = doc.components.schemas.flatMap {
      case (name, obj: OpenapiSchemaObject) =>
        generateClass(name, obj)
      case _ => throw new NotImplementedError("Only objects supported!")
    }
    classes.mkString("\n")
  }

  private[codegen] def generateClass(name: String, obj: OpenapiSchemaObject): Seq[String] = {
    def addName(parentName: String, key: String) = parentName + key.replace('_', ' ').replace('-', ' ').capitalize.replace(" ", "")
    def rec(name: String, obj: OpenapiSchemaObject, acc: List[String]): Seq[String] = {
      val innerClasses = obj.properties
        .collect { case (propName, st: OpenapiSchemaObject) =>
          val newName = addName(name, propName)
          rec(newName, st, Nil)
        }
        .flatten
        .toList
      val fs = obj.properties.collect {
        case (k, st: OpenapiSchemaSimpleType) =>
          val t = mapSchemaSimpleTypeToType(st)
          innerTypePrinter(k, t._1, t._2 || !obj.required.contains(k))
        case (k, st: OpenapiSchemaObject) =>
          val t = addName(name, k)
          innerTypePrinter(k, t, st.nullable || !obj.required.contains(k))
        case (k, OpenapiSchemaArray(inner: OpenapiSchemaSimpleType, nullable)) =>
          val innerT = mapSchemaSimpleTypeToType(inner)._1
          innerTypePrinter(k, s"Seq[$innerT]", nullable || !obj.required.contains(k))
      }
      require(fs.size == obj.properties.size, s"We can't serialize some of the properties yet! $name $obj")
      s"""|case class $name (
          |${indent(2)(fs.mkString(",\n"))}
          |)""".stripMargin :: innerClasses ::: acc
    }

    rec(addName("", name), obj, Nil)
  }

  private val reservedKeys = scala.reflect.runtime.universe.asInstanceOf[scala.reflect.internal.SymbolTable].nme.keywords.map(_.toString)

  private def fixKey(key: String) = {
    if (reservedKeys.contains(key))
      s"`$key`"
    else
      key
  }

  private def innerTypePrinter(key: String, tp: String, optional: Boolean) = {
    val fixedType = if (optional) s"Option[$tp]" else tp
    val fixedKey = fixKey(key)
    s"$fixedKey: $fixedType"
  }
}
