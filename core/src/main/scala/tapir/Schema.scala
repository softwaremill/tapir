package tapir

import tapir.generic.MethodName

sealed trait Schema {
  def show: String
}

object Schema {
  case object SString extends Schema {
    def show: String = "string"
  }
  case object SInteger extends Schema {
    def show: String = "integer"
  }
  case object SNumber extends Schema {
    def show: String = "number"
  }
  case object SBoolean extends Schema {
    def show: String = "boolean"
  }
  case class SObject(info: SObjectInfo, fields: Iterable[(String, Schema)], required: Iterable[String]) extends Schema {
    def show: String = s"object(${fields.map(f => s"${f._1}->${f._2.show}").mkString(",")};required:${required.mkString(",")})"
  }
  case class SArray(element: Schema) extends Schema {
    def show: String = s"array(${element.show})"
  }
  case object SBinary extends Schema {
    def show: String = "binary"
  }
  case object SDate extends Schema {
    def show: String = "date"
  }
  case object SDateTime extends Schema {
    def show: String = "date-time"
  }

  case class SRef(fullName: String) extends Schema {
    def show: String = s"ref($fullName)"
  }

  case class SCoproduct(schemas: List[Schema], discriminator: Option[Discriminator[_]]) extends Schema {
    override def show: String = "oneOf:" + schemas.mkString(",")
  }

  case class SObjectInfo(shortName: String, fullName: String)

  case class Discriminator[T](propertyName: String, mapping: Map[String, Schema])

  def discriminator[T, R](name: MethodName)(mapping: Map[R, SchemaFor[_ <: T]]): Discriminator[T] = {
    val map: Map[String, Schema] = mapping.map {
      case (k, v) => k.toString -> v.schema
    }.toMap
    Discriminator[T](name.name, map)
  }
}
