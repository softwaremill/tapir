package sapi

import io.circe.{Decoder, Encoder}
import io.circe._
import io.circe.parser._
import io.circe.syntax._

import language.experimental.macros
import magnolia._

import scala.util.Try

object JsonTypeMapper extends App {

  case class Name(first: String, last: String)
  case class User(name: Name, age: Int)

  val genUser = TypeInfoDerivation.gen[User]
  println(genUser)

  def json[T: TypeInfo: Encoder: Decoder]: TypeMapper[T] = new TypeMapper[T] {
    override def toString(t: T): String = t.asJson.noSpaces
    override def fromString(s: String): Try[T] = decode[T](s).toTry
  }

  //implicit val userType: TypeMapper[User] = json[User]  // TODO.sample(User("x"))

}

sealed trait TypeInfo[T]
case class ObjectInfo[T](name: String, fields: List[FieldInfo[_]]) extends TypeInfo[T]
case class PrimitiveTypeInfo[T](typeName: String) extends TypeInfo[T]
case class FieldInfo[T](name: String, value: TypeInfo[T])

object TypeInfo {
  implicit val stringTypeInfo: TypeInfo[String] = PrimitiveTypeInfo("string")
  implicit val intTypeInfo: TypeInfo[Int] = PrimitiveTypeInfo("int")
}

object TypeInfoDerivation {
  type Typeclass[T] = TypeInfo[T]

  def combine[T](ctx: CaseClass[TypeInfo, T]): TypeInfo[T] = {
    println("COMBINE " + ctx)
    ObjectInfo[T](ctx.typeName.short, ctx.parameters.map(p => FieldInfo(p.label, p.typeclass)).toList)
  }

  def dispatch[T](ctx: SealedTrait[TypeInfo, T]): TypeInfo[T] = {
    println("DISPATCH " + ctx)
    null
  }

  implicit def gen[T]: TypeInfo[T] = macro Magnolia.gen[T]
}
