package sttp.tapir

import sttp.capabilities.WebSockets
import sttp.model.Method
import sttp.monad.IdentityMonad
import sttp.monad.syntax._
import sttp.shared.Identity
import sttp.tapir.EndpointInput.{FixedMethod, PathCapture, Query}
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir.internal._
import sttp.tapir.macros.{EndpointErrorOutputsMacros, EndpointInputsMacros, EndpointOutputsMacros, EndpointSecurityInputsMacros}
import sttp.tapir.server.{
  PartialServerEndpoint,
  PartialServerEndpointSync,
  PartialServerEndpointWithSecurityOutput,
  PartialServerEndpointWithSecurityOutputSync,
  ServerEndpoint
}
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
  * public endpoints; when using a synchronous server, you can also use the more concise [[EndpointServerLogicOps.handle]] methods, which
  * work the save as above, but have the "effect" type fixed to [[Identity]].
  *
  * A concise description of an endpoint can be generated using the [[EndpointMetaOps.show]] method.
  *
  * @tparam SECURITY_INPUT
  *   Security input parameter types, abbreviated as `A`.
  * @tparam INPUT
  *   Input parameter types, abbreviated as `I`.
  * @tparam ERROR_OUTPUT
  *   Error output parameter types, abbreviated as `E`.
  * @tparam OUTPUT
  *   Output parameter types, abbreviated as `O`.
  * @tparam R
  *   The capabilities that are required by this endpoint's inputs/outputs. This might be `Any` (no requirements),
  *   [[sttp.capabilities.Effect]] (the interpreter must support the given effect type), [[sttp.capabilities.Streams]] (the ability to send
  *   and receive streaming bodies) or [[sttp.capabilities.WebSockets]] (the ability to handle websocket requests).
  */
case class Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, -R](
    securityInput: EndpointInput[SECURITY_INPUT],
    input: EndpointInput[INPUT],
    errorOutput: EndpointOutput[ERROR_OUTPUT],
    output: EndpointOutput[OUTPUT],
    info: EndpointInfo
) extends EndpointSecurityInputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointInputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointErrorOutputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointErrorOutputVariantsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointOutputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointInfoOps[R]
    with EndpointMetaOps
    with EndpointServerLogicOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R] { outer =>

  override type EndpointType[_A, _I, _E, _O, -_R] = Endpoint[_A, _I, _E, _O, _R]
  override type ThisType[-_R] = Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, _R]
  override private[tapir] def withSecurityInput[A2, R2](
      securityInput: EndpointInput[A2]
  ): Endpoint[A2, INPUT, ERROR_OUTPUT, OUTPUT, R with R2] =
    this.copy(securityInput = securityInput)
  override private[tapir] def withInput[I2, R2](input: EndpointInput[I2]): Endpoint[SECURITY_INPUT, I2, ERROR_OUTPUT, OUTPUT, R with R2] =
    this.copy(input = input)
  override private[tapir] def withErrorOutput[E2, R2](
      errorOutput: EndpointOutput[E2]
  ): Endpoint[SECURITY_INPUT, INPUT, E2, OUTPUT, R with R2] =
    this.copy(errorOutput = errorOutput)
  override private[tapir] def withErrorOutputVariant[E2, R2](
      errorOutput: EndpointOutput[E2],
      embedE: ERROR_OUTPUT => E2
  ): Endpoint[SECURITY_INPUT, INPUT, E2, OUTPUT, R with R2] =
    this.copy(errorOutput = errorOutput)
  override private[tapir] def withOutput[O2, R2](output: EndpointOutput[O2]): Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, O2, R with R2] =
    this.copy(output = output)
  override private[tapir] def withInfo(info: EndpointInfo): Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R] =
    this.copy(info = info)
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

  def mapErrorOutDecode[EE](f: E => DecodeResult[EE])(g: EE => E): EndpointType[A, I, EE, O, R] =
    withErrorOutput(errorOutput.mapDecode(f)(g))

  // mapError(f)(g) is defined in EndpointErrorOutputVariantsOps
}

trait EndpointErrorOutputVariantsOps[A, I, E, O, -R] {
  type EndpointType[_A, _I, _E, _O, -_R]
  def errorOutput: EndpointOutput[E]
  private[tapir] def withErrorOutputVariant[E2, R2](output: EndpointOutput[E2], embedE: E => E2): EndpointType[A, I, E2, O, R with R2]

