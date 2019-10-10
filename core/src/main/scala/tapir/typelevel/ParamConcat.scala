package tapir.typelevel

import com.github.ghik.silencer.silent

/**
  * Concatenates two parameter lists into one. Each parameter list can be either a single type, or a tuple.
  */
trait ParamConcat[T, U] {
  type Out
}

object ParamConcat extends LowPriorityTupleConcat3 {
  implicit def concatUnitLeft[U]: Aux[Unit, U, U] = null
  implicit def concatNothingLeft[U]: Aux[Nothing, U, U] = null // for void outputs
}

trait LowPriorityTupleConcat3 extends LowPriorityTupleConcat2 {
  implicit def concatUnitRight[U]: Aux[U, Unit, U] = null
  implicit def concatNothingRight[U]: Aux[U, Nothing, U] = null // for void outputs
}

trait LowPriorityTupleConcat2 extends LowPriorityTupleConcat1 {
  @silent("never used")
  implicit def concatTuples[T, U, TU](implicit tc: TupleOps.JoinAux[T, U, TU]): Aux[T, U, TU] = null
}

trait LowPriorityTupleConcat1 extends LowPriorityTupleConcat0 {
  @silent("never used")
  implicit def concatSingleAndTuple[T, U, TU](implicit tc: TupleOps.JoinAux[Tuple1[T], U, TU]): Aux[T, U, TU] = null
  @silent("never used")
  implicit def concatTupleAndSingle[T, U, TU](implicit tc: TupleOps.JoinAux[T, Tuple1[U], TU]): Aux[T, U, TU] = null
}

trait LowPriorityTupleConcat0 {
  type Aux[T, U, TU] = ParamConcat[T, U] { type Out = TU }

  @silent("never used")
  implicit def concatSingleAndSingle[T, U, TU](implicit tc: TupleOps.JoinAux[Tuple1[T], Tuple1[U], TU]): Aux[T, U, TU] = null
}
