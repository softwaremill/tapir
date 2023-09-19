package sttp.tapir.newschema

import sttp.tapir.Schema.annotations.encodedName
import sttp.tapir.Schema.annotations.description
import sttp.tapir.Schema.annotations.validate
import sttp.tapir.Schema
import sttp.tapir.Validator
import sttp.tapir.generic.Derived

// @encodedName("xyz")
// trait X

// case class Person(@encodedName("f") first: String, @description("How old") age: Int) extends X

// case class Rec(f: String, c: Option[Rec])
// object Rec:
//   // inline given recSchema: Schema[Rec] = Derive2.derive2[Rec]
//   val recSchema: Schema[Rec] =
//     inline given x: Schema[Rec] = Derive2.derive2[Rec]
//     x

// Derived - implicit conversions aren't looked up during Expr.summon, so this doesn't work.

sealed trait A[T]
case class X[T](i: Int) extends A[T]
case class Y[T](j: String) extends A[T]

// trait TC[T]
// given TC[X] = new TC[X] {}

sealed trait Opt[+T]
case object N extends Opt[Nothing]

object Test extends App {
  // println(Derive2.derive2[Person])
  // println("X")
  // println(Rec.recSchema)

  given xSchema[T]: Schema[X[T]] = Derive2.derive2[X[T]]
  given ySchema[T]: Schema[Y[T]] = Derive2.derive2[Y[T]]

  // val a = Derive2.derive2[A]
  // println(a)
  // println(a.applyValidation(X(10)))

  // println(implicitly[Schema[Option[String]]])
  // Derive2.derive2[Opt[String]]
  Derive2.derive2[Option[String]]

  // TestMacro.test[A]
}
