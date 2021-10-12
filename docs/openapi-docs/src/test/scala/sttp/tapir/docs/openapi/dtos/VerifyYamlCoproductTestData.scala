package sttp.tapir.docs.openapi.dtos

import sttp.tapir.tests.data.Entity
import sttp.tapir.{Schema, Validator}

// TODO: move back to VerifyYamlTest companion after https://github.com/lampepfl/dotty/issues/12849 is fixed
object VerifyYamlCoproductTestData {
  sealed trait GenericEntity[T]
  case class GenericPerson[T](data: T) extends GenericEntity[T]

  case class NestedEntity(entity: Entity)

  object Color extends Enumeration {
    type Color = Value

    val Blue = Value("blue")
    val Red = Value("red")

    implicit def schemaForEnum: Schema[Value] = Schema.string.validate(Validator.enumeration(values.toList, v => Option(v)))
  }

  sealed trait Shape {
    def shapeType: String
  }

  case class Square(color: Color.Value, shapeType: String = "square") extends Shape
}
