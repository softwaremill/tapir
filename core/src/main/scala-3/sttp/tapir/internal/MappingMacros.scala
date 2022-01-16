package sttp.tapir.internal

import sttp.tapir.Mapping

import scala.compiletime.erasedValue
import scala.deriving.Mirror

object MappingMacros {
  inline def mappingImpl[In, Out <: Product](using mc: Mirror.ProductOf[Out]): Mapping[In, Out] = {
    checkFields[Out, In]

    val to: In => Out = {
      case t: Tuple   => mc.fromProduct(t)
      case EmptyTuple => mc.fromProduct(EmptyTuple)
      case t          => mc.fromProduct(Tuple1(t))
    }
    def from(out: Out): In = Tuple.fromProduct(out) match {
      case EmptyTuple    => EmptyTuple.asInstanceOf[In]
      case Tuple1(value) => value.asInstanceOf[In]
      case value         => value.asInstanceOf[In]
    }
    Mapping.from(to)(from)
  }

  inline def checkFields[A, B](using m: Mirror.ProductOf[A]): Unit =
    inline (erasedValue[m.MirroredElemTypes], erasedValue[B]) match {
      case _: (EmptyTuple, B)      => ()
      case _: (B *: EmptyTuple, B) => ()
      case _: (B, B)               => ()
      case _: EmptyTuple           => ()
      case e                       => ComplietimeErrors.reportIncorrectMapping[B, A]
    }
}
