package tapir.typelevel

import com.github.ghik.silencer.silent

/**
  * Replace the first parameter of a function from I to J.
  * FN_IK[R] = (IK as args) => R
  * FN_JK[R] = (JK as args) => R
  * IK = (I, A, B, C, ...)
  * JK = (J, A, B, B, ...)
  */
trait ReplaceFirstInFn[I, FN_IK[_], J, FN_JK[_]] {
  def paramsAsArgsIk: ParamsAsArgs.Aux[_, FN_IK]
  def paramsAsArgsJk: ParamsAsArgs.Aux[_, FN_JK]
}

object ReplaceFirstInFn {
  @silent("never used")
  implicit def replaceFirst[FN_IK[_], I, IK, J, JK, FN_JK[_]](
      implicit
      p1: ParamsAsArgs.Aux[IK, FN_IK],
      r: ReplaceFirstInTuple[I, J, IK, JK],
      p2: ParamsAsArgs.Aux[JK, FN_JK]
  ): ReplaceFirstInFn[I, FN_IK, J, FN_JK] =
    new ReplaceFirstInFn[I, FN_IK, J, FN_JK] {
      override def paramsAsArgsIk: ParamsAsArgs.Aux[_, FN_IK] = p1
      override def paramsAsArgsJk: ParamsAsArgs.Aux[_, FN_JK] = p2
    }
}
