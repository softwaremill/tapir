package sttp.tapir.docs.openapi.dtos

import sttp.tapir.tests.data.Entity
import sttp.tapir.{Schema, Validator}
import sttp.tapir.SchemaType.SRef
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType
import sttp.tapir.FieldName

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

  sealed trait PetType
  object PetType {
    case object Canine extends PetType
    case object Feline extends PetType
    implicit val schema: Schema[PetType] = Schema.derivedEnumeration[PetType].defaultStringBased
  }

  sealed trait Pet
  object Pet {
    implicit val schema: Schema[Pet] = {
      val derived = Schema.derived[Pet]
      val mapping = Map(
        PetType.Canine.toString -> SRef(SName("sttp.tapir.docs.openapi.dtos.VerifyYamlCoproductTestData.Dog")),
        PetType.Feline.toString -> SRef(SName("sttp.tapir.docs.openapi.dtos.VerifyYamlCoproductTestData.Cat"))
      )

      derived.schemaType match {
        case s: SchemaType.SCoproduct[Pet] =>
          derived.copy(schemaType = s.addDiscriminatorField(FieldName("petType"), PetType.schema, mapping))
        case _ => derived
      }
    }
  }
  final case class Dog(breed: String) extends Pet
  final case class Cat(name: String) extends Pet
}
