package sapi.typelevel
import shapeless.ops.tuple.Prepend

/**
  * Concatenates two parameter lists into one. Each parameter list can be either a single type, or a tuple.
  */
trait ParamConcat[T, U] {
  type Out
}

object ParamConcat extends LowPriorityTupleConcat {
  implicit def concatTuples[T, U, TU](implicit tc: Prepend.Aux[T, U, TU]): Aux[T, U, TU] = null
}

trait LowPriorityTupleConcat extends LowestPriorityTupleConcat {
  implicit def concatNothingLeft[U]: Aux[Nothing, U, U] = null
  implicit def concatNothingRight[U]: Aux[U, Nothing, U] = null
}

trait LowestPriorityTupleConcat {
  type Aux[T, U, TU] = ParamConcat[T, U] { type Out = TU }

  implicit def concatSingleAndTuple[T, U, TU](implicit tc: Prepend.Aux[Tuple1[T], U, TU]): Aux[T, U, TU] = null
  implicit def concatTupleAndSingle[T, U, TU](implicit tc: Prepend.Aux[T, Tuple1[U], TU]): Aux[T, U, TU] = null
}
