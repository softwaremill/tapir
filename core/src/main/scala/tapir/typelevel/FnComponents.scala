package tapir.typelevel

/*
 * FN.tupled = TUPLE => RESULT
 */
trait FnComponents[-FN, TUPLE, RESULT] {
  def tupled(f: FN): TUPLE => RESULT
  def arity: Int
}

object FnComponents {
  implicit def fn1[T1, R]: FnComponents[T1 => R, T1, R] = new FnComponents[T1 => R, T1, R] {
    override def tupled(f: T1 => R): T1 => R = f
    override def arity: Int = 1
  }
  implicit def fn2[T1, T2, R]: FnComponents[(T1, T2) => R, (T1, T2), R] = new FnComponents[(T1, T2) => R, (T1, T2), R] {
    override def tupled(f: (T1, T2) => R): ((T1, T2)) => R = f.tupled
    override def arity: Int = 2
  }
  implicit def fn3[T1, T2, T3, R]: FnComponents[(T1, T2, T3) => R, (T1, T2, T3), R] =
    new FnComponents[(T1, T2, T3) => R, (T1, T2, T3), R] {
      override def tupled(f: (T1, T2, T3) => R): ((T1, T2, T3)) => R = f.tupled
      override def arity: Int = 3
    }
  implicit def fn4[T1, T2, T3, T4, R]: FnComponents[(T1, T2, T3, T4) => R, (T1, T2, T3, T4), R] =
    new FnComponents[(T1, T2, T3, T4) => R, (T1, T2, T3, T4), R] {
      override def tupled(f: (T1, T2, T3, T4) => R): ((T1, T2, T3, T4)) => R = f.tupled
      override def arity: Int = 4
    }
  implicit def fn5[T1, T2, T3, T4, T5, R]: FnComponents[(T1, T2, T3, T4, T5) => R, (T1, T2, T3, T4, T5), R] =
    new FnComponents[(T1, T2, T3, T4, T5) => R, (T1, T2, T3, T4, T5), R] {
      override def tupled(f: (T1, T2, T3, T4, T5) => R): ((T1, T2, T3, T4, T5)) => R = f.tupled
      override def arity: Int = 5
    }
  implicit def fn6[T1, T2, T3, T4, T5, T6, R]: FnComponents[(T1, T2, T3, T4, T5, T6) => R, (T1, T2, T3, T4, T5, T6), R] =
    new FnComponents[(T1, T2, T3, T4, T5, T6) => R, (T1, T2, T3, T4, T5, T6), R] {
      override def tupled(f: (T1, T2, T3, T4, T5, T6) => R): ((T1, T2, T3, T4, T5, T6)) => R = f.tupled
      override def arity: Int = 6
    }
}