  /** Replaces the current error output with a [[Tapir.oneOf]] output, where:
    *   - the first output variant is the current output: `oneOfVariant(errorOutput)`
    *   - the second output variant is the given `o`
    *
    * The variant for the current endpoint output will be created using [[Tapir.oneOfVariant]]. Hence, the current output will be used if
    * the run-time class of the output matches `E`. If the erasure of the `E` type is different from `E`, there will be a compile-time
    * failure, as no such run-time check is possible. In this case, use [[errorOutVariantsFromCurrent]] and create a variant using one of
    * the other variant factory methods (e.g. [[Tapir.oneOfVariantValueMatcher]]).
    *
    * During encoding/decoding, the new `o` variant will be considered after the current variant.
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

  /** Replaces the current error output with a [[Tapir.oneOf]] output, where:
    *   - the first output variant is the given `o`
    *   - the second, default output variant is the current output: `oneOfDefaultVariant(errorOutput)`
    *
    * Useful for adding specific error variants, while the more general ones are already covered by the existing error output.
    *
    * During encoding/decoding, the new `o` variant will be considered before the current variant.
    *
    * Adding error output variants is useful when extending the error outputs in a [[PartialServerEndpoint]], created using
    * [[EndpointServerLogicOps.serverSecurityLogic]].
    *
    * @param o
    *   The variant to add. Can be created given an output with one of the [[Tapir.oneOfVariant]] methods.
    * @tparam E2
    *   A common supertype of the new variant and the current output `E`.
    */
  def errorOutVariantPrepend[E2 >: E](o: OneOfVariant[_ <: E2]): EndpointType[A, I, E2, O, R] =
    withErrorOutputVariant(oneOf[E2](o, oneOfDefaultVariant(errorOutput)), identity)

  /** Same as [[errorOutVariantPrepend]], but allows appending multiple variants in one go. */
  def errorOutVariantsPrepend[E2 >: E](first: OneOfVariant[_ <: E2], other: OneOfVariant[_ <: E2]*): EndpointType[A, I, E2, O, R] =
    withErrorOutputVariant(oneOf[E2](first, other :+ oneOfDefaultVariant(errorOutput): _*), identity)

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

  def mapErrorOut[EE](f: E => EE)(g: EE => E): EndpointType[A, I, EE, O, R] =
    withErrorOutputVariant(errorOutput.map(f)(g), f)
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

  def prependOut[BS, P, PO, R2](i: StreamBodyIO[BS, P, R2])(implicit ts: ParamConcat.Aux[P, O, PO]): EndpointType[A, I, E, PO, R with R2] =
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
  def deprecated(): ThisType[R] = withInfo(info.deprecated(true))
  def attribute[T](k: AttributeKey[T]): Option[T] = info.attribute(k)
  def attribute[T](k: AttributeKey[T], v: T): ThisType[R] = withInfo(info.attribute(k, v))

  /** Append `ts` to the existing tags. */
  def tags(ts: List[String]): ThisType[R] = withInfo(info.tags(ts))

  /** Append `t` to the existing tags. */
  def tag(t: String): ThisType[R] = withInfo(info.tag(t))

  /** Overwrite the existing tags with `ts`. */
  def withTags(ts: List[String]): ThisType[R] = withInfo(info.withTags(ts))

  /** Overwrite the existing tags with a single tag `t`. */
  def withTag(t: String): ThisType[R] = withInfo(info.withTag(t))

  /** Remove all tags from this endpoint. */
  def withoutTags: ThisType[R] = withInfo(info.withoutTags)

  def info(i: EndpointInfo): ThisType[R] = withInfo(i)
}

trait EndpointMetaOps {
  def securityInput: EndpointInput[_]
  def input: EndpointInput[_]
  def errorOutput: EndpointOutput[_]
  def output: EndpointOutput[_]
  def info: EndpointInfo

  /** Shortened information about the endpoint. If the endpoint is named, returns the name, e.g. `[my endpoint]`. Otherwise, returns the
    * string representation of the method (if any) and path, e.g. `POST /books/add`
    */
  lazy val showShort: String = info.name match {
    case None       => s"${method.map(_.toString()).getOrElse("*")} ${showPathTemplate(showQueryParam = None)}"
    case Some(name) => s"[$name]"
  }

