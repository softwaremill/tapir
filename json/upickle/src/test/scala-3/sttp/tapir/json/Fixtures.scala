package sttp.tapir.json

import sttp.tapir.Schema.annotations.default
import sttp.tapir.Schema.annotations.description

object Fixtures:
  enum ColorEnum:
    case Green, Pink

  case class Response(color: ColorEnum, description: String)

  enum RichColorEnum(val code: Int):
    case Cyan extends RichColorEnum(3)
    case Magenta extends RichColorEnum(18)

  case class RichColorResponse(color: RichColorEnum)

case class ClassWithDefault(@default("field-a-default") fieldA: String, fieldB: String)
case class ClassWithScalaDefault(fieldA: String = "field-a-default", fieldB: String)
case class ClassWithScalaAndTapirDefault(@default("field-a-tapir-default") fieldA: String = "field-a-scala-default", fieldB: String, fieldC: Int = 55)
case class ClassWithDefault2(@default("field-a-default-2") fieldA: String, @default(ErrorTimeout) fieldB: ErrorCode)
case class ClassWithDefault3(fieldA: ErrorCode, @description("desc1") @default(InnerCaseClass("def-field", 65)) fieldB: InnerCaseClass, fieldC: InnerCaseClass)
case class InnerCaseClass(fieldInner: String, @default(4) fieldInnerInt: Int)
case class FlatClassWithOption(fieldA: String, fieldB: Option[Int])
case class NestedClassWithOption(innerField: Option[FlatClassWithOption])

sealed trait ErrorCode

case object ErrorNotFound extends ErrorCode
case object ErrorTimeout extends ErrorCode
case class CustomError(msg: String) extends ErrorCode
