package sttp.tapir

import sttp.capabilities.WebSockets
import sttp.model.Method
import sttp.monad.syntax._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.EndpointInput.FixedMethod
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir.RenderPathTemplate.{RenderPathParam, RenderQueryParam}
import sttp.tapir.internal._
import sttp.tapir.macros.{EndpointErrorOutputsMacros, EndpointInputsMacros, EndpointOutputsMacros, EndpointSecurityInputsMacros}
import sttp.tapir.server.{PartialServerEndpoint, ServerEndpoint}
import sttp.tapir.typelevel.{ErasureSameAsType, ParamConcat}

import scala.reflect.ClassTag

/** A description of an endpoint with the given inputs & outputs. The inputs are divided into two parts: security (`A`) and regular inputs
  * (`I`). There are also two kinds of outputs: error outputs (`E`) and regular outputs (`O`).
  *
  * In case there are no security inputs, the [[PublicEndpoint]] alias can be used, which omits the `A` parameter type.
  *
  * An endpoint can be interpreted as a server, client or documentation. The endpoint requires that server/client interpreters meet the
  * capabilities specified by `R` (if any).
  *
  * When interpreting an endpoint as a server, the inputs are decoded and the security logic is run first, before decoding the body in the
  * regular inputs. This allows short-circuiting further processing in case security checks fail. Server logic can be provided using
  * [[EndpointServerLogicOps.serverSecurityLogic]] variants for secure endpoints, and [[EndpointServerLogicOps.serverLogic]] variants for
  * public endpoints.
  *
  * A concise description of an endpoint can be generated using the [[EndpointMetaOps.show]] method.
  *
  * @tparam A
  *   Security input parameter types.
  * @tparam I
  *   Input parameter types.
  * @tparam E
  *   Error output parameter types.
  * @tparam O
  *   Output parameter types.
  * @tparam R
  *   The capabilities that are required by this endpoint's inputs/outputs. This might be `Any` (no requirements),
  *   [[sttp.capabilities.Effect]] (the interpreter must support the given effect type), [[sttp.capabilities.Streams]] (the ability to send
  *   and receive streaming bodies) or [[sttp.capabilities.WebSockets]] (the ability to handle websocket requests).
  */
case class Endpoint[A, I, E, O, -R](
    securityInput: EndpointInput[A],
    input: EndpointInput[I],
    errorOutput: EndpointOutput[E],
    output: EndpointOutput[O],
    info: EndpointInfo
) extends EndpointSecurityInputsOps[A, I, E, O, R]
    with EndpointInputsOps[A, I, E, O, R]
    with EndpointErrorOutputsOps[A, I, E, O, R]
    with EndpointErrorOutputVariantsOps[A, I, E, O, R]
    with EndpointOutputsOps[A, I, E, O, R]
    with EndpointInfoOps[R]
    with EndpointMetaOps
    with EndpointServerLogicOps[A, I, E, O, R] { outer =>

  override type EndpointType[_A, _I, _E, _O, -_R] = Endpoint[_A, _I, _E, _O, _R]
  override type ThisType[-_R] = Endpoint[A, I, E, O, _R]
  override private[tapir] def withSecurityInput[A2, R2](securityInput: EndpointInput[A2]): Endpoint[A2, I, E, O, R with R2] =
    this.copy(securityInput = securityInput)
  override private[tapir] def withInput[I2, R2](input: EndpointInput[I2]): Endpoint[A, I2, E, O, R with R2] = this.copy(input = input)
  override private[tapir] def withErrorOutput[E2, R2](errorOutput: EndpointOutput[E2]): Endpoint[A, I, E2, O, R with R2] =
    this.copy(errorOutput = errorOutput)
  override private[tapir] def withErrorOutputVariant[E2, R2](
      errorOutput: EndpointOutput[E2],
      embedE: E => E2
  ): Endpoint[A, I, E2, O, R with R2] =
    this.copy(errorOutput = errorOutput)
  override private[tapir] def withOutput[O2, R2](output: EndpointOutput[O2]): Endpoint[A, I, E, O2, R with R2] = this.copy(output = output)
  override private[tapir] def withInfo(info: EndpointInfo): Endpoint[A, I, E, O, R] = this.copy(info = info)
  override protected def showType: String = "Endpoint"
}