  /** Basic information about the endpoint, excluding mapping information, with inputs sorted (first the method, then path, etc.). E.g.:
    * `POST /books /add {header Authorization} {body as application/json (UTF-8)} -> {body as text/plain (UTF-8)}/-`
    */
  lazy val show: String = {
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
    * information. E.g.:
    *
    * {{{
    * Endpoint(securityin: -, in: /books POST /add {body as application/json (UTF-8)} {header Authorization}, errout: {body as text/plain (UTF-8)}, out: -)
    * }}}
    */
  lazy val showDetail: String =
    s"$showType${info.name.map("[" + _ + "]").getOrElse("")}(securityin: ${securityInput.show}, in: ${input.show}, errout: ${errorOutput.show}, out: ${output.show})"
  protected def showType: String

  /** Equivalent to `.toString`, shows the whole case class structure. */
  def showRaw: String = toString

  /** Shows endpoint path, by default all parametrised path and query components are replaced by {param_name} or {paramN}, e.g. for
    * {{{
    * endpoint.in("p1" / path[String] / query[String]("par2"))
    * }}}
    * returns `/p1/{param1}?par2={par2}`
    *
    * @param includeAuth
    *   Should authentication inputs be included in the result.
    * @param showNoPathAs
    *   How to show the path if the endpoint does not define any path inputs.
    * @param showPathsAs
    *   How to show [[Tapir.paths]] inputs (if at all), which capture multiple paths segments
    * @param showQueryParamsAs
    *   How to show [[Tapir.queryParams]] inputs (if at all), which capture multiple query parameters
    */
  def showPathTemplate(
      showPathParam: (Int, PathCapture[_]) => String = (index, pc) => pc.name.map(name => s"{$name}").getOrElse(s"{param$index}"),
      showQueryParam: Option[(Int, Query[_]) => String] = Some((_, q) => s"${q.name}={${q.name}}"),
      includeAuth: Boolean = true,
      showNoPathAs: String = "*",
      showPathsAs: Option[String] = Some("*"),
      showQueryParamsAs: Option[String] = Some("*")
  ): String = ShowPathTemplate(this)(showPathParam, showQueryParam, includeAuth, showNoPathAs, showPathsAs, showQueryParamsAs)

  /** The method defined in a fixed method input in this endpoint, if any (using e.g. [[EndpointInputsOps.get]] or
    * [[EndpointInputsOps.post]]).
    */
  lazy val method: Option[Method] = {
    import sttp.tapir.internal._
    input.method.orElse(securityInput.method)
  }
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

  /** Like [[serverLogic]], but specialised to the case when the result is always a pure success (`Right`), that is doesn't have any side
    * effects.
    */
  def serverLogicSuccessPure[F[_]](f: I => O)(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, F] =
    ServerEndpoint.public(this.asInstanceOf[Endpoint[Unit, I, E, O, R]], implicit m => i => f(i).unit.map(Right(_)))

  /** Like [[serverLogic]], but specialised to the case when the result is always a pure error (`Left`), that is doesn't have any side
    * effects.
    */
  def serverLogicErrorPure[F[_]](f: I => E)(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, F] =
    ServerEndpoint.public(this.asInstanceOf[Endpoint[Unit, I, E, O, R]], implicit m => i => f(i).unit.map(Left(_)))

  /** Same as [[serverLogic]], but requires `E` to be a throwable, and converts failed effects of type `E` to endpoint errors. */
  def serverLogicRecoverErrors[F[_]](
      f: I => F[O]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E], aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, F] =
    ServerEndpoint.public(this.asInstanceOf[Endpoint[Unit, I, E, O, R]], recoverErrors1[I, E, O, F](f))

  /** Like [[serverLogic]], but specialised to the case when the error type is `Unit` (e.g. a fixed status code), and the result of the
    * logic function is an option. A `None` is then treated as an error response.
    */
  def serverLogicOption[F[_]](
      f: I => F[Option[O]]
  )(implicit aIsUnit: A =:= Unit, eIsUnit: E =:= Unit): ServerEndpoint.Full[Unit, Unit, I, Unit, O, R, F] = {
    import sttp.monad.syntax._
    ServerEndpoint.public(
      this.asInstanceOf[Endpoint[Unit, I, Unit, O, R]],
      implicit m =>
        i =>
          f(i).map {
            case None    => Left(())
            case Some(v) => Right(v)
          }
    )
  }

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
  def serverSecurityLogic[PRINCIPAL, F[_]](f: A => F[Either[E, PRINCIPAL]]): PartialServerEndpoint[A, PRINCIPAL, I, E, O, R, F] =
    PartialServerEndpoint(this, _ => f)

