package sttp.tapir.typelevel

/** Implicits values of this type are defined when `T` is a prefix of the tuple `TU`. Then, `Out` (`= U`) is the type of
  * the rest of the tuple.
  *
  * See also [[ParamConcat]] for information on arity.
  */
trait ParamSubtract[TU, T] extends BinaryTupleOp {
  type Out
}

object ParamSubtract extends LowPriorityTupleSubtract1 {
  implicit def tMinusUnit[T](implicit ta: TupleArity[T]): ParamSubtract.Aux[T, Unit, T] =
    new ParamSubtract[T, Unit] {
      type Out = T
      override def leftArity: Int = 0
      override def rightArity: Int = ta.arity
    }
}

trait LowPriorityTupleSubtract1 extends LowPriorityTupleSubtract0 {
  implicit def tMinusT[T](implicit ta: TupleArity[T]): ParamSubtract.Aux[T, T, Unit] =
    new ParamSubtract[T, T] {
      type Out = Unit
      override def leftArity: Int = ta.arity
      override def rightArity: Int = 0
    }
}

trait LowPriorityTupleSubtract0 {
  type Aux[TU, T, U] = ParamSubtract[TU, T] { type Out = U }

  implicit def inductive[TAB, T, AB, TA, A, B](implicit
      thatTab: TupleHeadAndTail[TAB, T, AB],
      thatTa: TupleHeadAndTail[TA, T, A],
      base: ParamSubtract.Aux[AB, A, B],
      taa: TupleArity[TA],
      ba: TupleArity[B]
  ): ParamSubtract.Aux[TAB, TA, B] =
    new ParamSubtract[TAB, TA] {
      type Out = B
      override def leftArity: Int = taa.arity
      override def rightArity: Int = ba.arity
    }

  implicit def single[TA, T, A](implicit
      that: TupleHeadAndTail[TA, T, A],
      ta: TupleArity[T],
      aa: TupleArity[A]
  ): ParamSubtract.Aux[TA, T, A] =
    new ParamSubtract[TA, T] {
      type Out = A
      override def leftArity: Int = ta.arity
      override def rightArity: Int = aa.arity
    }
}