trait EndpointSecurityInputsOps[A, I, E, O, -R] extends EndpointSecurityInputsMacros[A, I, E, O, R] {
  type EndpointType[_A, _I, _E, _O, -_R]
  def securityInput: EndpointInput[A]
  private[tapir] def withSecurityInput[A2, R2](securityInput: EndpointInput[A2]): EndpointType[A2, I, E, O, R with R2]

  def securityIn[B, AB](i: EndpointInput[B])(implicit concat: ParamConcat.Aux[A, B, AB]): EndpointType[AB, I, E, O, R] =
    withSecurityInput(securityInput.and(i))

  def prependSecurityIn[B, BA](i: EndpointInput[B])(implicit concat: ParamConcat.Aux[B, A, BA]): EndpointType[BA, I, E, O, R] =
    withSecurityInput(i.and(securityInput))

  def mapSecurityIn[AA](m: Mapping[A, AA]): EndpointType[AA, I, E, O, R] =
    withSecurityInput(securityInput.map(m))

  def mapSecurityIn[AA](f: A => AA)(g: AA => A): EndpointType[AA, I, E, O, R] =
    withSecurityInput(securityInput.map(f)(g))

  def mapSecurityInDecode[AA](f: A => DecodeResult[AA])(g: AA => A): EndpointType[AA, I, E, O, R] =
    withSecurityInput(securityInput.mapDecode(f)(g))
}

trait EndpointInputsOps[A, I, E, O, -R] extends EndpointInputsMacros[A, I, E, O, R] {
  type EndpointType[_A, _I, _E, _O, -_R]
  def input: EndpointInput[I]
  private[tapir] def withInput[I2, R2](input: EndpointInput[I2]): EndpointType[A, I2, E, O, R with R2]

  def get: EndpointType[A, I, E, O, R] = method(Method.GET)
  def post: EndpointType[A, I, E, O, R] = method(Method.POST)
  def head: EndpointType[A, I, E, O, R] = method(Method.HEAD)
  def put: EndpointType[A, I, E, O, R] = method(Method.PUT)
  def delete: EndpointType[A, I, E, O, R] = method(Method.DELETE)
  def options: EndpointType[A, I, E, O, R] = method(Method.OPTIONS)
  def patch: EndpointType[A, I, E, O, R] = method(Method.PATCH)
  def connect: EndpointType[A, I, E, O, R] = method(Method.CONNECT)
  def trace: EndpointType[A, I, E, O, R] = method(Method.TRACE)
  def method(m: sttp.model.Method): EndpointType[A, I, E, O, R] = in(FixedMethod(m, Codec.idPlain(), EndpointIO.Info.empty))

  def in[J, IJ](i: EndpointInput[J])(implicit concat: ParamConcat.Aux[I, J, IJ]): EndpointType[A, IJ, E, O, R] =
    withInput(input.and(i))

  def prependIn[J, JI](i: EndpointInput[J])(implicit concat: ParamConcat.Aux[J, I, JI]): EndpointType[A, JI, E, O, R] =
    withInput(i.and(input))

  def in[BS, J, IJ, R2](i: StreamBodyIO[BS, J, R2])(implicit concat: ParamConcat.Aux[I, J, IJ]): EndpointType[A, IJ, E, O, R with R2] =
    withInput(input.and(i.toEndpointIO))

  def prependIn[BS, J, JI, R2](i: StreamBodyIO[BS, J, R2])(implicit
      concat: ParamConcat.Aux[J, I, JI]
  ): EndpointType[A, JI, E, O, R with R2] =
    withInput(i.toEndpointIO.and(input))

  def mapIn[II](m: Mapping[I, II]): EndpointType[A, II, E, O, R] =
    withInput(input.map(m))

  def mapIn[II](f: I => II)(g: II => I): EndpointType[A, II, E, O, R] =
    withInput(input.map(f)(g))

  def mapInDecode[II](f: I => DecodeResult[II])(g: II => I): EndpointType[A, II, E, O, R] =
    withInput(input.mapDecode(f)(g))
}