  /** Like [[serverSecurityLogic]], but specialised to the case when the result is always a success (`Right`), hence when the logic type can
    * be simplified to `A => F[PRINCIPAL]`.
    */
  def serverSecurityLogicSuccess[PRINCIPAL, F[_]](
      f: A => F[PRINCIPAL]
  ): PartialServerEndpoint[A, PRINCIPAL, I, E, O, R, F] =
    PartialServerEndpoint(this, implicit m => a => f(a).map(Right(_)))

  /** Like [[serverSecurityLogic]], but specialised to the case when the result is always an error (`Left`), hence when the logic type can
    * be simplified to `A => F[E]`.
    */
  def serverSecurityLogicError[PRINCIPAL, F[_]](
      f: A => F[E]
  ): PartialServerEndpoint[A, PRINCIPAL, I, E, O, R, F] =
    PartialServerEndpoint(this, implicit m => a => f(a).map(Left(_)))

  /** Like [[serverSecurityLogic]], but specialised to the case when the logic function is pure, that is doesn't have any side effects. */
  def serverSecurityLogicPure[PRINCIPAL, F[_]](f: A => Either[E, PRINCIPAL]): PartialServerEndpoint[A, PRINCIPAL, I, E, O, R, F] =
    PartialServerEndpoint(this, implicit m => a => f(a).unit)

  /** Same as [[serverSecurityLogic]], but requires `E` to be a throwable, and converts failed effects of type `E` to endpoint errors. */
  def serverSecurityLogicRecoverErrors[PRINCIPAL, F[_]](
      f: A => F[PRINCIPAL]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): PartialServerEndpoint[A, PRINCIPAL, I, E, O, R, F] =
    PartialServerEndpoint(this, recoverErrors1[A, E, PRINCIPAL, F](f))

  /** Like [[serverSecurityLogic]], but specialised to the case when the error type is `Unit` (e.g. a fixed status code), and the result of
    * the logic function is an option. A `None` is then treated as an error response.
    */
  def serverSecurityLogicOption[PRINCIPAL, F[_]](
      f: A => F[Option[PRINCIPAL]]
  )(implicit eIsUnit: E =:= Unit): PartialServerEndpoint[A, PRINCIPAL, I, Unit, O, R, F] = {
    import sttp.monad.syntax._
    PartialServerEndpoint(
      this.asInstanceOf[Endpoint[A, I, Unit, O, R]],
      implicit m =>
        a =>
          f(a).map {
            case None    => Left(())
            case Some(v) => Right(v)
          }
    )
  }

  //

  /** Like [[serverSecurityLogic]], but allows the security function to contribute to the overall output of the endpoint. A value for the
    * complete output `O` defined so far has to be provided. The value `PRINCIPAL` will be propagated as an input to the regular logic.
    */
  def serverSecurityLogicWithOutput[PRINCIPAL, F[_]](
      f: A => F[Either[E, (O, PRINCIPAL)]]
  ): PartialServerEndpointWithSecurityOutput[A, PRINCIPAL, I, E, O, Unit, R, F] =
    PartialServerEndpointWithSecurityOutput(this.output, this.copy(output = emptyOutput), _ => f)

  /** Like [[serverSecurityLogicWithOutput]], but specialised to the case when the result is always a success (`Right`), hence when the
    * logic type can be simplified to `A => F[(O, PRINCIPAL)]`.
    */
  def serverSecurityLogicSuccessWithOutput[PRINCIPAL, F[_]](
      f: A => F[(O, PRINCIPAL)]
  ): PartialServerEndpointWithSecurityOutput[A, PRINCIPAL, I, E, O, Unit, R, F] =
    PartialServerEndpointWithSecurityOutput(this.output, this.copy(output = emptyOutput), implicit m => a => f(a).map(Right(_)))

