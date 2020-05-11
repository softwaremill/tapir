package sttp.tapir

import sttp.model.Method
import sttp.tapir.EndpointInput.FixedMethod
import sttp.tapir.RenderPathTemplate.{RenderPathParam, RenderQueryParam}
import sttp.tapir.monad.Monad
import sttp.tapir.server.{ServerEndpointInParts, PartialServerEndpoint, ServerEndpoint}
import sttp.tapir.typelevel.{FnComponents, ParamConcat, ParamSubtract}
import sttp.tapir.internal._

/**
  * @tparam I Input parameter types.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  * @tparam S The type of streams that are used by this endpoint's inputs/outputs. `Nothing`, if no streams are used.
  */
case class Endpoint[I, E, O, +S](input: EndpointInput[I], errorOutput: EndpointOutput[E], output: EndpointOutput[O], info: EndpointInfo)
    extends EndpointInputsOps[I, E, O, S]
    with EndpointErrorOutputsOps[I, E, O, S]
    with EndpointOutputsOps[I, E, O, S]
    with EndpointInfoOps[I, E, O, S]
    with EndpointMetaOps[I, E, O, S] { outer =>

  override type EndpointType[_I, _E, _O, +_S] = Endpoint[_I, _E, _O, _S]
  override private[tapir] def withInput[I2, S2 >: S](input: EndpointInput[I2]): Endpoint[I2, E, O, S2] = this.copy(input = input)
  override private[tapir] def withErrorOutput[E2, S2 >: S](errorOutput: EndpointOutput[E2]): Endpoint[I, E2, O, S2] =
    this.copy(errorOutput = errorOutput)
  override private[tapir] def withOutput[O2, S2 >: S](output: EndpointOutput[O2]): Endpoint[I, E, O2, S2] = this.copy(output = output)
  override private[tapir] def withInfo(info: EndpointInfo): Endpoint[I, E, O, S] = this.copy(info = info)

  override protected def showType: String = "Endpoint"

  /**
    * Combine this endpoint description with a function, which implements the server-side logic. The logic returns
    * a result, which is either an error or a successful output, wrapped in an effect type `F`.
    *
    * A server endpoint can be passed to a server interpreter. Each server interpreter supports effects of a specific
    * type(s).
    *
    * Both the endpoint and logic function are considered complete, and cannot be later extended through the
    * returned [[ServerEndpoint]] value (except for endpoint meta-data). To provide the logic in parts, see
    * [[serverLogicPart]] and [[serverLogicForCurrent]].
    */
  def serverLogic[F[_]](f: I => F[Either[E, O]]): ServerEndpoint[I, E, O, S, F] = ServerEndpoint(this, _ => f)

  /**
    * Combine this endpoint description with a function, which implements a part of the server-side logic. The
    * partial logic returns a result, which is either an error or a success value, wrapped in an effect type `F`.
    *
    * Subsequent parts of the logic can be provided later using [[ServerEndpointInParts.andThenPart]], consuming
    * successive input parts. Finally, the logic can be completed, given a function which accepts as arguments the
    * results of applying on part-functions, and the remaining input. The final result is then a [[ServerEndpoint]].
    *
    * A complete server endpoint can be passed to a server interpreter. Each server interpreter supports effects
    * of a specific type(s).
    *
    * When using this method, the endpoint description is considered complete, and cannot be later extended through
    * the returned [[ServerEndpointInParts]] value. However, each part of the server logic can consume only some
    * of the input. To provide the logic in parts, while still being able to extend the endpoint description, see
    * [[serverLogicForCurrent]].
    *
    * An example use-case is providing authorization logic, followed by server logic (using an authorized user), given
    * a complete endpoint description.
    */
  def serverLogicPart[T, R, U, UR, F[_]](
      part: T => F[Either[E, U]]
  )(implicit iMinusR: ParamSubtract.Aux[I, T, R]): ServerEndpointInParts[U, R, I, E, O, S, F] = {
    type _T = T
    new ServerEndpointInParts[U, R, I, E, O, S, F](this) {
      override type T = _T
      override def splitInput: I => (T, R) = i => split(i)(iMinusR)
      override def logicFragment: Monad[F] => _T => F[Either[E, U]] = _ => part
    }
  }

  /**
    * Combine this endpoint description with a function, which implements a part of the server-side logic, for the
    * entire input defined so far. The partial logic returns a result, which is either an error or a success value,
    * wrapped in an effect type `F`.
    *
    * Subsequently, the endpoint inputs and outputs can be extended (but not error outputs!). Then, either further
    * parts of the server logic can be provided (again, consuming the whole input defined so far). Or, the entire
    * remaining server logic can be provided, given a function which accepts as arguments the results of applying
    * the part-functions, and the remaining input. The final result is then a [[ServerEndpoint]].
    *
    * A complete server endpoint can be passed to a server interpreter. Each server interpreter supports effects
    * of a specific type(s).
    *
    * When using this method, each logic part consumes the whole input defined so far. To provide the server logic
    * in parts, where only part of the input is consumed (but the endpoint cannot be later extended), see the
    * [[serverLogicPart]] function.
    *
    * An example use-case is defining an endpoint with fully-defined errors, and with authorization logic built-in.
    * Such an endpoint can be then extended by multiple other endpoints.
    */
  def serverLogicForCurrent[U, F[_]](_f: I => F[Either[E, U]]): PartialServerEndpoint[U, Unit, E, O, S, F] =
    new PartialServerEndpoint[U, Unit, E, O, S, F](this.copy(input = emptyInput)) {
      override type T = I
      override def tInput: EndpointInput[T] = outer.input
      override def partialLogic: Monad[F] => T => F[Either[E, U]] = _ => _f
    }
}