trait EndpointErrorOutputsOps[A, I, E, O, -R] extends EndpointErrorOutputsMacros[A, I, E, O, R] {
  type EndpointType[_A, _I, _E, _O, -_R]
  def errorOutput: EndpointOutput[E]
  private[tapir] def withErrorOutput[E2, R2](output: EndpointOutput[E2]): EndpointType[A, I, E2, O, R with R2]

  def errorOut[F, EF](o: EndpointOutput[F])(implicit ts: ParamConcat.Aux[E, F, EF]): EndpointType[A, I, EF, O, R] =
    withErrorOutput(errorOutput.and(o))

  def prependErrorOut[F, FE](o: EndpointOutput[F])(implicit ts: ParamConcat.Aux[F, E, FE]): EndpointType[A, I, FE, O, R] =
    withErrorOutput(o.and(errorOutput))

  def mapErrorOut[EE](m: Mapping[E, EE]): EndpointType[A, I, EE, O, R] =
    withErrorOutput(errorOutput.map(m))

  def mapErrorOut[EE](f: E => EE)(g: EE => E): EndpointType[A, I, EE, O, R] =
    withErrorOutput(errorOutput.map(f)(g))

  def mapErrorOutDecode[EE](f: E => DecodeResult[EE])(g: EE => E): EndpointType[A, I, EE, O, R] =
    withErrorOutput(errorOutput.mapDecode(f)(g))
}

trait EndpointErrorOutputVariantsOps[A, I, E, O, -R] {
  type EndpointType[_A, _I, _E, _O, -_R]
  def errorOutput: EndpointOutput[E]
  private[tapir] def withErrorOutputVariant[E2, R2](output: EndpointOutput[E2], embedE: E => E2): EndpointType[A, I, E2, O, R with R2]

  /** Appends a new error output variant.
    *
    * A variant for the current endpoint output will be created using the given [[Tapir.oneOfVariant]]. This is needed to capture the logic
    * which allows deciding if a run-time value is applicable to a variant. If the erasure of the `E` type is different from `E`, there will
    * be a compile-time failure, as no such run-time check is possible. In this case, use [[errorOutVariantsFromCurrent]] and create a
    * variant using one of the other variant factory methods (e.g. [[Tapir.oneOfVariantValueMatcher]]).
    *
    * During encoding/decoding, the new `o` variant will be checked after the current variant.
    *
    * More specifically, the current error output is replaced with a [[Tapir.oneOf]] output, where:
    *   - the first output variant is the current variant: `oneOfVariant(errorOutput)`
    *   - the second output variant is the given `o`
    *
    * Usage example:
    *
    * {{{
    *   sealed trait Parent
    *   case class Child1(v: String) extends Parent
    *   case class Child2(v: String) extends Parent
    *
    *   val e: PublicEndpoint[Unit, Parent, Unit, Any] = endpoint
    *     .errorOut(stringBody.mapTo[Child1])
    *     .errorOutVariant[Parent](oneOfVariant(stringBody.mapTo[Child2]))
    * }}}
    *
    * Adding error output variants is useful when extending the error outputs in a [[PartialServerEndpoint]], created using
    * [[EndpointServerLogicOps.serverSecurityLogic]].
    *
    * @param o
    *   The variant to add. Can be created given an output with one of the [[Tapir.oneOfVariant]] methods.
    * @tparam E2
    *   A common supertype of the new variant and the current output `E`.
    */
  def errorOutVariant[E2 >: E](
      o: OneOfVariant[_ <: E2]
  )(implicit ct: ClassTag[E], eEqualToErasure: ErasureSameAsType[E]): EndpointType[A, I, E2, O, R] =
    withErrorOutputVariant(oneOf[E2](oneOfVariant[E](errorOutput), o), identity)

  /** Same as [[errorOutVariant]], but allows appending multiple variants in one go. */
  def errorOutVariants[E2 >: E](first: OneOfVariant[_ <: E2], other: OneOfVariant[_ <: E2]*)(implicit
      ct: ClassTag[E],
      eEqualToErasure: ErasureSameAsType[E]
  ): EndpointType[A, I, E2, O, R] =
    withErrorOutputVariant(oneOf[E2](oneOfVariant[E](errorOutput), first +: other: _*), identity)

