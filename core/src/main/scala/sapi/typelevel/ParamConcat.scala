package sapi.typelevel

import sapi.Void
import shapeless.ops.tuple.Prepend

/**
  * Concatenates two parameter lists into one. Each parameter list can be either a single type, or a tuple.
  */
trait ParamConcat[T, U] {
  type Out
}

object ParamConcat extends LowPriorityTupleConcat2 {
  implicit def concatTuples[T, U, TU](implicit tc: Prepend.Aux[T, U, TU]): Aux[T, U, TU] = null
}

trait LowPriorityTupleConcat2 extends LowPriorityTupleConcat1 {
  implicit def concatVoidLeft[U]: Aux[Void, U, U] = null
  implicit def concatVoidRight[U]: Aux[U, Void, U] = null
}

trait LowPriorityTupleConcat1 extends LowPriorityTupleConcat0 {
  implicit def concatSingleAndTuple[T, U, TU](implicit tc: Prepend.Aux[Tuple1[T], U, TU]): Aux[T, U, TU] = null
  implicit def concatTupleAndSingle[T, U, TU](implicit tc: Prepend.Aux[T, Tuple1[U], TU]): Aux[T, U, TU] = null
}

trait LowPriorityTupleConcat0 {
  type Aux[T, U, TU] = ParamConcat[T, U] { type Out = TU }

  implicit def concatSingleAndSingle[T, U, TU](implicit tc: Prepend.Aux[Tuple1[T], Tuple1[U], TU]): Aux[T, U, TU] = null
}
