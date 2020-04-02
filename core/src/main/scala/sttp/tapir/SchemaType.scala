package sttp.tapir

sealed trait SchemaType {
  def show: String
}

object SchemaType { 
  case object SString extends SchemaType {
    def show: String = "string"
  }
  case object SInteger extends SchemaType {
    def show: String = "integer"
  }
  case object SNumber extends SchemaType {
    def show: String = "number"
  }
  case object SBoolean extends SchemaType {
    def show: String = "boolean"
  }
  case class SArray(element: Schema[_]) extends SchemaType {
    def show: String = s"array(${element.show})"
  }
  case object SBinary extends SchemaType {
    def show: String = "binary"
  }
  case object SDate extends SchemaType {
    def show: String = "date"
  }
  case object SDateTime extends SchemaType {
    def show: String = "date-time"
  }

  sealed trait SObject extends SchemaType {
    def info: SObjectInfo
  }
  case class SProduct(info: SObjectInfo, fields: Iterable[(String, Schema[_])]) extends SObject {
    def required: Iterable[String] = fields.collect {
      case (f, s) if !s.isOptional => f
    }
    def show: String = s"object(${fields.map(f => s"${f._1}->${f._2.show}").mkString(",")}"
  }
  object SProduct {
    val Empty: SProduct = SProduct(SObjectInfo.Unit, Iterable.empty)
  }
  case class SCoproduct(info: SObjectInfo, schemas: List[Schema[_]], discriminator: Option[Discriminator]) extends SObject {
    override def show: String = "oneOf:" + schemas.mkString(",")
  }
  case class SOpenProduct(info: SObjectInfo, valueSchema: Schema[_]) extends SObject {
    override def show: String = s"map"
  }

  case class SRef(info: SObjectInfo) extends SchemaType {
    def show: String = s"ref($info)"
  }

  case class SObjectInfo(fullName: String, typeParameterShortNames: List[String] = Nil)
  object SObjectInfo {
    val Unit: SObjectInfo = SObjectInfo(fullName = "Unit")
  }

  case class Discriminator(propertyName: String, mappingOverride: Map[String, SRef])
}
