package tapir

import tapir.typelevel.{ParamConcat, ParamsAsArgs}

/**
  * @tparam I Input parameter types.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  */
case class Endpoint[I, E, O](method: Method,
                             input: EndpointInput.Multiple[I],
                             errorOutput: EndpointOutput.Multiple[E],
                             output: EndpointOutput.Multiple[O],
                             name: Option[String],
                             summary: Option[String],
                             description: Option[String],
                             tags: Vector[String]) {
  def name(s: String): Endpoint[I, E, O] = this.copy(name = Some(s))

  def get: Endpoint[I, E, O] = this.copy[I, E, O](method = Method.GET)
  def post: Endpoint[I, E, O] = this.copy[I, E, O](method = Method.POST)
  def method(m: String): Endpoint[I, E, O] = this.copy[I, E, O](method = Method(m))

  def in[J, IJ](i: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): Endpoint[IJ, E, O] =
    this.copy[IJ, E, O](input = input.and(i))

  def out[P, OP](i: EndpointOutput[P])(implicit ts: ParamConcat.Aux[O, P, OP]): Endpoint[I, E, OP] =
    this.copy[I, E, OP](output = output.and(i))

  def errorOut[F, EF](i: EndpointOutput[F])(implicit ts: ParamConcat.Aux[E, F, EF]): Endpoint[I, EF, O] =
    this.copy[I, EF, O](errorOutput = errorOutput.and(i))

  def mapIn[T](f: I => T)(g: T => I)(implicit paramsAsArgs: ParamsAsArgs[I]): Endpoint[T, E, O] =
    this.copy[T, E, O](input = EndpointInput.Multiple(input.map(f)(g)))

  def summary(s: String): Endpoint[I, E, O] = copy(summary = Some(s))
  def description(d: String): Endpoint[I, E, O] = copy(description = Some(d))
  def tags(ts: List[String]): Endpoint[I, E, O] = copy(tags = tags ++ ts)
  def tag(t: String): Endpoint[I, E, O] = copy(tags = tags :+ t)

  def show: String =
    s"Endpoint${name.map("[" + _ + "]").getOrElse("")}(${method.m}, in: ${input.show}, errout: ${errorOutput.show}, out: ${output.show})"
}
