package tapir

import tapir.internal.DefaultStatusMappers
import tapir.typelevel.{FnComponents, ParamConcat, ParamsAsArgs}

/**
  * @tparam I Input parameter types.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  */
case class Endpoint[I, E, O](method: Method,
                             input: EndpointInput[I],
                             errorOutput: EndpointIO[E],
                             output: EndpointIO[O],
                             info: EndpointInfo) {

  def get: Endpoint[I, E, O] = this.copy[I, E, O](method = Method.GET)
  def head: Endpoint[I, E, O] = this.copy[I, E, O](method = Method.HEAD)
  def post: Endpoint[I, E, O] = this.copy[I, E, O](method = Method.POST)
  def put: Endpoint[I, E, O] = this.copy[I, E, O](method = Method.PUT)
  def delete: Endpoint[I, E, O] = this.copy[I, E, O](method = Method.DELETE)
  def options: Endpoint[I, E, O] = this.copy[I, E, O](method = Method.OPTIONS)
  def patch: Endpoint[I, E, O] = this.copy[I, E, O](method = Method.PATCH)
  def connect: Endpoint[I, E, O] = this.copy[I, E, O](method = Method.CONNECT)
  def trace: Endpoint[I, E, O] = this.copy[I, E, O](method = Method.TRACE)
  def method(m: String): Endpoint[I, E, O] = this.copy[I, E, O](method = Method(m))

  def in[J, IJ](i: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): Endpoint[IJ, E, O] =
    this.copy[IJ, E, O](input = input.and(i))

  def out[P, OP](i: EndpointIO[P])(implicit ts: ParamConcat.Aux[O, P, OP]): Endpoint[I, E, OP] =
    this.copy[I, E, OP](output = output.and(i))

  def errorOut[F, EF](i: EndpointIO[F])(implicit ts: ParamConcat.Aux[E, F, EF]): Endpoint[I, EF, O] =
    this.copy[I, EF, O](errorOutput = errorOutput.and(i))

  def mapIn[II](f: I => II)(g: II => I)(implicit paramsAsArgs: ParamsAsArgs[I]): Endpoint[II, E, O] =
    this.copy[II, E, O](input = input.map(f)(g))

  def mapInTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, I, CASE_CLASS],
                                                              paramsAsArgs: ParamsAsArgs[I]): Endpoint[CASE_CLASS, E, O] =
    this.copy[CASE_CLASS, E, O](input = input.mapTo(c)(fc, paramsAsArgs))

  def mapErrorOut[EE](f: E => EE)(g: EE => E)(implicit paramsAsArgs: ParamsAsArgs[E]): Endpoint[I, EE, O] =
    this.copy[I, EE, O](errorOutput = errorOutput.map(f)(g))

  def mapErrorOutTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, E, CASE_CLASS],
                                                                    paramsAsArgs: ParamsAsArgs[E]): Endpoint[I, CASE_CLASS, O] =
    this.copy[I, CASE_CLASS, O](errorOutput = errorOutput.mapTo(c)(fc, paramsAsArgs))

  def mapOut[OO](f: O => OO)(g: OO => O)(implicit paramsAsArgs: ParamsAsArgs[O]): Endpoint[I, E, OO] =
    this.copy[I, E, OO](output = output.map(f)(g))

  def mapOutTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, O, CASE_CLASS],
                                                               paramsAsArgs: ParamsAsArgs[O]): Endpoint[I, E, CASE_CLASS] =
    this.copy[I, E, CASE_CLASS](output = output.mapTo(c)(fc, paramsAsArgs))

  def name(n: String): Endpoint[I, E, O] = copy(info = info.name(n))
  def summary(s: String): Endpoint[I, E, O] = copy(info = info.summary(s))
  def description(d: String): Endpoint[I, E, O] = copy(info = info.description(d))
  def tags(ts: List[String]): Endpoint[I, E, O] = copy(info = info.tags(ts))
  def tag(t: String): Endpoint[I, E, O] = copy(info = info.tag(t))

  def info(i: EndpointInfo): Endpoint[I, E, O] = copy(info = i)

  def show: String =
    s"Endpoint${info.name.map("[" + _ + "]").getOrElse("")}(${method.m}, in: ${input.show}, errout: ${errorOutput.show}, out: ${output.show})"
}

case class EndpointInfo(name: Option[String], summary: Option[String], description: Option[String], tags: Vector[String]) {
  def name(n: String): EndpointInfo = this.copy(name = Some(n))
  def summary(s: String): EndpointInfo = copy(summary = Some(s))
  def description(d: String): EndpointInfo = copy(description = Some(d))
  def tags(ts: List[String]): EndpointInfo = copy(tags = tags ++ ts)
  def tag(t: String): EndpointInfo = copy(tags = tags :+ t)
}
