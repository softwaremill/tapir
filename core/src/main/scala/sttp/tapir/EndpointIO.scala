package sttp.tapir

import java.nio.charset.Charset
import sttp.capabilities.{Streams, WebSockets}
import sttp.model.{Header, Method}
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.EndpointIO.Info
import sttp.tapir.internal._
import sttp.tapir.model.ServerRequest
import sttp.tapir.typelevel.{FnComponents, ParamConcat}
import sttp.ws.WebSocketFrame

import scala.collection.immutable.ListMap
import scala.concurrent.duration.FiniteDuration

/** A transput is EITHER an input, or an output (see: https://ell.stackexchange.com/questions/21405/hypernym-for-input-and-output).
  * The transput traits contain common functionality, shared by all inputs and outputs.
  *
  * Note that implementations of `EndpointIO` can be used BOTH as inputs and outputs.
  *
  * The hierarchy is as follows:
  *
  *                        /---> `EndpointInput`  >---\
  * `EndpointTransput` >---                            ---> `EndpointIO`
  *                        \---> `EndpointOutput` >---/
  *
  * @tparam R The capabilities that are required by this transput. This might be `Any` (no requirements),
  *           [[sttp.capabilities.Effect]] (the interpreter must support the given effect type),
  *           [[sttp.capabilities.Streams]] (the ability to send and receive streaming bodies) or
  *           [[sttp.capabilities.WebSockets]] (the ability to handle websocket requests).
  */
sealed trait EndpointTransput[T, -R] {
  private[tapir] type ThisType[X]

  def map[U](mapping: Mapping[T, U]): ThisType[U]
  def map[U](f: T => U)(g: U => T): ThisType[U] = map(Mapping.from(f)(g))
  def mapDecode[U](f: T => DecodeResult[U])(g: U => T): ThisType[U] = map(Mapping.fromDecode(f)(g))
  def mapTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, T, CASE_CLASS]): ThisType[CASE_CLASS] = {
    map[CASE_CLASS](fc.tupled(c).apply(_))(ProductToParams(_, fc.arity).asInstanceOf[T])
  }

  def validate(v: Validator[T]): ThisType[T] = map(Mapping.id[T].validate(v))

  def show: String
}

object EndpointTransput {
  import EndpointIO.{Example, Info}

  sealed trait Basic[T, -R] extends EndpointTransput[T, R] {
    private[tapir] type L
    private[tapir] type CF <: CodecFormat

    def codec: Codec[L, T, CF]
    def info: Info[T]
    private[tapir] def copyWith[U](c: Codec[L, U, CF], i: Info[U]): ThisType[U]

    override def map[U](mapping: Mapping[T, U]): ThisType[U] = copyWith(codec.map(mapping), info.map(mapping))

    def schema(s: Schema[T]): ThisType[T] = copyWith(codec.schema(s), info)
    def schema(s: Option[Schema[T]]): ThisType[T] = copyWith(codec.schema(s), info)
    def modifySchema(modify: Schema[T] => Schema[T]): ThisType[T] = copyWith(codec.modifySchema(modify), info)

    def description(d: String): ThisType[T] = copyWith(codec, info.description(d))
    def example(t: T): ThisType[T] = copyWith(codec, info.example(t))
    def example(example: Example[T]): ThisType[T] = copyWith(codec, info.example(example))
    def examples(examples: List[Example[T]]): ThisType[T] = copyWith(codec, info.examples(examples))
    def deprecated(): ThisType[T] = copyWith(codec, info.deprecated(true))
  }

  sealed trait Pair[T, -R] extends EndpointTransput[T, R] {
    def left: EndpointTransput[_, R]
    def right: EndpointTransput[_, R]

    private[tapir] val combine: CombineParams
    private[tapir] val split: SplitParams

    override def show: String = {
      def flattenedPairs(et: EndpointTransput[_, R]): Vector[EndpointTransput[_, R]] =
        et match {
          case p: Pair[_, R] => flattenedPairs(p.left) ++ flattenedPairs(p.right)
          case other         => Vector(other)
        }
      showMultiple(flattenedPairs(this))
    }
  }
}

sealed trait EndpointInput[T, -R] extends EndpointTransput[T, R] {
  private[tapir] type ThisType[X] <: EndpointInput[X, R]

