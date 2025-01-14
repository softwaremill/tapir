package sttp.tapir.internal

import sttp.tapir.Mapping

import scala.compiletime.erasedValue
import scala.deriving.Mirror

private[tapir] object MappingMacros {
  inline def mappingImpl[In, Out <: Product](using mc: Mirror.ProductOf[Out]): Mapping[In, Out] = {
    checkFields[Out, In]

    val to: In => Out = t =>
      inline erasedValue[In] match {
        case _: Tuple => mc.fromProduct(t.asInstanceOf[Tuple])
        case _        => mc.fromProduct(Tuple1(t))
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
      case _: (EmptyTuple, Unit)   => ()
      case _: (B *: EmptyTuple, B) => ()
      case _: (B, B)               => ()
      case e                       => CompileTimeErrors.reportIncorrectMapping[B, A]
    }
}