trait EndpointInputsOps[I, E, O, +S] {
  type EndpointType[_I, _E, _O, +_S]
  def input: EndpointInput[I]
  private[tapir] def withInput[I2, S2 >: S](input: EndpointInput[I2]): EndpointType[I2, E, O, S2]

  def get: EndpointType[I, E, O, S] = method(Method.GET)
  def post: EndpointType[I, E, O, S] = method(Method.POST)
  def head: EndpointType[I, E, O, S] = method(Method.HEAD)
  def put: EndpointType[I, E, O, S] = method(Method.PUT)
  def delete: EndpointType[I, E, O, S] = method(Method.DELETE)
  def options: EndpointType[I, E, O, S] = method(Method.OPTIONS)
  def patch: EndpointType[I, E, O, S] = method(Method.PATCH)
  def connect: EndpointType[I, E, O, S] = method(Method.CONNECT)
  def trace: EndpointType[I, E, O, S] = method(Method.TRACE)
  def method(m: sttp.model.Method): EndpointType[I, E, O, S] = in(FixedMethod(m, Codec.idPlain(), EndpointIO.Info.empty))

  def in[J, IJ](i: EndpointInput[J])(implicit concat: ParamConcat.Aux[I, J, IJ]): EndpointType[IJ, E, O, S] =
    withInput(input.and(i))

  def prependIn[J, JI](i: EndpointInput[J])(implicit concat: ParamConcat.Aux[J, I, JI]): EndpointType[JI, E, O, S] =
    withInput(i.and(input))

  def in[J, IJ, S2 >: S](i: StreamingEndpointIO[J, S2])(implicit concat: ParamConcat.Aux[I, J, IJ]): EndpointType[IJ, E, O, S2] =
    withInput(input.and(i.toEndpointIO))

  def prependIn[J, JI, S2 >: S](i: StreamingEndpointIO[J, S2])(implicit concat: ParamConcat.Aux[J, I, JI]): EndpointType[JI, E, O, S2] =
    withInput(i.toEndpointIO.and(input))

  def mapIn[II](m: Mapping[I, II]): EndpointType[II, E, O, S] =
    withInput(input.map(m))

  def mapIn[II](f: I => II)(g: II => I): EndpointType[II, E, O, S] =
    withInput(input.map(f)(g))

