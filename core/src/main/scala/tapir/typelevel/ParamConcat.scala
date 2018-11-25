package tapir.typelevel

import tapir.typelevel.akka.TupleOps

/**
  * Concatenates two parameter lists into one. Each parameter list can be either a single type, or a tuple.
  */
trait ParamConcat[T, U] {
  type Out
}

object ParamConcat extends LowPriorityTupleConcat3 {
  implicit def concatUnitLeft[U]: Aux[Unit, U, U] = null
}

trait LowPriorityTupleConcat3 extends LowPriorityTupleConcat2 {
  implicit def concatUnitRight[U]: Aux[U, Unit, U] = null
}

trait LowPriorityTupleConcat2 extends LowPriorityTupleConcat1 {
  implicit def concatTuples[T, U, TU](implicit tc: TupleOps.JoinAux[T, U, TU]): Aux[T, U, TU] = null
}

trait LowPriorityTupleConcat1 extends LowPriorityTupleConcat0 {
  implicit def concatSingleAndTuple[T, U, TU](implicit tc: TupleOps.JoinAux[Tuple1[T], U, TU]): Aux[T, U, TU] = null
  implicit def concatTupleAndSingle[T, U, TU](implicit tc: TupleOps.JoinAux[T, Tuple1[U], TU]): Aux[T, U, TU] = null
}

trait LowPriorityTupleConcat0 {
  type Aux[T, U, TU] = ParamConcat[T, U] { type Out = TU }

  implicit def concatSingleAndSingle[T, U, TU](implicit tc: TupleOps.JoinAux[Tuple1[T], Tuple1[U], TU]): Aux[T, U, TU] = null
}
