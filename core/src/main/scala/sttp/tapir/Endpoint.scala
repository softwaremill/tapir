package sttp.tapir

import sttp.capabilities.WebSockets
import sttp.model.Method
import sttp.monad.MonadError
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.EndpointInput.FixedMethod
import sttp.tapir.RenderPathTemplate.{RenderPathParam, RenderQueryParam}
import sttp.tapir.internal._
import sttp.tapir.macros.{EndpointErrorOutputsMacros, EndpointInputsMacros, EndpointOutputsMacros}
import sttp.tapir.server.{PartialServerEndpoint, ServerEndpoint, ServerEndpointInParts}
import sttp.tapir.typelevel.{ParamConcat, ParamSubtract}

import scala.collection.immutable.Nil
import scala.reflect.ClassTag

/** @tparam I Input parameter types.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  * @tparam R The capabilities that are required by this endpoint's inputs/outputs. This might be `Any` (no
  *           requirements), [[sttp.capabilities.Effect]] (the interpreter must support the given effect type),
  *           [[sttp.capabilities.Streams]] (the ability to send and receive streaming bodies) or
  *           [[sttp.capabilities.WebSockets]] (the ability to handle websocket requests).
  */
case class Endpoint[I, E, O, -R](input: EndpointInput[I], errorOutput: EndpointOutput[E], output: EndpointOutput[O], info: EndpointInfo)
    extends EndpointInputsOps[I, E, O, R]
    with EndpointErrorOutputsOps[I, E, O, R]
    with EndpointOutputsOps[I, E, O, R]
    with EndpointInfoOps[I, E, O, R]
    with EndpointMetaOps[I, E, O, R]
    with EndpointServerLogicOps[I, E, O, R] { outer =>

  override type EndpointType[_I, _E, _O, -_R] = Endpoint[_I, _E, _O, _R]
  override private[tapir] def withInput[I2, R2](input: EndpointInput[I2]): Endpoint[I2, E, O, R with R2] = this.copy(input = input)
  override private[tapir] def withErrorOutput[E2, R2](errorOutput: EndpointOutput[E2]): Endpoint[I, E2, O, R with R2] =
    this.copy(errorOutput = errorOutput)
  override private[tapir] def withOutput[O2, R2](output: EndpointOutput[O2]): Endpoint[I, E, O2, R with R2] = this.copy(output = output)
  override private[tapir] def withInfo(info: EndpointInfo): Endpoint[I, E, O, R] = this.copy(info = info)
  override protected def showType: String = "Endpoint"
}

trait EndpointInputsOps[I, E, O, -R] extends EndpointInputsMacros[I, E, O, R] {
  type EndpointType[_I, _E, _O, -_R]
  def input: EndpointInput[I]
  private[tapir] def withInput[I2, R2](input: EndpointInput[I2]): EndpointType[I2, E, O, R with R2]

  def get: EndpointType[I, E, O, R] = method(Method.GET)
  def post: EndpointType[I, E, O, R] = method(Method.POST)
  def head: EndpointType[I, E, O, R] = method(Method.HEAD)
  def put: EndpointType[I, E, O, R] = method(Method.PUT)
  def delete: EndpointType[I, E, O, R] = method(Method.DELETE)
  def options: EndpointType[I, E, O, R] = method(Method.OPTIONS)
  def patch: EndpointType[I, E, O, R] = method(Method.PATCH)
  def connect: EndpointType[I, E, O, R] = method(Method.CONNECT)
  def trace: EndpointType[I, E, O, R] = method(Method.TRACE)
  def method(m: sttp.model.Method): EndpointType[I, E, O, R] = in(FixedMethod(m, Codec.idPlain(), EndpointIO.Info.empty))

  def in[J, IJ](i: EndpointInput[J])(implicit concat: ParamConcat.Aux[I, J, IJ]): EndpointType[IJ, E, O, R] =
    withInput(input.and(i))

  def prependIn[J, JI](i: EndpointInput[J])(implicit concat: ParamConcat.Aux[J, I, JI]): EndpointType[JI, E, O, R] =
    withInput(i.and(input))

  def in[BS, J, IJ, R2](i: StreamBodyIO[BS, J, R2])(implicit concat: ParamConcat.Aux[I, J, IJ]): EndpointType[IJ, E, O, R with R2] =
    withInput(input.and(i.toEndpointIO))

  def prependIn[BS, J, JI, R2](i: StreamBodyIO[BS, J, R2])(implicit concat: ParamConcat.Aux[J, I, JI]): EndpointType[JI, E, O, R with R2] =
    withInput(i.toEndpointIO.and(input))

