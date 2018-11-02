package sapi
import shapeless.HList
import shapeless.ops.hlist.Prepend

case class Endpoint[U[_], I <: HList, O](name: Option[String],
                                         method: U[Method],
                                         input: EndpointInput.Multiple[I],
                                         output: TypeMapper[O, Nothing],
                                         summary: Option[String],
                                         description: Option[String],
                                         okResponseDescription: Option[String],
                                         errorResponseDescription: Option[String],
                                         tags: List[String]) {
  def name(s: String): Endpoint[U, I, O] = this.copy(name = Some(s))

  def get(): Endpoint[Id, I, O] = this.copy[Id, I, O](method = Method.GET)
  def post(): Endpoint[Id, I, O] = this.copy[Id, I, O](method = Method.POST)

  def in[J <: HList, IJ <: HList](i: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): Endpoint[U, IJ, O] =
    this.copy[U, IJ, O](input = input.and(i))

  def out[T, M <: MediaType](implicit tm: TypeMapper[T, M]): Endpoint[U, I, T] = copy[U, I, T](output = tm)

  def summary(s: String): Endpoint[U, I, O] = copy(summary = Some(s))
  def description(d: String): Endpoint[U, I, O] = copy(description = Some(d))
  def okResponseDescription(d: String): Endpoint[U, I, O] = copy(okResponseDescription = Some(d))
  def errorResponseDescription(d: String): Endpoint[U, I, O] = copy(errorResponseDescription = Some(d))
  def tags(ts: List[String]): Endpoint[U, I, O] = copy(tags = ts)
  def tag(t: String): Endpoint[U, I, O] = copy(tags = t :: tags)
}
