package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.{indent, mapSchemaSimpleTypeToType}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaConstantString,
  OpenapiSchemaEnum,
  OpenapiSchemaMap,
  OpenapiSchemaObject,
  OpenapiSchemaSimpleType
}

class ClassDefinitionGenerator {

  def classDefs(doc: OpenapiDocument): Option[String] = {
    doc.components
      .map(_.schemas.flatMap {
        case (name, obj: OpenapiSchemaObject) =>
          generateClass(name, obj)
        case (name, obj: OpenapiSchemaEnum) =>
          generateEnum(name, obj)
        case _ => throw new NotImplementedError("Only objects and enums supported!")
      })
      .map(_.mkString("\n"))
  }

  // Uses enumeratum so as to work with scala 2, but ideally should probably generate scala 3 enums instead when it can
  private[codegen] def generateEnum(name: String, obj: OpenapiSchemaEnum): Seq[String] = {
    val members = obj.items.map{
      case OpenapiSchemaConstantString(s) => s"case object $s extends $name"
      case _ => throw new NotImplementedError("Only string enums are supported!")
    }
    s"""|sealed trait $name extends EnumEntry
        |object $name extends Enum[$name] {
        |  val values = findValues
        |${indent(2)(members.mkString("\n"))}
        |}""".stripMargin :: Nil
  }

  private[codegen] def generateClass(name: String, obj: OpenapiSchemaObject): Seq[String] = {
    def rec(name: String, obj: OpenapiSchemaObject, acc: List[String]): Seq[String] = {
      val innerClasses = obj.properties
        .collect {
          case (propName, st: OpenapiSchemaObject) =>
            val newName = addName(name, propName)
            rec(newName, st, Nil)

          case (propName, OpenapiSchemaMap(st: OpenapiSchemaObject, _)) =>
            val newName = addName(addName(name, propName), "item")
            rec(newName, st, Nil)

          case (propName, OpenapiSchemaArray(st: OpenapiSchemaObject, _)) =>
            val newName = addName(addName(name, propName), "item")
            rec(newName, st, Nil)
        }
        .flatten
        .toList

      val properties = obj.properties.map { case (key, schemaType) =>
        val tpe = mapSchemaTypeToType(name, key, obj.required.contains(key), schemaType)
        val fixedKey = fixKey(key)
        s"$fixedKey: $tpe"
      }

      s"""|case class $name (
          |${indent(2)(properties.mkString(",\n"))}
          |)""".stripMargin :: innerClasses ::: acc
    }

    rec(addName("", name), obj, Nil)
  }

  private def mapSchemaTypeToType(parentName: String, key: String, required: Boolean, schemaType: OpenapiSchemaType): String = {
    val (tpe, optional) = schemaType match {
      case simpleType: OpenapiSchemaSimpleType =>
        mapSchemaSimpleTypeToType(simpleType)

      case objectType: OpenapiSchemaObject =>
        addName(parentName, key) -> objectType.nullable

      case mapType: OpenapiSchemaMap =>
        val innerType = mapSchemaTypeToType(addName(parentName, key), "item", required = true, mapType.items)
        s"Map[String, $innerType]" -> mapType.nullable

      case arrayType: OpenapiSchemaArray =>
        val innerType = mapSchemaTypeToType(addName(parentName, key), "item", required = true, arrayType.items)
        s"Seq[$innerType]" -> arrayType.nullable

      case _ =>
        throw new NotImplementedError(s"We can't serialize some of the properties yet! $parentName $key $schemaType")
    }

    if (optional || !required) s"Option[$tpe]" else tpe
  }

  private def addName(parentName: String, key: String) = parentName + key.replace('_', ' ').replace('-', ' ').capitalize.replace(" ", "")

  private val reservedKeys = scala.reflect.runtime.universe.asInstanceOf[scala.reflect.internal.SymbolTable].nme.keywords.map(_.toString)

  private def fixKey(key: String) = {
    if (reservedKeys.contains(key))
      s"`$key`"
    else
      key
  }
}
