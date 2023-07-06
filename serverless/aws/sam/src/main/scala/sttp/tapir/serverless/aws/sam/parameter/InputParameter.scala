package sttp.tapir.serverless.aws.sam.parameter

import sttp.tapir.internal.{CombineParams, Params, ParamsAsAny, mkCombine}
import sttp.tapir.serverless.aws.sam.parameter.InputParameters.toList
import sttp.tapir.typelevel.ParamConcat

private[sam] case class InputParameter(name: String, description: Option[String]) {
  val ref: String = s"!Ref $name"
}

private[parameter] sealed trait InputParameters[I] {
  private[sam] lazy val combinedParams = InputParameters.combine(this)

  private[sam] lazy val list: List[InputParameter] = toList(this)
}

private[parameter] object InputParameters {

  private[parameter] case object Empty extends InputParameters[Unit]
  private[parameter] case class Cons[I, IJ](head: InputParameter, tail: InputParameters[I], combiner: CombineParams)
      extends InputParameters[IJ]

  def apply[I, IJ](head: InputParameter, tail: InputParameters[I])(implicit
      concat: ParamConcat.Aux[I, InputParameter, IJ]
  ): InputParameters[IJ] = Cons[I, IJ](head, tail, mkCombine(concat))

  private[parameter] def combine(params: InputParameters[_]): Params =
    params match {
      case InputParameters.Cons(theLast, firstInRow, combiner) => combiner(combine(firstInRow), ParamsAsAny(theLast))
      case InputParameters.Empty                               => ParamsAsAny(())
    }

  private[parameter] def toList(params: InputParameters[_]): List[InputParameter] = {
    def rec(acc: List[InputParameter], remaining: InputParameters[_]): List[InputParameter] =
      remaining match {
        case InputParameters.Empty               => acc
        case InputParameters.Cons(head, tail, _) => rec(head :: acc, tail)
      }
    rec(Nil, params)
  }

}
