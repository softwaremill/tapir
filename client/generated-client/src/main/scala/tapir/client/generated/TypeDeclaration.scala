package tapir.client.generated

import TypeDeclaration._

object TypeDeclaration {
  case class Field(fieldName: String, fieldTypeName: String)

  case class TypeName[T](name: String)
}

trait TypeDeclaration[T] {
  def name: String
  def fields: List[Field]

  def typeName = TypeName(name)
}