  def mapIn[II](m: Mapping[I, II]): EndpointType[II, E, O, R] =
    withInput(input.map(m))

  def mapIn[II](f: I => II)(g: II => I): EndpointType[II, E, O, R] =
    withInput(input.map(f)(g))

  def mapInDecode[II](f: I => DecodeResult[II])(g: II => I): EndpointType[II, E, O, R] =
    withInput(input.mapDecode(f)(g))

  def httpMethod: Option[Method] = {
    import sttp.tapir.internal._
    input.method
  }
}

trait EndpointErrorOutputsOps[I, E, O, -R] extends EndpointErrorOutputsMacros[I, E, O, R] {
  type EndpointType[_I, _E, _O, -_R]
  def errorOutput: EndpointOutput[E]
  private[tapir] def withErrorOutput[E2, R2](input: EndpointOutput[E2]): EndpointType[I, E2, O, R with R2]

  def errorOut[F, EF](i: EndpointOutput[F])(implicit ts: ParamConcat.Aux[E, F, EF]): EndpointType[I, EF, O, R] =
    withErrorOutput(errorOutput.and(i))

  def prependErrorOut[F, FE](i: EndpointOutput[F])(implicit ts: ParamConcat.Aux[F, E, FE]): EndpointType[I, FE, O, R] =
    withErrorOutput(i.and(errorOutput))

  def mapErrorOut[EE](m: Mapping[E, EE]): EndpointType[I, EE, O, R] =
    withErrorOutput(errorOutput.map(m))

  def mapErrorOut[EE](f: E => EE)(g: EE => E): EndpointType[I, EE, O, R] =
    withErrorOutput(errorOutput.map(f)(g))

  def mapErrorOutDecode[EE](f: E => DecodeResult[EE])(g: EE => E): EndpointType[I, EE, O, R] =
    withErrorOutput(errorOutput.mapDecode(f)(g))
}

trait EndpointOutputsOps[I, E, O, -R] extends EndpointOutputsMacros[I, E, O, R] {
  type EndpointType[_I, _E, _O, -_R]
  def output: EndpointOutput[O]
  private[tapir] def withOutput[O2, R2](input: EndpointOutput[O2]): EndpointType[I, E, O2, R with R2]

  def out[P, OP](i: EndpointOutput[P])(implicit ts: ParamConcat.Aux[O, P, OP]): EndpointType[I, E, OP, R] =
    withOutput(validated(output.and(i)))

  def prependOut[P, PO](i: EndpointOutput[P])(implicit ts: ParamConcat.Aux[P, O, PO]): EndpointType[I, E, PO, R] =
    withOutput(validated(i.and(output)))

  def out[BS, P, OP, R2](i: StreamBodyIO[BS, P, R2])(implicit ts: ParamConcat.Aux[O, P, OP]): EndpointType[I, E, OP, R with R2] =
    withOutput(validated(output.and(i.toEndpointIO)))

  def prependOut[BS, P, PO, R2](i: StreamBodyIO[BS, P, R2])(implicit ts: ParamConcat.Aux[P, O, PO]): EndpointType[I, E, PO, R] =
    withOutput(validated(i.toEndpointIO.and(output)))

  def out[PIPE_REQ_RESP, P, OP, R2](i: WebSocketBodyOutput[PIPE_REQ_RESP, _, _, P, R2])(implicit
      ts: ParamConcat.Aux[O, P, OP]
  ): EndpointType[I, E, OP, R with R2 with WebSockets] = withOutput(validated(output.and(i.toEndpointOutput)))

  def prependOut[PIPE_REQ_RESP, P, PO, R2](i: WebSocketBodyOutput[PIPE_REQ_RESP, _, _, P, R2])(implicit
      ts: ParamConcat.Aux[P, O, PO]
  ): EndpointType[I, E, PO, R with R2 with WebSockets] = withOutput(validated(i.toEndpointOutput.and(output)))

  def mapOut[OO](m: Mapping[O, OO]): EndpointType[I, E, OO, R] =
    withOutput(output.map(m))

  def mapOut[OO](f: O => OO)(g: OO => O): EndpointType[I, E, OO, R] =
    withOutput(output.map(f)(g))

  def mapOutDecode[OO](f: O => DecodeResult[OO])(g: OO => O): EndpointType[I, E, OO, R] =
    withOutput(output.mapDecode(f)(g))

