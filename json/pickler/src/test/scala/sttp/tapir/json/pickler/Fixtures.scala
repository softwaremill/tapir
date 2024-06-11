package sttp.tapir.json.pickler

import sttp.tapir.Schema.annotations.default
import sttp.tapir.Schema.annotations.description
import java.util.UUID
import sttp.tapir.Schema.annotations.encodedName

object Fixtures:
  enum ColorEnum:
    case Green, Pink

  case class Book(author: String, title: String) derives Pickler
  case class BookShelf(books: List[Book]) derives Pickler

  case class Response(color: ColorEnum, description: String)

  enum RichColorEnum(val code: Int):
    case Cyan extends RichColorEnum(3)
    case Magenta extends RichColorEnum(18)

  case class RichColorResponse(color: RichColorEnum)

  enum Entity:
    case Person(first: String, age: Int)
    case Business(address: String)

  case class ClassWithDefault(@default("field-a-default") fieldA: String, fieldB: String)
  case class ClassWithScalaDefault(fieldA: String = "field-a-default", fieldB: String)
  case class ClassWithScalaAndTapirDefault(
      @default("field-a-tapir-default") fieldA: String = "field-a-scala-default",
      fieldB: String,
      fieldC: Int = 55
  )
  case class FlatClass(fieldA: Int, fieldB: String)
  case class TopClass(fieldA: String, fieldB: InnerClass)
  case class InnerClass(fieldA11: Int)

  case class TopClass2(fieldA: String, fieldB: AnnotatedInnerClass)
  case class AnnotatedInnerClass(@encodedName("encoded_field-a") fieldA: String, fieldB: String)
  case class ClassWithDefault2(@default("field-a-default-2") fieldA: String, @default(ErrorTimeout) fieldB: ErrorCode)
  case class ClassWithDefault3(
      fieldA: ErrorCode,
      @description("desc1") @default(InnerCaseClass("def-field", 65)) fieldB: InnerCaseClass,
      fieldC: InnerCaseClass
  )
  case class InnerCaseClass(fieldInner: String, @default(4) fieldInnerInt: Int)
  case class FlatClassWithOption(fieldA: String, fieldB: Option[Int], fieldC: Boolean)
  case class NestedClassWithOption(innerField: Option[FlatClassWithOption])

  case class FlatClassWithList(fieldA: String, fieldB: List[Int])
  case class NestedClassWithList(innerField: List[FlatClassWithList])
  case class FlatClassWithArray(fieldA: String, fieldB: Array[Int])
  case class NestedClassWithArray(innerField: Array[FlatClassWithArray])
  case class SimpleTestResult(msg: String)
  case class ClassWithEither(fieldA: String, fieldB: Either[String, SimpleTestResult])
  case class ClassWithMap(field: Map[String, SimpleTestResult])
  case class ClassWithMapCustomKey(field: Map[UUID, SimpleTestResult])
  case class UserId(value: UUID) extends AnyVal
  case class UserName(name: String) extends AnyVal
  case class ClassWithValues(id: UserId, name: UserName, age: Int)
  sealed trait ErrorCode

  case object ErrorNotFound extends ErrorCode
  case object ErrorTimeout extends ErrorCode
  case class CustomError(msg: String) extends ErrorCode

  sealed trait Status:
    def code: Int

  case class StatusOk(oF: Int) extends Status {
    def code = 200
  }
  case class StatusBadRequest(bF: Int) extends Status {
    def code = 400
  }

  case object StatusInternalError extends Status {
    def code = 500
  }

  case class StatusResponse(status: Status)

  case class SealedVariantContainer(v: SealedVariant)

  sealed trait SealedVariant
  case object VariantA extends SealedVariant
  case object VariantB extends SealedVariant
  case object VariantC extends SealedVariant

  sealed trait NotAllSealedVariant
  case object NotAllSealedVariantA extends NotAllSealedVariant
  case class NotAllSealedVariantB(innerField: Int) extends NotAllSealedVariant