  /** Replace the error output with a [[Tapir.oneOf]] output, using the variants returned by `variants`. The current output should be
    * included in one of the returned variants.
    *
    * Allows creating the variant list in a custom order, placing the current variant in an arbitrary position, and using default variants
    * if necessary.
    *
    * Adding error output variants is useful when extending the error outputs in a [[PartialServerEndpoint]], created using
    * [[EndpointServerLogicOps.serverSecurityLogic]].
    *
    * @tparam E2
    *   A common supertype of the new variant and the current output `E`.
    */
  def errorOutVariantsFromCurrent[E2 >: E](variants: EndpointOutput[E] => List[OneOfVariant[_ <: E2]]): EndpointType[A, I, E2, O, R] =
    withErrorOutputVariant(EndpointOutput.OneOf[E2, E2](variants(errorOutput), Mapping.id), identity)

  /** Adds a new error variant, where the current error output is represented as a `Left`, and the given one as a `Right`. */
  def errorOutEither[E2](o: EndpointOutput[E2]): EndpointType[A, I, Either[E, E2], O, R] =
    withErrorOutputVariant(
      oneOf(
        oneOfVariantValueMatcher(o.map(Right(_))(_.value)) { case Right(_) =>
          true
        },
        oneOfVariantValueMatcher(errorOutput.map(Left(_))(_.value)) { case Left(_) =>
          true
        }
      ),
      Left(_)
    )
}

trait EndpointOutputsOps[A, I, E, O, -R] extends EndpointOutputsMacros[A, I, E, O, R] {
  type EndpointType[_A, _I, _E, _O, -_R]
  def output: EndpointOutput[O]
  private[tapir] def withOutput[O2, R2](input: EndpointOutput[O2]): EndpointType[A, I, E, O2, R with R2]

  def out[P, OP](i: EndpointOutput[P])(implicit ts: ParamConcat.Aux[O, P, OP]): EndpointType[A, I, E, OP, R] =
    withOutput(output.and(i))

  def prependOut[P, PO](i: EndpointOutput[P])(implicit ts: ParamConcat.Aux[P, O, PO]): EndpointType[A, I, E, PO, R] =
    withOutput(i.and(output))

  def out[BS, P, OP, R2](i: StreamBodyIO[BS, P, R2])(implicit ts: ParamConcat.Aux[O, P, OP]): EndpointType[A, I, E, OP, R with R2] =
    withOutput(output.and(i.toEndpointIO))

  def prependOut[BS, P, PO, R2](i: StreamBodyIO[BS, P, R2])(implicit ts: ParamConcat.Aux[P, O, PO]): EndpointType[A, I, E, PO, R] =
    withOutput(i.toEndpointIO.and(output))

  def out[PIPE_REQ_RESP, P, OP, R2](i: WebSocketBodyOutput[PIPE_REQ_RESP, _, _, P, R2])(implicit
      ts: ParamConcat.Aux[O, P, OP]
  ): EndpointType[A, I, E, OP, R with R2 with WebSockets] = withOutput(output.and(i.toEndpointOutput))

  def prependOut[PIPE_REQ_RESP, P, PO, R2](i: WebSocketBodyOutput[PIPE_REQ_RESP, _, _, P, R2])(implicit
      ts: ParamConcat.Aux[P, O, PO]
  ): EndpointType[A, I, E, PO, R with R2 with WebSockets] = withOutput(i.toEndpointOutput.and(output))

  def mapOut[OO](m: Mapping[O, OO]): EndpointType[A, I, E, OO, R] =
    withOutput(output.map(m))

  def mapOut[OO](f: O => OO)(g: OO => O): EndpointType[A, I, E, OO, R] =
    withOutput(output.map(f)(g))

  def mapOutDecode[OO](f: O => DecodeResult[OO])(g: OO => O): EndpointType[A, I, E, OO, R] =
    withOutput(output.mapDecode(f)(g))
}