  def and[U, TU, R2](other: EndpointInput[U, R2])(implicit concat: ParamConcat.Aux[T, U, TU]): EndpointInput[TU, R with R2] =
    EndpointInput.Pair(this, other, mkCombine(concat), mkSplit(concat))
  def /[U, TU, R2](other: EndpointInput[U, R2])(implicit concat: ParamConcat.Aux[T, U, TU]): EndpointInput[TU, R with R2] = and(other)
}

object EndpointInput {
  import EndpointIO.Info

  sealed trait Single[T, -R] extends EndpointInput[T, R] {
    private[tapir] type ThisType[X] <: EndpointInput.Single[X, R]
  }

  sealed trait Basic[T, -R] extends Single[T, R] with EndpointTransput.Basic[T, R]

  case class FixedMethod[T](m: Method, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = FixedMethod[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedMethod[U] = copy(codec = c, info = i)
    override def show: String = m.method
  }

  case class FixedPath[T](s: String, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = FixedPath[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedPath[U] = copy(codec = c, info = i)
    override def show = s"/$s"
  }

  case class PathCapture[T](name: Option[String], codec: Codec[String, T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = PathCapture[X]
    override private[tapir] type L = String
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[String, U, TextPlain], i: Info[U]): PathCapture[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"/[${name.getOrElse("")}]", codec.validator)

    def name(n: String): PathCapture[T] = copy(name = Some(n))
  }

  case class PathsCapture[T](codec: Codec[List[String], T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = PathsCapture[X]
    override private[tapir] type L = List[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[String], U, TextPlain], i: Info[U]): PathsCapture[U] = copy(codec = c, info = i)
    override def show = s"/..."
  }

  case class Query[T](name: String, codec: Codec[List[String], T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = Query[X]
    override private[tapir] type L = List[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[String], U, TextPlain], i: Info[U]): Query[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"?$name", codec.validator)
  }

  case class QueryParams[T](codec: Codec[sttp.model.QueryParams, T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = QueryParams[X]
    override private[tapir] type L = sttp.model.QueryParams
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[sttp.model.QueryParams, U, TextPlain], i: Info[U]): QueryParams[U] =
      copy(codec = c, info = i)
    override def show: String = s"?..."
  }

  case class Cookie[T](name: String, codec: Codec[Option[String], T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = Cookie[X]
    override private[tapir] type L = Option[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Option[String], U, TextPlain], i: Info[U]): Cookie[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"{cookie $name}", codec.validator)
  }

  case class ExtractFromRequest[T](codec: Codec[ServerRequest, T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = ExtractFromRequest[X]
    override private[tapir] type L = ServerRequest
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[ServerRequest, U, TextPlain], i: Info[U]): ExtractFromRequest[U] =
      copy(codec = c, info = i)
    override def show: String = s"{data from request}"
  }

  //

  case class WWWAuthenticate(values: List[String]) {
    def headers: List[Header] = values.map(Header("WWW-Authenticate", _))
  }

  object WWWAuthenticate {
    val DefaultRealm = "default realm"
    def basic(realm: String = DefaultRealm): WWWAuthenticate = single("Basic", realm)
    def bearer(realm: String = DefaultRealm): WWWAuthenticate = single("Bearer", realm)
    def apiKey(realm: String = DefaultRealm): WWWAuthenticate = single("ApiKey", realm)
    def single(scheme: String, realm: String = DefaultRealm): WWWAuthenticate = WWWAuthenticate(List(s"""$scheme realm="$realm""""))
  }

  trait Auth[T, -R] extends EndpointInput.Single[T, R] {
    def input: EndpointInput.Single[T, R]
    def challenge: WWWAuthenticate
    def securitySchemeName: Option[String]
    def securitySchemeName(name: String): ThisType[T]
  }

  object Auth {
    case class ApiKey[T, R](input: EndpointInput.Single[T, R], challenge: WWWAuthenticate, securitySchemeName: Option[String])
        extends Auth[T, R] {
      override private[tapir] type ThisType[X] = ApiKey[X, R]
      override def show: String = s"auth(api key, via ${input.show})"
      override def map[U](mapping: Mapping[T, U]): ApiKey[U, R] = copy(input = input.map(mapping))

      override def securitySchemeName(name: String): ApiKey[T, R] = copy(securitySchemeName = Some(name))
    }
    case class Http[T, R](
        scheme: String,
        input: EndpointInput.Single[T, R],
        challenge: WWWAuthenticate,
        securitySchemeName: Option[String]
    ) extends Auth[T, R] {
      override private[tapir] type ThisType[X] = Http[X, R]
      override def show: String = s"auth($scheme http, via ${input.show})"
      override def map[U](mapping: Mapping[T, U]): Http[U, R] = copy(input = input.map(mapping))

      override def securitySchemeName(name: String): Http[T, R] = copy(securitySchemeName = Some(name))
    }
    case class Oauth2[T, R](
        authorizationUrl: String,
        tokenUrl: String,
        scopes: ListMap[String, String],
        refreshUrl: Option[String] = None,
        input: EndpointInput.Single[T, R],
        challenge: WWWAuthenticate,
        securitySchemeName: Option[String]
    ) extends Auth[T, R] {
      override private[tapir] type ThisType[X] = Oauth2[X, R]
      override def show: String = s"auth(oauth2, via ${input.show})"
      override def map[U](mapping: Mapping[T, U]): Oauth2[U, R] = copy(input = input.map(mapping))

      def requiredScopes(requiredScopes: Seq[String]): ScopedOauth2[T, R] = ScopedOauth2(this, requiredScopes)

      override def securitySchemeName(name: String): Oauth2[T, R] = copy(securitySchemeName = Some(name))
    }

    case class ScopedOauth2[T, R](oauth2: Oauth2[T, R], requiredScopes: Seq[String]) extends Auth[T, R] {
      require(requiredScopes.forall(oauth2.scopes.keySet.contains), "all requiredScopes have to be defined on outer Oauth2#scopes")

      override private[tapir] type ThisType[X] = ScopedOauth2[X, R]
      override def show: String = s"scoped(${oauth2.show})"
      override def map[U](mapping: Mapping[T, U]): ScopedOauth2[U, R] = copy(oauth2 = oauth2.map(mapping))

      override def input: Single[T, R] = oauth2.input

      override def challenge: WWWAuthenticate = oauth2.challenge
      override def securitySchemeName: Option[String] = oauth2.securitySchemeName

      override def securitySchemeName(name: String): ScopedOauth2[T, R] = copy(oauth2 = oauth2.securitySchemeName(name))
    }
  }

  //

  case class MappedPair[T, U, TU, V, R](input: Pair[T, U, TU, R], mapping: Mapping[TU, V]) extends EndpointInput.Single[V, R] {
    override private[tapir] type ThisType[X] = MappedPair[T, U, TU, X, R]
    override def show: String = input.show
    override def map[W](m: Mapping[V, W]): MappedPair[T, U, TU, W, R] = copy[T, U, TU, W, R](input, mapping.map(m))
  }

  case class Pair[T, U, TU, R](
      left: EndpointInput[T, R],
      right: EndpointInput[U, R],
      private[tapir] val combine: CombineParams,
      private[tapir] val split: SplitParams
  ) extends EndpointInput[TU, R]
      with EndpointTransput.Pair[TU, R] {
    override private[tapir] type ThisType[X] = EndpointInput[X, R]
    override def map[V](m: Mapping[TU, V]): EndpointInput[V, R] = MappedPair[T, U, TU, V, R](this, m)
  }
}

sealed trait EndpointOutput[T, -R] extends EndpointTransput[T, R] {
  private[tapir] type ThisType[X] <: EndpointOutput[X, R]

  def and[J, IJ, R2](other: EndpointOutput[J, R2])(implicit concat: ParamConcat.Aux[T, J, IJ]): EndpointOutput[IJ, R with R2] =
    EndpointOutput.Pair(this, other, mkCombine(concat), mkSplit(concat))
}

object EndpointOutput {
  sealed trait Single[T, -R] extends EndpointOutput[T, R] {
    private[tapir] def _mapping: Mapping[_, T]
  }

  sealed trait Basic[T, -R] extends Single[T, R] with EndpointTransput.Basic[T, R] {
    override private[tapir] def _mapping: Mapping[_, T] = codec
  }

  //

  case class StatusCode[T](
      documentedCodes: Map[sttp.model.StatusCode, Info[Unit]],
      codec: Codec[sttp.model.StatusCode, T, TextPlain],
      info: Info[T]
  ) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = StatusCode[X]
    override private[tapir] type L = sttp.model.StatusCode
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[sttp.model.StatusCode, U, TextPlain], i: Info[U]): StatusCode[U] =
      copy(codec = c, info = i)
    override def show: String = s"status code - possible codes ($documentedCodes)"

    def description(code: sttp.model.StatusCode, d: String): StatusCode[T] = {
      val updatedCodes = documentedCodes + (code -> Info.empty[Unit].description(d))
      copy(documentedCodes = updatedCodes)
    }
  }

  //

  case class FixedStatusCode[T](statusCode: sttp.model.StatusCode, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = FixedStatusCode[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedStatusCode[U] = copy(codec = c, info = i)
    override def show: String = s"status code ($statusCode)"
  }

  // P == streams.Pipe, but we can't express this using dependent types here.
  case class WebSocketBody[PIPE_REQ_RESP, REQ, RESP, T, S](
      streams: Streams[S],
      requests: Codec[WebSocketFrame, REQ, CodecFormat],
      responses: Codec[WebSocketFrame, RESP, CodecFormat],
      codec: Codec[PIPE_REQ_RESP, T, CodecFormat],
      info: Info[T],
      concatenateFragmentedFrames: Boolean,
      ignorePong: Boolean,
      autoPongOnPing: Boolean,
      decodeCloseRequests: Boolean,
      decodeCloseResponses: Boolean,
      autoPing: Option[(FiniteDuration, WebSocketFrame.Ping)]
  ) extends Basic[T, S with WebSockets] {
    override private[tapir] type ThisType[X] = WebSocketBody[PIPE_REQ_RESP, REQ, RESP, X, S]
    override private[tapir] type L = PIPE_REQ_RESP
    override private[tapir] type CF = CodecFormat
    override private[tapir] def copyWith[U](c: Codec[PIPE_REQ_RESP, U, CodecFormat], i: Info[U]) = copy(codec = c, info = i)

    def requestsSchema(s: Schema[REQ]): ThisType[T] = copy(requests = requests.schema(s))
    def requestsSchema(s: Option[Schema[REQ]]): ThisType[T] = copy(requests = requests.schema(s))
    def modifyRequestsSchema(modify: Schema[REQ] => Schema[REQ]): ThisType[T] = copy(requests = requests.modifySchema(modify))

    def responsesSchema(s: Schema[RESP]): ThisType[T] = copy(responses = responses.schema(s))
    def responsesSchema(s: Option[Schema[RESP]]): ThisType[T] = copy(responses = responses.schema(s))
    def modifyResponsesSchema(modify: Schema[RESP] => Schema[RESP]): ThisType[T] = copy(responses = responses.modifySchema(modify))

    /** @param c If `true`, fragmented frames will be concatenated, and the data frames that the `requests` & `responses`
      *          codecs decode will always have `finalFragment` set to `true`.
      *          Note that only some interpreters expose fragmented frames.
      */
    def concatenateFragmentedFrames(c: Boolean): WebSocketBody[PIPE_REQ_RESP, REQ, RESP, T, S] =
      this.copy(concatenateFragmentedFrames = c)

    /** Note: some interpreters ignore this setting.
      * @param i If `true`, [[WebSocketFrame.Pong]] frames will be ignored and won't be passed to the codecs for decoding.
      *          Note that only some interpreters expose ping-pong frames.
      */
    def ignorePong(i: Boolean): WebSocketBody[PIPE_REQ_RESP, REQ, RESP, T, S] = this.copy(concatenateFragmentedFrames = i)

    /** Note: some interpreters ignore this setting.
      * @param a If `true`, [[WebSocketFrame.Ping]] frames will cause a matching [[WebSocketFrame.Pong]] frame to be sent
      *          back, and won't be passed to codecs for decoding.
      *          Note that only some interpreters expose ping-pong frames.
      */
    def autoPongOnPing(a: Boolean): WebSocketBody[PIPE_REQ_RESP, REQ, RESP, T, S] = this.copy(concatenateFragmentedFrames = a)

    /** Note: some interpreters ignore this setting.
      * @param d If `true`, [[WebSocketFrame.Close]] frames won't be passed to the request codec for decoding (in server
      *          interpreters).
      */
    def decodeCloseRequests(d: Boolean): WebSocketBody[PIPE_REQ_RESP, REQ, RESP, T, S] = this.copy(decodeCloseRequests = d)

    /** Note: some interpreters ignore this setting.
      * @param d If `true`, [[WebSocketFrame.Close]] frames won't be passed to the response codec for decoding (in client
      *          interpreters).
      */
    def decodeCloseResponses(d: Boolean): WebSocketBody[PIPE_REQ_RESP, REQ, RESP, T, S] = this.copy(decodeCloseResponses = d)

    /** Note: some interpreters ignore this setting.
      * @param p If `Some`, send the given `Ping` frame at the given interval. If `None`, do not automatically send pings.
      */
    def autoPing(p: Option[(FiniteDuration, WebSocketFrame.Ping)]): WebSocketBody[PIPE_REQ_RESP, REQ, RESP, T, S] =
      this.copy(autoPing = p)

    override def show: String = "{body as web socket}"
  }

  /** Specifies that for `statusCode`, the given `output` should be used.
    *
    * The `appliesTo` function should determine, whether a runtime value matches the type `O`.
    * This check cannot be in general done by checking the run-time class of the value, due to type erasure (if `O` has
    * type parameters).
    */
  case class StatusMapping[O, -R] private[tapir] (
      statusCode: Option[sttp.model.StatusCode],
      output: EndpointOutput[O, R],
      appliesTo: Any => Boolean
  )

  case class OneOf[O, T, R](mappings: Seq[StatusMapping[_ <: O, R]], codec: Mapping[O, T]) extends Single[T, R] {
    override private[tapir] type ThisType[X] = OneOf[O, X, R]
    override private[tapir] def _mapping: Mapping[_, T] = codec
    override def map[U](mapping: Mapping[T, U]): OneOf[O, U, R] = copy[O, U, R](codec = codec.map(mapping))
    override def show: String = showOneOf(mappings.map(_.output.show))
  }

  //

  case class Void[T]() extends EndpointOutput[T, Any] {
    override private[tapir] type ThisType[X] = Void[X]
    override def show: String = "void"
    override def map[U](mapping: Mapping[T, U]): Void[U] = Void()

    override def and[U, TU, R2](other: EndpointOutput[U, R2])(implicit concat: ParamConcat.Aux[T, U, TU]): EndpointOutput[TU, R2] =
      other.asInstanceOf[EndpointOutput[TU, R2]]
  }

  //

  case class MappedPair[T, U, TU, V, R](output: Pair[T, U, TU, R], mapping: Mapping[TU, V]) extends EndpointOutput.Single[V, R] {
    override private[tapir] type ThisType[X] = MappedPair[T, U, TU, X, R]
    override private[tapir] def _mapping: Mapping[_, V] = mapping
    override def show: String = output.show
    override def map[W](m: Mapping[V, W]): MappedPair[T, U, TU, W, R] = copy[T, U, TU, W, R](output, mapping.map(m))
  }

  case class Pair[T, U, TU, R](
      left: EndpointOutput[T, R],
      right: EndpointOutput[U, R],
      private[tapir] val combine: CombineParams,
      private[tapir] val split: SplitParams
  ) extends EndpointOutput[TU, R]
      with EndpointTransput.Pair[TU, R] {
    override private[tapir] type ThisType[X] = EndpointOutput[X, R]
    override def map[V](m: Mapping[TU, V]): EndpointOutput[V, R] = MappedPair[T, U, TU, V, R](this, m)
  }
}

sealed trait EndpointIO[T, -R] extends EndpointInput[T, R] with EndpointOutput[T, R] {
  private[tapir] type ThisType[X] <: EndpointInput[X, R] with EndpointOutput[X, R]

  def and[J, IJ, R2](other: EndpointIO[J, R2])(implicit concat: ParamConcat.Aux[T, J, IJ]): EndpointIO[IJ, R with R2] =
    EndpointIO.Pair(this, other, mkCombine(concat), mkSplit(concat))
}

object EndpointIO {
  sealed trait Single[I, -R] extends EndpointIO[I, R] with EndpointInput.Single[I, R] with EndpointOutput.Single[I, R] {
    private[tapir] type ThisType[X] <: EndpointIO.Single[X, R]
  }

  sealed trait Basic[I, -R] extends Single[I, R] with EndpointInput.Basic[I, R] with EndpointOutput.Basic[I, R]

  case class Body[RAW, T](bodyType: RawBodyType[RAW], codec: Codec[RAW, T, CodecFormat], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = Body[RAW, X]
    override private[tapir] type L = RAW
    override private[tapir] type CF = CodecFormat
    override private[tapir] def copyWith[U](c: Codec[RAW, U, CodecFormat], i: Info[U]): Body[RAW, U] = copy(codec = c, info = i)
    override def show: String = {
      val charset = bodyType.asInstanceOf[RawBodyType[_]] match {
        case RawBodyType.StringBody(charset) => s" (${charset.toString})"
        case _                               => ""
      }
      val format = codec.format.mediaType
      addValidatorShow(s"{body as $format$charset}", codec.validator)
    }
  }

  //BS == streams.BinaryStream, but we can't express this using dependent types here.
  case class StreamBody[BS, T, S](streams: Streams[S], codec: Codec[BS, T, CodecFormat], info: Info[T], charset: Option[Charset])
      extends Basic[T, S] {
    override private[tapir] type ThisType[X] = StreamBody[BS, X, S]
    override private[tapir] type L = BS
    override private[tapir] type CF = CodecFormat
    override private[tapir] def copyWith[U](c: Codec[BS, U, CodecFormat], i: Info[U]) = copy(codec = c, info = i)

    override def show: String = "{body as stream}"
  }

  case class FixedHeader[T](h: sttp.model.Header, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = FixedHeader[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedHeader[U] = copy(codec = c, info = i)
    override def show = s"{header ${h.name}: ${h.value}}"
  }

  case class Header[T](name: String, codec: Codec[List[String], T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = Header[X]
    override private[tapir] type L = List[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[String], U, TextPlain], i: Info[U]): Header[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"{header $name}", codec.validator)
  }

  case class Headers[T](codec: Codec[List[sttp.model.Header], T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = Headers[X]
    override private[tapir] type L = List[sttp.model.Header]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[sttp.model.Header], U, TextPlain], i: Info[U]): Headers[U] =
      copy(codec = c, info = i)
    override def show = "{multiple headers}"
  }

  case class Empty[T](codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Basic[T, Any] {
    override private[tapir] type ThisType[X] = Empty[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): Empty[U] = copy(codec = c, info = i)
    override def show = "-"
  }

  //

  case class MappedPair[T, U, TU, V, R](io: Pair[T, U, TU, R], mapping: Mapping[TU, V]) extends EndpointIO.Single[V, R] {
    override private[tapir] type ThisType[X] = MappedPair[T, U, TU, X, R]
    override private[tapir] def _mapping: Mapping[_, V] = mapping
    override def show: String = io.show
    override def map[W](m: Mapping[V, W]): MappedPair[T, U, TU, W, R] = copy[T, U, TU, W, R](io, mapping.map(m))
  }

  case class Pair[T, U, TU, R](
      left: EndpointIO[T, R],
      right: EndpointIO[U, R],
      private[tapir] val combine: CombineParams,
      private[tapir] val split: SplitParams
  ) extends EndpointIO[TU, R]
      with EndpointTransput.Pair[TU, R] {
    override private[tapir] type ThisType[X] = EndpointIO[X, R]
    override def map[V](m: Mapping[TU, V]): EndpointIO[V, R] = MappedPair[T, U, TU, V, R](this, m)
  }

  //

  case class Example[+T](value: T, name: Option[String], summary: Option[String]) {
    def map[B](f: T => B): Example[B] = copy(value = f(value))
  }

  object Example {
    def of[T](t: T, name: Option[String] = None, summary: Option[String] = None): Example[T] = Example(t, name, summary)
  }

  case class Info[T](description: Option[String], examples: List[Example[T]], deprecated: Boolean) {
    def description(d: String): Info[T] = copy(description = Some(d))
    def example: Option[T] = examples.headOption.map(_.value)
    def example(t: T): Info[T] = example(Example.of(t))
    def example(example: Example[T]): Info[T] = copy(examples = examples :+ example)
    def examples(ts: List[Example[T]]): Info[T] = copy(examples = ts)
    def deprecated(d: Boolean): Info[T] = copy(deprecated = d)

    def map[U](codec: Mapping[T, U]): Info[U] =
      Info(
        description,
        examples.map(e => e.copy(value = codec.decode(e.value))).collect { case Example(DecodeResult.Value(ee), name, summary) =>
          Example(ee, name, summary)
        },
        deprecated
      )
  }
  object Info {
    def empty[T]: Info[T] = Info[T](None, Nil, deprecated = false)
  }
}
