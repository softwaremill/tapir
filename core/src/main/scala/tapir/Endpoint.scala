package tapir

import tapir.EndpointInput.RequestMethod
import tapir.model.Method
import tapir.server.ServerEndpoint
import tapir.typelevel.{FnComponents, ParamConcat, ParamsAsArgs}

/**
  * @tparam I Input parameter types.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  * @tparam S The type of streams that are used by this endpoint's inputs/outputs. `Nothing`, if no streams are used.
  */
case class Endpoint[I, E, O, +S](input: EndpointInput[I], errorOutput: EndpointOutput[E], output: EndpointOutput[O], info: EndpointInfo) {

  def get: Endpoint[I, E, O, S] = in(RequestMethod(Method.GET))
  def post: Endpoint[I, E, O, S] = in(RequestMethod(Method.POST))
  def head: Endpoint[I, E, O, S] = in(RequestMethod(Method.HEAD))
  def put: Endpoint[I, E, O, S] = in(RequestMethod(Method.PUT))
  def delete: Endpoint[I, E, O, S] = in(RequestMethod(Method.DELETE))
  def options: Endpoint[I, E, O, S] = in(RequestMethod(Method.OPTIONS))
  def patch: Endpoint[I, E, O, S] = in(RequestMethod(Method.PATCH))
  def connect: Endpoint[I, E, O, S] = in(RequestMethod(Method.CONNECT))
  def trace: Endpoint[I, E, O, S] = in(RequestMethod(Method.TRACE))
  def method(m: String): Endpoint[I, E, O, S] = in(RequestMethod(Method(m)))

  def in[J, IJ](i: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): Endpoint[IJ, E, O, S] =
    this.copy[IJ, E, O, S](input = input.and(i))

  def in[J, IJ, S2 >: S](i: StreamingEndpointIO[J, S2])(implicit ts: ParamConcat.Aux[I, J, IJ]): Endpoint[IJ, E, O, S2] =
    this.copy[IJ, E, O, S2](input = input.and(i.toEndpointIO))

  def out[P, OP](i: EndpointOutput[P])(implicit ts: ParamConcat.Aux[O, P, OP]): Endpoint[I, E, OP, S] =
    this.copy[I, E, OP, S](output = output.and(i))

  def out[P, OP, S2 >: S](i: StreamingEndpointIO[P, S2])(implicit ts: ParamConcat.Aux[O, P, OP]): Endpoint[I, E, OP, S2] =
    this.copy[I, E, OP, S2](output = output.and(i.toEndpointIO))

  def errorOut[F, EF](i: EndpointOutput[F])(implicit ts: ParamConcat.Aux[E, F, EF]): Endpoint[I, EF, O, S] =
    this.copy[I, EF, O, S](errorOutput = errorOutput.and(i))

  def mapIn[II](f: I => II)(g: II => I)(implicit paramsAsArgs: ParamsAsArgs[I]): Endpoint[II, E, O, S] =
    this.copy[II, E, O, S](input = input.map(f)(g))

  def mapInTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, I, CASE_CLASS],
                                                              paramsAsArgs: ParamsAsArgs[I]): Endpoint[CASE_CLASS, E, O, S] =
    this.copy[CASE_CLASS, E, O, S](input = input.mapTo(c)(fc, paramsAsArgs))

  def mapErrorOut[EE](f: E => EE)(g: EE => E)(implicit paramsAsArgs: ParamsAsArgs[E]): Endpoint[I, EE, O, S] =
    this.copy[I, EE, O, S](errorOutput = errorOutput.map(f)(g))

  def mapErrorOutTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, E, CASE_CLASS],
                                                                    paramsAsArgs: ParamsAsArgs[E]): Endpoint[I, CASE_CLASS, O, S] =
    this.copy[I, CASE_CLASS, O, S](errorOutput = errorOutput.mapTo(c)(fc, paramsAsArgs))

  def mapOut[OO](f: O => OO)(g: OO => O)(implicit paramsAsArgs: ParamsAsArgs[O]): Endpoint[I, E, OO, S] =
    this.copy[I, E, OO, S](output = output.map(f)(g))

  def mapOutTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, O, CASE_CLASS],
                                                               paramsAsArgs: ParamsAsArgs[O]): Endpoint[I, E, CASE_CLASS, S] =
    this.copy[I, E, CASE_CLASS, S](output = output.mapTo(c)(fc, paramsAsArgs))

  def name(n: String): Endpoint[I, E, O, S] = copy(info = info.name(n))
  def summary(s: String): Endpoint[I, E, O, S] = copy(info = info.summary(s))
  def description(d: String): Endpoint[I, E, O, S] = copy(info = info.description(d))
  def tags(ts: List[String]): Endpoint[I, E, O, S] = copy(info = info.tags(ts))
  def tag(t: String): Endpoint[I, E, O, S] = copy(info = info.tag(t))

  def info(i: EndpointInfo): Endpoint[I, E, O, S] = copy(info = i)

  def show: String = {
    s"Endpoint${info.name.map("[" + _ + "]").getOrElse("")}(in: ${input.show}, errout: ${errorOutput.show}, out: ${output.show})"
  }

  def serverLogic[F[_]](f: I => F[Either[E, O]]): ServerEndpoint[I, E, O, S, F] = ServerEndpoint(this, f)
}

case class EndpointInfo(name: Option[String], summary: Option[String], description: Option[String], tags: Vector[String]) {
  def name(n: String): EndpointInfo = this.copy(name = Some(n))
  def summary(s: String): EndpointInfo = copy(summary = Some(s))
  def description(d: String): EndpointInfo = copy(description = Some(d))
  def tags(ts: List[String]): EndpointInfo = copy(tags = tags ++ ts)
  def tag(t: String): EndpointInfo = copy(tags = tags :+ t)
}
