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
}
