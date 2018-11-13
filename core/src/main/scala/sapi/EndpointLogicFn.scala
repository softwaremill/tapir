package sapi
import shapeless.HList
import shapeless.ops.function.FnFromProduct

trait EndpointLogicFn[I <: HList, E <: HList, O <: HList, M[_], FN] { // FN == I => M[Either[E, O]]
  type TE
  type TO

  val eToResult: HListToResult.Aux[E, TE]
  val oToResult: HListToResult.Aux[O, TO]
  val fnFromProduct: FnFromProduct.Aux[I => M[Either[TE, TO]], FN]
}

object EndpointLogicFn {
  implicit def endpointLogicFn[I <: HList, E <: HList, O <: HList, _TE, _TO, M[_], FN](
      implicit _eToResult: HListToResult.Aux[E, _TE],
      _oToResult: HListToResult.Aux[O, _TO],
      _fnFromProduct: FnFromProduct.Aux[I => M[Either[_TE, _TO]], FN]): EndpointLogicFn[I, E, O, M, FN] =
    new EndpointLogicFn[I, E, O, M, FN] {
      type TE = _TE
      type TO = _TO

      override val eToResult: HListToResult.Aux[E, TE] = _eToResult
      override val oToResult: HListToResult.Aux[O, TO] = _oToResult
      override val fnFromProduct: FnFromProduct.Aux[I => M[Either[TE, TO]], FN] = _fnFromProduct
    }
}
