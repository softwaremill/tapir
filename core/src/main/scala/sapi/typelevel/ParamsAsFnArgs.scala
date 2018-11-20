package sapi.typelevel

/**
  * Views parameters (single type or a tuple) as arguments of a function of the appropriate arity.
  */
trait ParamsAsFnArgs[I, FN[_]] {
  def toFn[O](f: I => O): FN[O]
  def argAt(args: I, i: Int): Any

  def applyFn[R](f: FN[R], args: I): R
}

object ParamsAsFnArgs extends LowPriorityParamsToFn {
  implicit def tuple2ToFn[A1, A2]: ParamsAsFnArgs[(A1, A2), (A1, A2) => ?] = new ParamsAsFnArgs[(A1, A2), (A1, A2) => ?] {
    override def toFn[R](f: ((A1, A2)) => R): (A1, A2) => R = (a1, a2) => f((a1, a2))
    override def argAt(args: (A1, A2), i: Int): Any = args.productElement(i)
    override def applyFn[R](f: (A1, A2) => R, args: (A1, A2)): R = f(args._1, args._2)
  }

  implicit def tuple6ToFn[A1, A2, A3, A4, A5, A6]: ParamsAsFnArgs[(A1, A2, A3, A4, A5, A6), (A1, A2, A3, A4, A5, A6) => ?] =
    new ParamsAsFnArgs[(A1, A2, A3, A4, A5, A6), (A1, A2, A3, A4, A5, A6) => ?] {
      override def toFn[R](f: ((A1, A2, A3, A4, A5, A6)) => R): (A1, A2, A3, A4, A5, A6) => R =
        (a1, a2, a3, a4, a5, a6) => f((a1, a2, a3, a4, a5, a6))
      override def argAt(args: (A1, A2, A3, A4, A5, A6), i: Int): Any = args.productElement(i)
      override def applyFn[R](f: (A1, A2, A3, A4, A5, A6) => R, args: (A1, A2, A3, A4, A5, A6)): R =
        f(args._1, args._2, args._3, args._4, args._5, args._6)
    }
}

trait LowPriorityParamsToFn {
  implicit def singleToFn[A1]: ParamsAsFnArgs[A1, A1 => ?] = new ParamsAsFnArgs[A1, A1 => ?] {
    override def toFn[R](f: A1 => R): A1 => R = a1 => f(a1)
    override def argAt(args: A1, i: Int): Any = if (i == 0) args else throw new IndexOutOfBoundsException(i.toString)
    override def applyFn[R](f: A1 => R, args: A1): R = f(args)
  }
}
