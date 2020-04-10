package sttp.tapir.typelevel

import com.github.ghik.silencer.silent

/**
  * Concatenates two parameter lists into one. Each parameter list can be either a single type, or a tuple.
  */
trait ParamConcat[T, U] {
  type Out

  /**
    * Is the left type of the concatenation (`T`) a tuple, as viewed at compile-time (at run-time, this value might be
    * a tuple).
    */
  def leftIsTuple: Boolean

  /**
    * Is the right type of the concatenation (`U`) a tuple, as viewed at compile-time (at run-time, this value might be
    * a tuple).
    */
  def rightIsTuple: Boolean
  def bothTuples: Boolean = leftIsTuple && rightIsTuple
}

object ParamConcat extends LowPriorityTupleConcat3 {
  implicit def concatUnitLeft[U]: Aux[Unit, U, U] = new ParamConcat[Unit, U] {
    override type Out = U
    override def leftIsTuple: Boolean = true
    override def rightIsTuple: Boolean = false
  }
  // for void outputs
  implicit def concatNothingLeft[U]: Aux[Nothing, U, U] = new ParamConcat[Nothing, U] {
    override type Out = U
    override def leftIsTuple: Boolean = true
    override def rightIsTuple: Boolean = false
  }
}

trait LowPriorityTupleConcat3 extends LowPriorityTupleConcat2 {
  implicit def concatUnitRight[U]: Aux[U, Unit, U] = new ParamConcat[U, Unit] {
    override type Out = U
    override def leftIsTuple: Boolean = false
    override def rightIsTuple: Boolean = true
  }
  // for void outputs
  implicit def concatNothingRight[U]: Aux[U, Nothing, U] = new ParamConcat[U, Nothing] {
    override type Out = U
    override def leftIsTuple: Boolean = false
    override def rightIsTuple: Boolean = true
  }
}

trait LowPriorityTupleConcat2 extends LowPriorityTupleConcat1 {
  @silent("never used")
  implicit def concatTuples[T, U, TU](implicit tc: TupleOps.JoinAux[T, U, TU]): Aux[T, U, TU] = new ParamConcat[T, U] {
    override type Out = TU
    override def leftIsTuple: Boolean = true
    override def rightIsTuple: Boolean = true
  }
}

trait LowPriorityTupleConcat1 extends LowPriorityTupleConcat0 {
  @silent("never used")
  implicit def concatSingleAndTuple[T, U, TU](implicit tc: TupleOps.JoinAux[Tuple1[T], U, TU]): Aux[T, U, TU] = new ParamConcat[T, U] {
    override type Out = TU
    override def leftIsTuple: Boolean = false
    override def rightIsTuple: Boolean = true
  }
  @silent("never used")
  implicit def concatTupleAndSingle[T, U, TU](implicit tc: TupleOps.JoinAux[T, Tuple1[U], TU]): Aux[T, U, TU] = new ParamConcat[T, U] {
    override type Out = TU
    override def leftIsTuple: Boolean = true
    override def rightIsTuple: Boolean = false
  }
}

trait LowPriorityTupleConcat0 {
  type Aux[T, U, TU] = ParamConcat[T, U] { type Out = TU }

  @silent("never used")
  implicit def concatSingleAndSingle[T, U, TU](implicit tc: TupleOps.JoinAux[Tuple1[T], Tuple1[U], TU]): Aux[T, U, TU] =
    new ParamConcat[T, U] {
      override type Out = TU
      override def leftIsTuple: Boolean = false
      override def rightIsTuple: Boolean = false
    }
}