trait EndpointInfoOps[-R] {
  type ThisType[-_R]
  def info: EndpointInfo
  private[tapir] def withInfo(info: EndpointInfo): ThisType[R]

  def name(n: String): ThisType[R] = withInfo(info.name(n))
  def summary(s: String): ThisType[R] = withInfo(info.summary(s))
  def description(d: String): ThisType[R] = withInfo(info.description(d))
  def tags(ts: List[String]): ThisType[R] = withInfo(info.tags(ts))
  def tag(t: String): ThisType[R] = withInfo(info.tag(t))
  def deprecated(): ThisType[R] = withInfo(info.deprecated(true))
  def docsExtension[D: JsonCodec](key: String, value: D): ThisType[R] = withInfo(info.docsExtension(key, value))

  def info(i: EndpointInfo): ThisType[R] = withInfo(i)
}

trait EndpointMetaOps {
  def securityInput: EndpointInput[_]
  def input: EndpointInput[_]
  def errorOutput: EndpointOutput[_]
  def output: EndpointOutput[_]
  def info: EndpointInfo

  /** Basic information about the endpoint, excluding mapping information, with inputs sorted (first the method, then path, etc.) */
  def show: String = {
    def showOutputs(o: EndpointOutput[_]): String = showOneOf(o.asBasicOutputsList.map(os => showMultiple(os.sortByType)))

    val namePrefix = info.name.map("[" + _ + "] ").getOrElse("")
    val showInputs = showMultiple(
      (securityInput.asVectorOfBasicInputs() ++ input.asVectorOfBasicInputs()).sortBy(basicInputSortIndex)
    )
    val showSuccessOutputs = showOutputs(output)
    val showErrorOutputs = showOutputs(errorOutput)

    s"$namePrefix$showInputs -> $showErrorOutputs/$showSuccessOutputs"
  }

  /** Detailed description of the endpoint, with inputs/outputs represented in the same order as originally defined, including mapping
    * information.
    */
  def showDetail: String =
    s"$showType${info.name.map("[" + _ + "]").getOrElse("")}(securityin: ${securityInput.show}, in: ${input.show}, errout: ${errorOutput.show}, out: ${output.show})"
  protected def showType: String

  /** Equivalent to `.toString`, shows the whole case class structure. */
  def showRaw: String = toString

  /** Renders endpoint path, by default all parametrised path and query components are replaced by {param_name} or {paramN}, e.g. for
    * {{{
    * endpoint.in("p1" / path[String] / query[String]("par2"))
    * }}}
    * returns `/p1/{param1}?par2={par2}`
    *
    * @param includeAuth
    *   Should authentication inputs be included in the result.
    */
  def renderPathTemplate(
      renderPathParam: RenderPathParam = RenderPathTemplate.Defaults.path,
      renderQueryParam: Option[RenderQueryParam] = Some(RenderPathTemplate.Defaults.query),
      includeAuth: Boolean = true
  ): String = RenderPathTemplate(this)(renderPathParam, renderQueryParam, includeAuth)

  /** The method defined in a fixed method input in this endpoint, if any (using e.g. [[EndpointInputsOps.get]] or
    * [[EndpointInputsOps.post]]).
    */
  def method: Option[Method] = {
    import sttp.tapir.internal._
    input.method.orElse(securityInput.method)
  }

  @deprecated("Use method", since = "0.19.0")
  def httpMethod: Option[Method] = method
}

trait EndpointServerLogicOps[A, I, E, O, -R] { outer: Endpoint[A, I, E, O, R] =>

  /** Combine this public endpoint description with a function, which implements the server-side logic. The logic returns a result, which is
    * either an error or a successful output, wrapped in an effect type `F`. For secure endpoints, use [[serverSecurityLogic]].
    *
    * A server endpoint can be passed to a server interpreter. Each server interpreter supports effects of a specific type(s).
    *
    * Both the endpoint and logic function are considered complete, and cannot be later extended through the returned [[ServerEndpoint]]
    * value (except for endpoint meta-data). Secure endpoints allow providing the security logic before all the inputs and outputs are
    * specified.
    */
  def serverLogic[F[_]](f: I => F[Either[E, O]])(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, F] = {
    import sttp.monad.syntax._
    ServerEndpoint.public(this.asInstanceOf[Endpoint[Unit, I, E, O, R]], implicit m => i => f(i).map(x => x))
  }

