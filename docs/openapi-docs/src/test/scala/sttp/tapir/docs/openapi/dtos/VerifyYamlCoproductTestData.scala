package sttp.tapir.docs.openapi.dtos

import sttp.tapir.SchemaType.SObjectInfo
import sttp.tapir.docs.openapi.dtos.VerifyYamlCoproductTestData.CornerStyle.values
import sttp.tapir.{Schema, Validator}
import sttp.tapir.tests.Entity

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

  object CornerStyle extends Enumeration {
    type CornerStyle = Value

    val Rounded = Value("rounded")
    val Straight = Value("straight")

    implicit def schemaForEnum: Schema[Value] =
      Schema.string.validate(Validator.enumeration(values.toList, v => Option(v), Some(SObjectInfo("CornerStyle"))))
  }

  object Tag extends Enumeration {
    type Tag = Value

    val Tag1 = Value("tag1")
    val Tag2 = Value("tag2")

    implicit def schemaForEnum: Schema[Value] =
      Schema.string.validate(Validator.enumeration(values.toList, v => Option(v), Some(SObjectInfo("Tag"))))
  }

  sealed trait Shape {
    def shapeType: String
  }

  case class Square(color: Color.Value, shapeType: String = "square", cornerStyle: Option[CornerStyle.Value], tags: Seq[Tag.Value])
      extends Shape
}
