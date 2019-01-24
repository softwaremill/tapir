package tapir

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

  case class SRef(fullName: String) extends Schema {
    def show: String = s"ref($fullName)"
  }

  case class SObjectInfo(shortName: String, fullName: String)
}
