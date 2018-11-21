package tapir.typelevel

trait ParamsToTuple[I] {
  type Out
  def toTuple(i: I): Out
}

object ParamsToTuple extends LowPriorityParamsToTuple {
  implicit def tuple2ToTuple[A1, A2]: Aux[(A1, A2), (A1, A2)] = new ParamsToTuple[(A1, A2)] {
    override type Out = (A1, A2)
    override def toTuple(i: (A1, A2)): (A1, A2) = i
  }
  implicit def tuple6ToTuple[A1, A2, A3, A4, A5, A6]: Aux[(A1, A2, A3, A4, A5, A6), (A1, A2, A3, A4, A5, A6)] =
    new ParamsToTuple[(A1, A2, A3, A4, A5, A6)] {
      override type Out = (A1, A2, A3, A4, A5, A6)
      override def toTuple(i: (A1, A2, A3, A4, A5, A6)): (A1, A2, A3, A4, A5, A6) = i
    }
}

trait LowPriorityParamsToTuple {
  type Aux[I, T] = ParamsToTuple[I] { type Out = T }

  implicit def singleToTuple[A]: Aux[A, Tuple1[A]] = new ParamsToTuple[A] {
    override type Out = Tuple1[A]
    override def toTuple(i: A): Tuple1[A] = Tuple1(i)
  }
}
