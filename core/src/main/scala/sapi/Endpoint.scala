package sapi
import shapeless.HList
import shapeless.ops.hlist.Prepend

case class Endpoint[I <: HList, O <: HList, OE <: HList](name: Option[String],
                                                         method: Method,
                                                         input: EndpointInput.Multiple[I],
                                                         output: EndpointOutput.Multiple[O],
                                                         errorOutput: EndpointOutput.Multiple[OE],
                                                         summary: Option[String],
                                                         description: Option[String],
                                                         tags: Vector[String]) {
  def name(s: String): Endpoint[I, O, OE] = this.copy(name = Some(s))

  def get(): Endpoint[I, O, OE] = this.copy[I, O, OE](method = Method.GET)
  def post(): Endpoint[I, O, OE] = this.copy[I, O, OE](method = Method.POST)

  def in[J <: HList, IJ <: HList](i: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): Endpoint[IJ, O, OE] =
    this.copy[IJ, O, OE](input = input.and(i))

  def out[P <: HList, OP <: HList](i: EndpointOutput[P])(implicit ts: Prepend.Aux[O, P, OP]): Endpoint[I, OP, OE] =
    this.copy[I, OP, OE](output = output.and(i))

  def errorOut[P <: HList, OEP <: HList](i: EndpointOutput[P])(implicit ts: Prepend.Aux[OE, P, OEP]): Endpoint[I, O, OEP] =
    this.copy[I, O, OEP](errorOutput = errorOutput.and(i))

  def summary(s: String): Endpoint[I, O, OE] = copy(summary = Some(s))
  def description(d: String): Endpoint[I, O, OE] = copy(description = Some(d))
  def tags(ts: List[String]): Endpoint[I, O, OE] = copy(tags = tags ++ ts)
  def tag(t: String): Endpoint[I, O, OE] = copy(tags = tags :+ t)
}