  /** Like [[serverLogic]], but specialised to the case when the result is always a success (`Right`), hence when the logic type can be
    * simplified to `I => F[O]`.
    */
  def serverLogicSuccess[F[_]](
      f: I => F[O]
  )(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, F] =
    ServerEndpoint.public(this.asInstanceOf[Endpoint[Unit, I, E, O, R]], implicit m => i => f(i).map(Right(_)))

  /** Like [[serverLogic]], but specialised to the case when the result is always an error (`Left`), hence when the logic type can be
    * simplified to `I => F[E]`.
    */
  def serverLogicError[F[_]](
      f: I => F[E]
  )(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, F] =
    ServerEndpoint.public(this.asInstanceOf[Endpoint[Unit, I, E, O, R]], implicit m => i => f(i).map(Left(_)))

  /** Like [[serverLogic]], but specialised to the case when the logic function is pure, that is doesn't have any side effects. */
  def serverLogicPure[F[_]](f: I => Either[E, O])(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, F] =
    ServerEndpoint.public(this.asInstanceOf[Endpoint[Unit, I, E, O, R]], implicit m => i => f(i).unit)

  /** Same as [[serverLogic]], but requires `E` to be a throwable, and coverts failed effects of type `E` to endpoint errors. */
  def serverLogicRecoverErrors[F[_]](
      f: I => F[O]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E], aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, F] =
    ServerEndpoint.public(this.asInstanceOf[Endpoint[Unit, I, E, O, R]], recoverErrors1[I, E, O, F](f))

  //

  /** Combine this endpoint description with a function, which implements the security logic of the endpoint.
    *
    * Subsequently, the endpoint inputs and outputs can be extended (for error outputs, new variants can be added, but they cannot be
    * arbitrarily extended). Then the main server logic can be provided, given a function which accepts as arguments the result of the
    * security logic and the remaining input. The final result is then a [[ServerEndpoint]].
    *
    * A complete server endpoint can be passed to a server interpreter. Each server interpreter supports effects of a specific type(s).
    *
    * An example use-case is defining an endpoint with fully-defined errors, and with security logic built-in. Such an endpoint can be then
    * extended by multiple other endpoints, by specifying different inputs, outputs and the main logic.
    */
  def serverSecurityLogic[U, F[_]](f: A => F[Either[E, U]]): PartialServerEndpoint[A, U, I, E, O, R, F] =
    PartialServerEndpoint(this, _ => f)

  /** Like [[serverSecurityLogic]], but specialised to the case when the result is always a success (`Right`), hence when the logic type can
    * be simplified to `A => F[U]`.
    */
  def serverSecurityLogicSuccess[U, F[_]](
      f: A => F[U]
  ): PartialServerEndpoint[A, U, I, E, O, R, F] =
    PartialServerEndpoint(this, implicit m => a => f(a).map(Right(_)))

  /** Like [[serverSecurityLogic]], but specialised to the case when the result is always an error (`Left`), hence when the logic type can
    * be simplified to `A => F[E]`.
    */
  def serverSecurityLogicError[U, F[_]](
      f: A => F[E]
  ): PartialServerEndpoint[A, U, I, E, O, R, F] =
    PartialServerEndpoint(this, implicit m => a => f(a).map(Left(_)))

  /** Like [[serverSecurityLogic]], but specialised to the case when the logic function is pure, that is doesn't have any side effects. */
  def serverSecurityLogicPure[U, F[_]](f: A => Either[E, U]): PartialServerEndpoint[A, U, I, E, O, R, F] =
    PartialServerEndpoint(this, implicit m => a => f(a).unit)

  /** Same as [[serverSecurityLogic]], but requires `E` to be a throwable, and coverts failed effects of type `E` to endpoint errors. */
  def serverSecurityLogicRecoverErrors[U, F[_]](
      f: A => F[U]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): PartialServerEndpoint[A, U, I, E, O, R, F] =
    PartialServerEndpoint(this, recoverErrors1[A, E, U, F](f))
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