  /** Like [[serverSecurityLogicWithOutput]], but specialised to the case when the logic function is pure, that is doesn't have any side
    * effects.
    */
  def serverSecurityLogicPureWithOutput[PRINCIPAL, F[_]](
      f: A => Either[E, (O, PRINCIPAL)]
  ): PartialServerEndpointWithSecurityOutput[A, PRINCIPAL, I, E, O, Unit, R, F] =
    PartialServerEndpointWithSecurityOutput(this.output, this.copy(output = emptyOutput), implicit m => a => f(a).unit)

  /** Same as [[serverSecurityLogicWithOutput]], but requires `E` to be a throwable, and converts failed effects of type `E` to endpoint
    * errors.
    */
  def serverSecurityLogicRecoverErrorsWithOutput[PRINCIPAL, F[_]](
      f: A => F[(O, PRINCIPAL)]
  )(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): PartialServerEndpointWithSecurityOutput[A, PRINCIPAL, I, E, O, Unit, R, F] =
    PartialServerEndpointWithSecurityOutput(this.output, this.copy(output = emptyOutput), recoverErrors1[A, E, (O, PRINCIPAL), F](f))

  /** Like [[serverSecurityLogicWithOutput]], but specialised to the case when the error type is `Unit` (e.g. a fixed status code), and the
    * result of the logic function is an option. A `None` is then treated as an error response.
    */
  def serverSecurityLogicOptionWithOutput[PRINCIPAL, F[_]](
      f: A => F[Option[(O, PRINCIPAL)]]
  )(implicit eIsUnit: E =:= Unit): PartialServerEndpointWithSecurityOutput[A, PRINCIPAL, I, Unit, O, Unit, R, F] = {
    import sttp.monad.syntax._
    PartialServerEndpointWithSecurityOutput(
      this.output,
      this.copy(output = emptyOutput).asInstanceOf[Endpoint[A, I, Unit, Unit, R]],
      implicit m =>
        a =>
          f(a).map {
            case None    => Left(())
            case Some(v) => Right(v)
          }
    )
  }

  // Direct-style

  /** Direct-style variant of [[serverLogic]], using the [[Identity]] "effect". */
  def handle(f: I => Either[E, O])(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Identity] =
    serverLogic[Identity](f)

  /** Direct-style variant of [[serverLogicSuccess]], using the [[Identity]] "effect". */
  def handleSuccess(f: I => O)(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Identity] =
    serverLogicSuccess[Identity](f)

  /** Direct-style variant of [[serverLogicError]], using the [[Identity]] "effect". */
  def handleError(f: I => E)(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Identity] =
    serverLogicError[Identity](f)