  private def validated[OP](output: EndpointOutput[OP]): EndpointOutput[OP] = {

    def isBody(o: EndpointOutput.Basic[_]): Boolean = o match {
      case _ @(EndpointIO.Body(_, _, _) | EndpointIO.StreamBodyWrapper(_)) => true
      case _                                                               => false
    }

    output.asBasicOutputsList
      .map { case (status, outputs) => status -> outputs.filter(isBody) }
      .groupBy { case (status, _) => status }
      .map { case (status, outputs) =>
        val formats = outputs.flatMap { case (_, output) =>
          output.collect {
            case EndpointIO.Body(bodyType, codec, _) =>
              codec.format.mediaType.copy(charset = charset(bodyType).map(_.name()))
            case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, charset)) =>
              codec.format.mediaType.copy(charset = charset.map(_.name()))
          }
        }
        val duplicates = formats.diff(formats.distinct)
        if (duplicates.nonEmpty) {
          Left(
            s"Ambiguous mapping of status ${status.map(_.toString).getOrElse("default status")} to format ${duplicates.mkString(", ")}"
          )
        } else Right(())
      }
      .partition(_.isLeft) match {
      case (Nil, _)    => output
      case (errors, _) => throw new RuntimeException((errors collect { case Left(e) => e }).mkString("\n"))
    }
  }
}

trait EndpointInfoOps[I, E, O, -R] {
  type EndpointType[_I, _E, _O, -_R]
  def info: EndpointInfo
  private[tapir] def withInfo(info: EndpointInfo): EndpointType[I, E, O, R]

  def name(n: String): EndpointType[I, E, O, R] = withInfo(info.name(n))
  def summary(s: String): EndpointType[I, E, O, R] = withInfo(info.summary(s))
  def description(d: String): EndpointType[I, E, O, R] = withInfo(info.description(d))
  def tags(ts: List[String]): EndpointType[I, E, O, R] = withInfo(info.tags(ts))
  def tag(t: String): EndpointType[I, E, O, R] = withInfo(info.tag(t))
  def deprecated(): EndpointType[I, E, O, R] = withInfo(info.deprecated(true))
  def docsExtension[A: JsonCodec](key: String, value: A): EndpointType[I, E, O, R] = withInfo(info.docsExtension(key, value))

  def info(i: EndpointInfo): EndpointType[I, E, O, R] = withInfo(i)
}

trait EndpointMetaOps[I, E, O, -R] {
  type EndpointType[_I, _E, _O, -_R]
  def input: EndpointInput[I]
  def errorOutput: EndpointOutput[E]
  def output: EndpointOutput[O]
  def info: EndpointInfo

