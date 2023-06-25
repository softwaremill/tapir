package sttp.tapir.serverless.aws.sam.parameter

import cats.data.NonEmptyList
import sttp.tapir.serverless.aws.sam.AwsSamOptions
import sttp.tapir.typelevel.ParamConcat

case class AwsSamOptionsParameterized[I](initialOptions: AwsSamOptions, params: InputParameters[I]) {

  def withParameter[IJ](name: String)(implicit concat: ParamConcat.Aux[I, InputParameter, IJ]): AwsSamOptionsParameterized[IJ] =
    withParameter(name, Option.empty[String])

  def withParameter[IJ](name: String, description: Option[String])(implicit
      concat: ParamConcat.Aux[I, InputParameter, IJ]
  ): AwsSamOptionsParameterized[IJ] =
    withParameter(InputParameter(name, description))

  private def withParameter[IJ](param: InputParameter)(implicit
      concat: ParamConcat.Aux[I, InputParameter, IJ]
  ): AwsSamOptionsParameterized[IJ] =
    this.copy(params = InputParameters(param, params))

  def customiseOptions(f: (I, AwsSamOptions) => AwsSamOptions): AwsSamOptions = {
    f(params.combinedParams.asAny.asInstanceOf[I], initialOptions)
      .copy(parameters = NonEmptyList.fromList(params.list))
  }
}

object AwsSamOptionsParameterized {

  def empty(initialOptions: AwsSamOptions): AwsSamOptionsParameterized[Unit] =
    AwsSamOptionsParameterized(initialOptions, InputParameters.Empty)

  def single(initialOptions: AwsSamOptions, name: String, description: Option[String]): AwsSamOptionsParameterized[InputParameter] =
    AwsSamOptionsParameterized.empty(initialOptions).withParameter(InputParameter(name, description))
}
