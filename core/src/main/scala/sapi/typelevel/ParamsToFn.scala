package sapi.typelevel

trait ParamsToFn[I, Out[_]] {
  def apply[R](f: I => R): Out[R]
  def arg(args: I, i: Int): Any
}

object ParamsToFn extends LowPriorityParamsToFn {
  implicit def tuple2ToFn[A1, A2]: ParamsToFn[(A1, A2), (A1, A2) => ?] = new ParamsToFn[(A1, A2), (A1, A2) => ?] {
    override def apply[R](f: ((A1, A2)) => R): (A1, A2) => R = (a1, a2) => f((a1, a2))
    override def arg(args: (A1, A2), i: Int): Any = args.productElement(i)
  }

  implicit def tuple6ToFn[A1, A2, A3, A4, A5, A6]: ParamsToFn[(A1, A2, A3, A4, A5, A6), (A1, A2, A3, A4, A5, A6) => ?] =
    new ParamsToFn[(A1, A2, A3, A4, A5, A6), (A1, A2, A3, A4, A5, A6) => ?] {
      override def apply[R](f: ((A1, A2, A3, A4, A5, A6)) => R): (A1, A2, A3, A4, A5, A6) => R =
        (a1, a2, a3, a4, a5, a6) => f((a1, a2, a3, a4, a5, a6))
      override def arg(args: (A1, A2, A3, A4, A5, A6), i: Int): Any = args.productElement(i)
    }
}

trait LowPriorityParamsToFn {
  implicit def singleToFn[A1]: ParamsToFn[A1, A1 => ?] = new ParamsToFn[A1, A1 => ?] {
    override def apply[R](f: A1 => R): A1 => R = a1 => f(a1)
    override def arg(args: A1, i: Int): Any = if (i == 0) args else throw new IndexOutOfBoundsException(i.toString)
  }
}
