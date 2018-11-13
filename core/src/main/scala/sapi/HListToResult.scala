package sapi

import shapeless._
import shapeless.ops.hlist.Tupler

trait HListToResult[L <: HList] extends DepFn1[L]

object HListToResult extends HListToResultInstances {
  def apply[L <: HList](implicit hlistToResult: HListToResult[L]): Aux[L, hlistToResult.Out] = hlistToResult
}

trait HListToResultInstances {
  type Aux[L <: HList, Out0] = HListToResult[L] {
    type Out = Out0
  }
  implicit val hnilResult: Aux[HNil, Unit] = // TODO: not nothing?
    new HListToResult[HNil] {
      type Out = Unit
      def apply(l: HNil): Out = ()
    }
  implicit def hlistToResult1[A]: Aux[A :: HNil, A] = new HListToResult[A :: HNil] {
    type Out = A
    def apply(l: A :: HNil): Out = l.head
  }
  implicit def hlistToResultOver2[A, B, T <: HList](implicit tupler: Tupler[A :: B :: T]): Aux[A :: B :: T, tupler.Out] =
    new HListToResult[A :: B :: T] {
      type Out = tupler.Out
      def apply(l: A :: B :: T): Out = tupler(l)
    }
}