  /** Direct-style variant of [[serverLogicRecoverErrors]], using the [[Identity]] "effect". */
  def handleRecoverErrors(
      f: I => O
  )(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E],
      aIsUnit: A =:= Unit
  ): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Identity] =
    serverLogicRecoverErrors[Identity](f)

  /** Direct-style variant of [[serverLogicOption]], using the [[Identity]] "effect". */
  def handleOption(
      f: I => Option[O]
  )(implicit aIsUnit: A =:= Unit, eIsUnit: E =:= Unit): ServerEndpoint.Full[Unit, Unit, I, Unit, O, R, Identity] =
    serverLogicOption[Identity](f)

  //

  /** Direct-style variant of [[serverSecurityLogic]], using the [[Identity]] "effect". */
  def handleSecurity[PRINCIPAL](f: A => Either[E, PRINCIPAL]): PartialServerEndpointSync[A, PRINCIPAL, I, E, O, R] =
    PartialServerEndpointSync(this, f)

  /** Direct-style variant of [[serverSecurityLogicSuccess]], using the [[Identity]] "effect". */
  def handleSecuritySuccess[PRINCIPAL](f: A => PRINCIPAL): PartialServerEndpointSync[A, PRINCIPAL, I, E, O, R] =
    PartialServerEndpointSync(this, a => Right(f(a)))

  /** Direct-style variant of [[serverSecurityLogicError]], using the [[Identity]] "effect". */
  def handleSecurityError[PRINCIPAL](f: A => E): PartialServerEndpointSync[A, PRINCIPAL, I, E, O, R] =
    PartialServerEndpointSync(this, a => Left(f(a)))

  /** Direct-style variant of [[serverSecurityLogicRecoverErrors]], using the [[Identity]] "effect". */
  def handleSecurityRecoverErrors[PRINCIPAL](
      f: A => PRINCIPAL
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): PartialServerEndpointSync[A, PRINCIPAL, I, E, O, R] =
    PartialServerEndpointSync(this, recoverErrors1[A, E, PRINCIPAL, Identity](f)(implicitly, implicitly)(IdentityMonad))

  /** Direct-style variant of [[serverSecurityLogicOption]], using the [[Identity]] "effect". */
  def handleSecurityOption[PRINCIPAL](f: A => Option[PRINCIPAL])(implicit
      eIsUnit: E =:= Unit
  ): PartialServerEndpointSync[A, PRINCIPAL, I, Unit, O, R] = {
    PartialServerEndpointSync(
      this.asInstanceOf[Endpoint[A, I, Unit, O, R]],
      a =>
        f(a) match {
          case None    => Left(())
          case Some(v) => Right(v)
        }
    )
  }

  //

  /** Direct-style variant of [[serverSecurityLogicWithOutput]], using the [[Identity]] "effect". */
  def handleSecurityWithOutput[PRINCIPAL](
      f: A => Either[E, (O, PRINCIPAL)]
  ): PartialServerEndpointWithSecurityOutputSync[A, PRINCIPAL, I, E, O, Unit, R] =
    PartialServerEndpointWithSecurityOutputSync(this.output, this.copy(output = emptyOutput), f)

  /** Direct-style variant of [[serverSecurityLogicSuccessWithOutput]], using the [[Identity]] "effect". */
  def handleSecuritySuccessWithOutput[PRINCIPAL](
      f: A => (O, PRINCIPAL)
  ): PartialServerEndpointWithSecurityOutputSync[A, PRINCIPAL, I, E, O, Unit, R] =
    PartialServerEndpointWithSecurityOutputSync(this.output, this.copy(output = emptyOutput), a => Right(f(a)))

  /** Direct-style variant of [[serverSecurityLogicRecoverErrorsWithOutput]], using the [[Identity]] "effect". */
  def handleSecurityRecoverErrorsWithOutput[PRINCIPAL](
      f: A => (O, PRINCIPAL)
  )(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): PartialServerEndpointWithSecurityOutputSync[A, PRINCIPAL, I, E, O, Unit, R] =
    PartialServerEndpointWithSecurityOutputSync(
      this.output,
      this.copy(output = emptyOutput),
      recoverErrors1[A, E, (O, PRINCIPAL), Identity](f)(implicitly, implicitly)(IdentityMonad)
    )

  /** Direct-style variant of [[serverSecurityLogicOptionWithOutput]], using the [[Identity]] "effect". */
  def handleSecurityOptionWithOutput[PRINCIPAL](
      f: A => Option[(O, PRINCIPAL)]
  )(implicit eIsUnit: E =:= Unit): PartialServerEndpointWithSecurityOutputSync[A, PRINCIPAL, I, Unit, O, Unit, R] = {
    PartialServerEndpointWithSecurityOutputSync(
      this.output,
      this.copy(output = emptyOutput).asInstanceOf[Endpoint[A, I, Unit, Unit, R]],
      a =>
        f(a) match {
          case None    => Left(())
          case Some(v) => Right(v)
        }
    )
  }
}

case class EndpointInfo(
    name: Option[String],
    summary: Option[String],
    description: Option[String],
    tags: Vector[String],
    deprecated: Boolean,
    attributes: AttributeMap
) {
  def name(n: String): EndpointInfo = this.copy(name = Some(n))
  def summary(s: String): EndpointInfo = copy(summary = Some(s))
  def description(d: String): EndpointInfo = copy(description = Some(d))
  def deprecated(d: Boolean): EndpointInfo = copy(deprecated = d)
  def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)
  def attribute[T](k: AttributeKey[T], v: T): EndpointInfo = copy(attributes = attributes.put(k, v))

  /** Append to the existing tags * */
  def tags(ts: List[String]): EndpointInfo = copy(tags = tags ++ ts)
  def tag(t: String): EndpointInfo = copy(tags = tags :+ t)

  /** Overwrite the existing tags * */
  def withTags(ts: List[String]): EndpointInfo = copy(tags = ts.toVector)
  def withTag(t: String): EndpointInfo = copy(tags = Vector(t))
  def withoutTags: EndpointInfo = copy(tags = Vector.empty)
}
