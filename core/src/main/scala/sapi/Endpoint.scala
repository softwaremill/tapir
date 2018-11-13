package sapi
import shapeless.HList
import shapeless.ops.hlist.Prepend

case class Endpoint[I <: HList, O <: HList, E <: HList](name: Option[String], // TODO: swap O and E
                                                        method: Method,
                                                        input: EndpointInput.Multiple[I],
                                                        output: EndpointOutput.Multiple[O],
                                                        errorOutput: EndpointOutput.Multiple[E],
                                                        summary: Option[String],
                                                        description: Option[String],
                                                        tags: Vector[String]) {
  def name(s: String): Endpoint[I, O, E] = this.copy(name = Some(s))

  def get(): Endpoint[I, O, E] = this.copy[I, O, E](method = Method.GET)
  def post(): Endpoint[I, O, E] = this.copy[I, O, E](method = Method.POST)

  def in[J <: HList, IJ <: HList](i: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): Endpoint[IJ, O, E] =
    this.copy[IJ, O, E](input = input.and(i))

  def out[P <: HList, OP <: HList](i: EndpointOutput[P])(implicit ts: Prepend.Aux[O, P, OP]): Endpoint[I, OP, E] =
    this.copy[I, OP, E](output = output.and(i))

  def errorOut[F <: HList, EF <: HList](i: EndpointOutput[F])(implicit ts: Prepend.Aux[E, F, EF]): Endpoint[I, O, EF] =
    this.copy[I, O, EF](errorOutput = errorOutput.and(i))

  def summary(s: String): Endpoint[I, O, E] = copy(summary = Some(s))
  def description(d: String): Endpoint[I, O, E] = copy(description = Some(d))
  def tags(ts: List[String]): Endpoint[I, O, E] = copy(tags = tags ++ ts)
  def tag(t: String): Endpoint[I, O, E] = copy(tags = tags :+ t)
}