  /** Basic information about the endpoint, excluding mapping information, with inputs sorted (first the method, then
    * path, etc.)
    */
  def show: String = {
    def showOutputs(o: EndpointOutput[_]): String = {
      val basicOutputsMap = o.asBasicOutputsList

      basicOutputsMap match {
        case (None, defaultOutputs) :: Nil =>
          showMultiple(defaultOutputs.sortByType)
        case list =>
          val mappings = list.map { case (_, os) =>
            showMultiple(os)
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

  /** Detailed description of the endpoint, with inputs/outputs represented in the same order as originally defined,
    * including mapping information.
    */
  def showDetail: String =
    s"$showType${info.name.map("[" + _ + "]").getOrElse("")}(in: ${input.show}, errout: ${errorOutput.show}, out: ${output.show})"
  protected def showType: String

  /** Equivalent to `.toString`, shows the whole case class structure.
    */
  def showRaw: String = toString

  /** Renders endpoint path, by default all parametrised path and query components are replaced by {param_name} or
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

trait EndpointServerLogicOps[I, E, O, -R] { outer: Endpoint[I, E, O, R] =>

  /** Combine this endpoint description with a function, which implements the server-side logic. The logic returns
    * a result, which is either an error or a successful output, wrapped in an effect type `F`.
    *
    * A server endpoint can be passed to a server interpreter. Each server interpreter supports effects of a specific
    * type(s).
    *
    * Both the endpoint and logic function are considered complete, and cannot be later extended through the
    * returned [[ServerEndpoint]] value (except for endpoint meta-data). To provide the logic in parts, see
    * [[serverLogicPart]] and [[serverLogicForCurrent]].
    */
  def serverLogic[F[_]](f: I => F[Either[E, O]]): ServerEndpoint[I, E, O, R, F] = ServerEndpoint(this, _ => f)

  /** Same as [[serverLogic]], but accepts a function that can't fail, and converts to an `Either`.
    */
  def serverLogicSuccess[F[_]](f: I => F[O]): ServerEndpoint[I, E, O, R, F] =
    ServerEndpoint(this, me => in => me.map(f(in))(Right[E, O]))

  /** Same as [[serverLogic]], but requires `E` to be a throwable, and coverts failed effects of type `E` to endpoint
    * errors.
    */
  def serverLogicRecoverErrors[F[_]](
      f: I => F[O]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): ServerEndpoint[I, E, O, R, F] =
    ServerEndpoint(this, recoverErrors[I, E, O, F](f))

  /** Combine this endpoint description with a function, which implements a part of the server-side logic. The
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
    * the returned [[ServerEndpointInParts]] value. However, each part of the server logic can consume only a part
    * of the input. To provide the logic in parts, while still being able to extend the endpoint description, see
    * [[serverLogicForCurrent]].
    *
    * An example use-case is providing authorization logic, followed by server logic (using an authorized user), given
    * a complete endpoint description.
    *
    * Note that the type of the `f` partial server logic function cannot be inferred, it must be explicitly given
    * (e.g. by providing a function or method value).
    */
  def serverLogicPart[T, IR, U, F[_]](
      f: T => F[Either[E, U]]
  )(implicit iMinusT: ParamSubtract.Aux[I, T, IR]): ServerEndpointInParts[U, IR, I, E, O, R, F] = {
    type _T = T
    new ServerEndpointInParts[U, IR, I, E, O, R, F](this) {
      override type T = _T
      override def splitInput: I => (T, IR) = i => split(i)(iMinusT)
      override def logicFragment: MonadError[F] => _T => F[Either[E, U]] = _ => f
    }
  }

  /** Same as [[serverLogicPart]], but requires `E` to be a throwable, and coverts failed effects of type `E` to
    * endpoint errors.
    */
  def serverLogicPartRecoverErrors[T, IR, U, F[_]](
      f: T => F[U]
  )(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E],
      iMinusR: ParamSubtract.Aux[I, T, IR]
  ): ServerEndpointInParts[U, IR, I, E, O, R, F] = {
    type _T = T
    new ServerEndpointInParts[U, IR, I, E, O, R, F](this) {
      override type T = _T
      override def splitInput: I => (T, IR) = i => split(i)(iMinusR)
      override def logicFragment: MonadError[F] => _T => F[Either[E, U]] = recoverErrors[_T, E, U, F](f)
    }
  }

  /** Combine this endpoint description with a function, which implements a part of the server-side logic, for the
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
  def serverLogicForCurrent[U, F[_]](f: I => F[Either[E, U]]): PartialServerEndpoint[I, U, Unit, E, O, R, F] =
    new PartialServerEndpoint[I, U, Unit, E, O, R, F](this.copy(input = emptyInput)) {
      override def tInput: EndpointInput[I] = outer.input
      override def partialLogic: MonadError[F] => I => F[Either[E, U]] = _ => f
    }

  /** Same as [[serverLogicForCurrent]], but requires `E` to be a throwable, and coverts failed effects of type `E` to
    * endpoint errors.
    */
  def serverLogicForCurrentRecoverErrors[U, F[_]](
      f: I => F[U]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): PartialServerEndpoint[I, U, Unit, E, O, R, F] =
    new PartialServerEndpoint[I, U, Unit, E, O, R, F](this.copy(input = emptyInput)) {
      override def tInput: EndpointInput[I] = outer.input
      override def partialLogic: MonadError[F] => I => F[Either[E, U]] = recoverErrors(f)
    }
}

case class EndpointInfo(
    name: Option[String],
    summary: Option[String],
    description: Option[String],
    tags: Vector[String],
    deprecated: Boolean,
    docsExtensions: Vector[DocsExtension[_]]
) {
  def name(n: String): EndpointInfo = this.copy(name = Some(n))
  def summary(s: String): EndpointInfo = copy(summary = Some(s))
  def description(d: String): EndpointInfo = copy(description = Some(d))
  def tags(ts: List[String]): EndpointInfo = copy(tags = tags ++ ts)
  def tag(t: String): EndpointInfo = copy(tags = tags :+ t)
  def deprecated(d: Boolean): EndpointInfo = copy(deprecated = d)
  def docsExtension[A: JsonCodec](key: String, value: A): EndpointInfo =
    copy(docsExtensions = docsExtensions :+ DocsExtension.of(key, value))
}
