package sttp.tapir.newschema

import sttp.tapir.Schema.annotations.{encodedName, validate}
import sttp.tapir.SchemaType.SInteger
import sttp.tapir.{Schema, Validator}

object Test extends App {

  case class A(@encodedName("fff") f: Int, @validate(Validator.minLength[String](1)) g: String)
  @encodedName("xxx")
  case class B(f: Int, g: String)

  case class Rec(f: Int, c: Rec)

//  println(NewSchemaMacro.gen[A])
//  println(NewSchemaMacro.gen[B])
  //
  //  println(NewSchemaMacro.gen[A].applyValidation(A(0, "bcx")))

//  implicit def recSchema: Schema[Rec] = NewSchemaMacro.gen[Rec]
//  println(recSchema)

  import NewSchemaMacro._
  implicit val schemaForInt: Schema[Int] = Schema(SInteger[Int]()).format("int32")
  println(implicitly[Schema[Rec]])
}