  def mapInDecode[II](f: I => DecodeResult[II])(g: II => I): EndpointType[II, E, O, S] =
    withInput(input.mapDecode(f)(g))

  def mapInTo[COMPANION, CASE_CLASS <: Product](
      c: COMPANION
  )(implicit fc: FnComponents[COMPANION, I, CASE_CLASS]): EndpointType[CASE_CLASS, E, O, S] =
    withInput[CASE_CLASS, S](input = input.mapTo(c)(fc))

  def httpMethod: Option[Method] = {
    import sttp.tapir.internal._
    input.method
  }
}

trait EndpointErrorOutputsOps[I, E, O, +S] {
  type EndpointType[_I, _E, _O, +_S]
  def errorOutput: EndpointOutput[E]
  private[tapir] def withErrorOutput[E2, S2 >: S](input: EndpointOutput[E2]): EndpointType[I, E2, O, S2]

  def errorOut[F, EF](i: EndpointOutput[F])(implicit ts: ParamConcat.Aux[E, F, EF]): EndpointType[I, EF, O, S] =
    withErrorOutput(errorOutput.and(i))

  def prependErrorOut[F, FE](i: EndpointOutput[F])(implicit ts: ParamConcat.Aux[F, E, FE]): EndpointType[I, FE, O, S] =
    withErrorOutput(i.and(errorOutput))

  def mapErrorOut[EE](m: Mapping[E, EE]): EndpointType[I, EE, O, S] =
    withErrorOutput(errorOutput.map(m))

  def mapErrorOut[EE](f: E => EE)(g: EE => E): EndpointType[I, EE, O, S] =
    withErrorOutput(errorOutput.map(f)(g))

  def mapErrorOutDecode[EE](f: E => DecodeResult[EE])(g: EE => E): EndpointType[I, EE, O, S] =
    withErrorOutput(errorOutput.mapDecode(f)(g))

  def mapErrorOutTo[COMPANION, CASE_CLASS <: Product](
      c: COMPANION
  )(implicit fc: FnComponents[COMPANION, E, CASE_CLASS]): EndpointType[I, CASE_CLASS, O, S] =
    withErrorOutput(errorOutput.mapTo(c)(fc))
}

trait EndpointOutputsOps[I, E, O, +S] {
  type EndpointType[_I, _E, _O, +_S]
  def output: EndpointOutput[O]
  private[tapir] def withOutput[O2, S2 >: S](input: EndpointOutput[O2]): EndpointType[I, E, O2, S2]

  def out[P, OP](i: EndpointOutput[P])(implicit ts: ParamConcat.Aux[O, P, OP]): EndpointType[I, E, OP, S] =
    withOutput(output.and(i))

  def prependOut[P, PO](i: EndpointOutput[P])(implicit ts: ParamConcat.Aux[P, O, PO]): EndpointType[I, E, PO, S] =
    withOutput(i.and(output))

  def out[P, OP, S2 >: S](i: StreamingEndpointIO[P, S2])(implicit ts: ParamConcat.Aux[O, P, OP]): EndpointType[I, E, OP, S2] =
    withOutput(output.and(i.toEndpointIO))

  def mapOut[OO](m: Mapping[O, OO]): EndpointType[I, E, OO, S] =
    withOutput(output.map(m))

  def mapOut[OO](f: O => OO)(g: OO => O): EndpointType[I, E, OO, S] =
    withOutput(output.map(f)(g))

  def mapOutDecode[OO](f: O => DecodeResult[OO])(g: OO => O): EndpointType[I, E, OO, S] =
    withOutput(output.mapDecode(f)(g))

  def mapOutTo[COMPANION, CASE_CLASS <: Product](
      c: COMPANION
  )(implicit fc: FnComponents[COMPANION, O, CASE_CLASS]): EndpointType[I, E, CASE_CLASS, S] =
    withOutput(output.mapTo(c)(fc))
}

trait EndpointInfoOps[I, E, O, +S] {
  type EndpointType[_I, _E, _O, +_S]
  def info: EndpointInfo
  private[tapir] def withInfo(info: EndpointInfo): EndpointType[I, E, O, S]

