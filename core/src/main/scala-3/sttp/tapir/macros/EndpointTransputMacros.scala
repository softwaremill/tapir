package sttp.tapir.macros

import sttp.tapir.EndpointTransput

import scala.compiletime.erasedValue
import scala.deriving.Mirror

trait EndpointTransputMacros[T] { this: EndpointTransput[T] =>
  inline def mapTo[CASE_CLASS <: Product](using mc: Mirror.ProductOf[CASE_CLASS]): ThisType[CASE_CLASS] = {
    checkFields[CASE_CLASS, T]

    val to: T => CASE_CLASS = {
      case t: Tuple => mc.fromProduct(t)
      case t =>   mc.fromProduct(Tuple1(t))
    }
    val from: CASE_CLASS => T = Tuple.fromProduct(_).asInstanceOf[T]

    this.map[CASE_CLASS](to)(from)
  }

  inline def checkFields[A, B](using m: Mirror.ProductOf[A]): Unit =
    inline (erasedValue[m.MirroredElemTypes], erasedValue[B]) match {
      case _: (B *: EmptyTuple, B) => ()
      case _: (B, B) => ()
      case e => scala.compiletime.error("Given case class does not match the endpoint transput.")
    }
}