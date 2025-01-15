package sttp.tapir.typelevel

/** Concatenates two parameter lists into one. Each parameter list can be either a single type, or a tuple.
  *
  * The arity of a type if `0` if it's `Unit` /`Nothing`, as these types act as a neutral element in the tuple-concatenation operation that
  * takes place when combining inputs/outputs.
  *
  * The arity of a type is `1` if it's a "singular" type (as viewed at compile-time; at run-time, the value might in fact be a tuple).
  *
  * Otherwise, the arity of a tuple.
  */
trait ParamConcat[T, U] extends BinaryTupleOp {
  type Out
}

object ParamConcat extends LowPriorityTupleConcat4 {
  implicit def concatUnitUnit[U]: Aux[Unit, Unit, Unit] =
    new ParamConcat[Unit, Unit] {
      override type Out = Unit
      override def leftArity = 0
      override def rightArity = 0
    }
  implicit def concatNothingNothing[U]: Aux[Nothing, Nothing, Nothing] =
    new ParamConcat[Nothing, Nothing] {
      override type Out = Nothing
      override def leftArity = 0
      override def rightArity = 0
    }
  implicit def concatNothingUnit[U]: Aux[Nothing, Unit, Unit] =
    new ParamConcat[Nothing, Unit] {
      override type Out = Unit
      override def leftArity = 0
      override def rightArity = 0
    }
  implicit def concatUnitNothing[U]: Aux[Unit, Nothing, Unit] =
    new ParamConcat[Unit, Nothing] {
      override type Out = Unit
      override def leftArity = 0
      override def rightArity = 0
    }
}

trait LowPriorityTupleConcat4 extends LowPriorityTupleConcat3 {
  implicit def concatUnitLeft[U](implicit ua: TupleArity[U]): Aux[Unit, U, U] =
    new ParamConcat[Unit, U] {
      override type Out = U
      override def leftArity: Int = 0
      override def rightArity: Int = ua.arity
    }
  implicit def concatNothingLeft[U](implicit ua: TupleArity[U]): Aux[Nothing, U, U] =
    new ParamConcat[Nothing, U] {
      override type Out = U
      override def leftArity: Int = 0
      override def rightArity: Int = ua.arity
    }
}

trait LowPriorityTupleConcat3 extends LowPriorityTupleConcat2 {
  implicit def concatUnitRight[T](implicit ta: TupleArity[T]): Aux[T, Unit, T] =
    new ParamConcat[T, Unit] {
      override type Out = T
      override def leftArity: Int = ta.arity
      override def rightArity: Int = 0
    }
  // for void outputs
  implicit def concatNothingRight[T](implicit ta: TupleArity[T]): Aux[T, Nothing, T] =
    new ParamConcat[T, Nothing] {
      override type Out = T
      override def leftArity: Int = ta.arity
      override def rightArity: Int = 0
    }
}

trait LowPriorityTupleConcat2 extends LowPriorityTupleConcat1 {
  implicit def concatTuples[T, U, TU](implicit tc: TupleOps.JoinAux[T, U, TU], ta: TupleArity[T], ua: TupleArity[U]): Aux[T, U, TU] =
    new ParamConcat[T, U] {
      override type Out = TU
      override def leftArity: Int = ta.arity
      override def rightArity: Int = ua.arity
    }
}

trait LowPriorityTupleConcat1 extends LowPriorityTupleConcat0 {
  implicit def concatSingleAndTuple[T, U, TU](implicit tc: TupleOps.JoinAux[Tuple1[T], U, TU], ua: TupleArity[U]): Aux[T, U, TU] =
    new ParamConcat[T, U] {
      override type Out = TU
      override def leftArity = 1
      override def rightArity: Int = ua.arity
    }
  implicit def concatTupleAndSingle[T, U, TU](implicit tc: TupleOps.JoinAux[T, Tuple1[U], TU], ta: TupleArity[T]): Aux[T, U, TU] =
    new ParamConcat[T, U] {
      override type Out = TU
      override def leftArity: Int = ta.arity
      override def rightArity = 1
    }
}

trait LowPriorityTupleConcat0 {
  type Aux[T, U, TU] = ParamConcat[T, U] { type Out = TU }

  implicit def concatSingleAndSingle[T, U, TU](implicit tc: TupleOps.JoinAux[Tuple1[T], Tuple1[U], TU]): Aux[T, U, TU] =
    new ParamConcat[T, U] {
      override type Out = TU
      override def leftArity = 1
      override def rightArity = 1
    }
}