  def name(n: String): EndpointType[I, E, O, S] = withInfo(info.name(n))
  def summary(s: String): EndpointType[I, E, O, S] = withInfo(info.summary(s))
  def description(d: String): EndpointType[I, E, O, S] = withInfo(info.description(d))
  def tags(ts: List[String]): EndpointType[I, E, O, S] = withInfo(info.tags(ts))
  def tag(t: String): EndpointType[I, E, O, S] = withInfo(info.tag(t))
  def deprecated(): EndpointType[I, E, O, S] = withInfo(info.deprecated(true))

  def info(i: EndpointInfo): EndpointType[I, E, O, S] = withInfo(i)
}

trait EndpointMetaOps[I, E, O, +S] {
  type EndpointType[_I, _E, _O, +_S]
  def input: EndpointInput[I]
  def errorOutput: EndpointOutput[E]
  def output: EndpointOutput[O]
  def info: EndpointInfo

  /**
    * Basic information about the endpoint, excluding mapping information, with inputs sorted (first the method, then
    * path, etc.)
    */
  def show: String = {
    def showOutputs(o: EndpointOutput[_]): String = {
      val basicOutputsMap = o.asBasicOutputsMap

      basicOutputsMap.get(None) match {
        case Some(defaultOutputs) if basicOutputsMap.size == 1 =>
          showMultiple(defaultOutputs.sortByType)
        case _ =>
          val mappings = basicOutputsMap.map {
            case (_, os) => showMultiple(os)
          }
          showOneOf(mappings.toSeq)
      }
    }

    val namePrefix = info.name.map("[" + _ + "] ").getOrElse("")
    val showInputs = showMultiple((input.asVectorOfBasicInputs() ++ additionalInputsForShow).sortBy(basicInputSortIndex))
    val showSuccessOutputs = showOutputs(output)
    val showErrorOutputs = showOutputs(errorOutput)

    s"$namePrefix$showInputs -> $showErrorOutputs/$showSuccessOutputs"
  }
  protected def additionalInputsForShow: Vector[EndpointInput.Basic[_]] = Vector.empty

  /**
    * Detailed description of the endpoint, with inputs/outputs represented in the same order as originally defined,
    * including mapping information.
    */
  def showDetail: String =
    s"$showType${info.name.map("[" + _ + "]").getOrElse("")}(in: ${input.show}, errout: ${errorOutput.show}, out: ${output.show})"
  protected def showType: String

  /**
    * Equivalent to `.toString`, shows the whole case class structure.
    */
  def showRaw: String = toString

  /**
    * Renders endpoint path, by default all parametrised path and query components are replaced by {param_name} or
    * {paramN}, e.g. for
    * {{{
    * endpoint.in("p1" / path[String] / query[String]("par2"))
    * }}}
    * returns `/p1/{param1}?par2={par2}`
    *
    * @param includeAuth Should authentication inputs be included in the result.
    */
  def renderPathTemplate(
      renderPathParam: RenderPathParam = RenderPathTemplate.Defaults.path,
      renderQueryParam: Option[RenderQueryParam] = Some(RenderPathTemplate.Defaults.query),
      includeAuth: Boolean = true
  ): String = RenderPathTemplate(this)(renderPathParam, renderQueryParam, includeAuth)
}

case class EndpointInfo(
    name: Option[String],
    summary: Option[String],
    description: Option[String],
    tags: Vector[String],
    deprecated: Boolean
) {
  def name(n: String): EndpointInfo = this.copy(name = Some(n))
  def summary(s: String): EndpointInfo = copy(summary = Some(s))
  def description(d: String): EndpointInfo = copy(description = Some(d))
  def tags(ts: List[String]): EndpointInfo = copy(tags = tags ++ ts)
  def tag(t: String): EndpointInfo = copy(tags = tags :+ t)
  def deprecated(d: Boolean): EndpointInfo = copy(deprecated = d)
}
