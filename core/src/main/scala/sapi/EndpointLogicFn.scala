package sapi

import shapeless._
import shapeless.ops.function.FnFromProduct

trait EndpointLogicFn[I <: HList, E <: HList, O <: HList] {
  type TE
  type TO

  val eToResult: HListToResult.Aux[E, TE]
  val oToResult: HListToResult.Aux[O, TO]
}

object EndpointLogicFn {
  implicit def endpointLogicFn[I <: HList, E <: HList, O <: HList, _TE, _TO](
      implicit _eToResult: HListToResult.Aux[E, _TE],
      _oToResult: HListToResult.Aux[O, _TO]): EndpointLogicFn[I, E, O] =
    new EndpointLogicFn[I, E, O] {
      type TE = _TE
      type TO = _TO

      override val eToResult: HListToResult.Aux[E, TE] = _eToResult
      override val oToResult: HListToResult.Aux[O, TO] = _oToResult
    }

  trait FnFromHList[I <: HList, Out[_]] {
    def apply[R](f: I => R): Out[R]
  }

  implicit def fnFromHlist1[A1]: FnFromHList[A1 :: HNil, A1 => ?] = new FnFromHList[A1 :: HNil, A1 => ?] {
    override def apply[R](f: A1 :: HNil => R): A1 => R = a1 => f(a1 :: HNil)
  }

  implicit def fnFromHlist2[A1, A2]: FnFromHList[A1 :: A2 :: HNil, (A1, A2) => ?] = new FnFromHList[A1 :: A2 :: HNil, (A1, A2) => ?] {
    override def apply[R](f: A1 :: A2 :: HNil => R): (A1, A2) => R = (a1, a2) => f(a1 :: a2 :: HNil)
  }

  implicit def fnFromHlist6[A1, A2, A3, A4, A5, A6]: FnFromHList[A1 :: A2 :: A3 :: A4 :: A5 :: A6 :: HNil, (A1, A2, A3, A4, A5, A6) => ?] =
    new FnFromHList[A1 :: A2 :: A3 :: A4 :: A5 :: A6 :: HNil, (A1, A2, A3, A4, A5, A6) => ?] {
      override def apply[R](f: A1 :: A2 :: A3 :: A4 :: A5 :: A6 :: HNil => R): (A1, A2, A3, A4, A5, A6) => R =
        (a1, a2, a3, a4, a5, a6) => f(a1 :: a2 :: a3 :: a4 :: a5 :: a6 :: HNil)
    }
}
