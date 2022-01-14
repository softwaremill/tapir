package sttp.tapir

import sttp.capabilities.Streams
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.model.{ContentTypeRange, Method}
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.EndpointIO.{Example, Info}
import sttp.tapir.RawBodyType.StringBody
import sttp.tapir.internal._
import sttp.tapir.macros.{EndpointInputMacros, EndpointOutputMacros, EndpointTransputMacros}
import sttp.tapir.model.ServerRequest
import sttp.tapir.typelevel.ParamConcat
import sttp.ws.WebSocketFrame

import java.nio.charset.{Charset, StandardCharsets}
import scala.annotation.StaticAnnotation
import scala.collection.immutable.{ListMap, Seq}
import scala.concurrent.duration.FiniteDuration

/** A transput is EITHER an input, or an output (see: https://ell.stackexchange.com/questions/21405/hypernym-for-input-and-output). The
  * transput traits contain common functionality, shared by all inputs and outputs.
  *
  * Note that implementations of `EndpointIO` can be used *both* as inputs and outputs.
  *
  * The hierarchy is as follows:
  *
  * {{{
  *                         /---> `EndpointInput`  >---\
  * `EndpointTransput` >---                            ---> `EndpointIO`
  *                         \---> `EndpointOutput` >---/
  * }}}
  *
  * Inputs and outputs additionally form another hierarchy:
  *
  * {{{
  *                                       /--> `Single` >--> `Basic` >--> `Atom`
  * `EndpointInput` / `EndpointOutput` >--
  *                                       \--> `Pair`
  * }}}
  *
  * where the intuition behind the traits is:
  *   - `single`: represents a single value, as opposed to a pair (tuple); a pair, but transformed using `.map` is also a single value
  *   - `basic`: corresponds to an input/output which is encoded/decoded in one step; always `single`
  *   - `atom`: corresponds to a single component of a request or response. Such inputs/outputs contain a [[Codec]] and [[Info]] meta-data,
  *     and are always `basic`
  */
sealed trait EndpointTransput[T] extends EndpointTransputMacros[T] {
  private[tapir] type ThisType[X]

  def map[U](mapping: Mapping[T, U]): ThisType[U]
  def map[U](f: T => U)(g: U => T): ThisType[U] = map(Mapping.from(f)(g))
  def mapDecode[U](f: T => DecodeResult[U])(g: U => T): ThisType[U] = map(Mapping.fromDecode(f)(g))

  def validate(v: Validator[T]): ThisType[T] = map(Mapping.id[T].validate(v))

  def show: String
}

object EndpointTransput {
  sealed trait Basic[T] extends EndpointTransput[T]

  sealed trait Atom[T] extends Basic[T] {
    private[tapir] type L
    private[tapir] type CF <: CodecFormat

    def codec: Codec[L, T, CF]
    def info: Info[T]
    private[tapir] def copyWith[U](c: Codec[L, U, CF], i: Info[U]): ThisType[U]

    override def map[U](mapping: Mapping[T, U]): ThisType[U] = copyWith(codec.map(mapping), info.map(mapping))

    def schema(s: Schema[T]): ThisType[T] = copyWith(codec.schema(s), info)
    def schema(s: Option[Schema[T]]): ThisType[T] = copyWith(codec.schema(s), info)
    def schema(modify: Schema[T] => Schema[T]): ThisType[T] = copyWith(codec.schema(modify), info)

    def validateOption[U](v: Validator[U])(implicit tIsOptionU: T =:= Option[U]): ThisType[T] =
      schema(_.modifyUnsafe[U](Schema.ModifyCollectionElements)(_.validate(v)))
    def validateIterable[C[X] <: Iterable[X], U](v: Validator[U])(implicit tIsCU: T =:= C[U]): ThisType[T] =
      schema(_.modifyUnsafe[U](Schema.ModifyCollectionElements)(_.validate(v)))

    def description(d: String): ThisType[T] = copyWith(codec, info.description(d))
    def default(d: T): ThisType[T] = copyWith(codec.schema(_.default(d, Some(codec.encode(d)))), info)
    def example(t: T): ThisType[T] = copyWith(codec, info.example(t))
    def example(example: Example[T]): ThisType[T] = copyWith(codec, info.example(example))
    def examples(examples: List[Example[T]]): ThisType[T] = copyWith(codec, info.examples(examples))
    def deprecated(): ThisType[T] = copyWith(codec, info.deprecated(true))
    def attribute[A](k: AttributeKey[A]): Option[A] = info.attribute(k)
    def attribute[A](k: AttributeKey[A], v: A): ThisType[T] = copyWith(codec, info.attribute(k, v))
  }

  sealed trait Pair[T] extends EndpointTransput[T] {
    def left: EndpointTransput[_]
    def right: EndpointTransput[_]

    private[tapir] val combine: CombineParams
    private[tapir] val split: SplitParams

    override def show: String = {
      def flattenedPairs(et: EndpointTransput[_]): Vector[EndpointTransput[_]] =
        et match {
          case p: Pair[_] => flattenedPairs(p.left) ++ flattenedPairs(p.right)
          case other      => Vector(other)
        }
      showMultiple(flattenedPairs(this))
    }
  }
}

sealed trait EndpointInput[T] extends EndpointTransput[T] {
  private[tapir] type ThisType[X] <: EndpointInput[X]

  def and[U, TU](other: EndpointInput[U])(implicit concat: ParamConcat.Aux[T, U, TU]): EndpointInput[TU] =
    EndpointInput.Pair(this, other, mkCombine(concat), mkSplit(concat))
  def /[U, TU](other: EndpointInput[U])(implicit concat: ParamConcat.Aux[T, U, TU]): EndpointInput[TU] = and(other)
}

object EndpointInput extends EndpointInputMacros {
  sealed trait Single[T] extends EndpointInput[T] {
    private[tapir] type ThisType[X] <: EndpointInput.Single[X]
  }
  sealed trait Basic[T] extends Single[T] with EndpointTransput.Basic[T]
  sealed trait Atom[T] extends Basic[T] with EndpointTransput.Atom[T]

  case class FixedMethod[T](m: Method, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = FixedMethod[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedMethod[U] = copy(codec = c, info = i)
    override def show: String = m.method
  }

  case class FixedPath[T](s: String, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = FixedPath[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedPath[U] = copy(codec = c, info = i)
    override def show: String = s"/${UrlencodedData.encode(s)}"
  }

  case class PathCapture[T](name: Option[String], codec: Codec[String, T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = PathCapture[X]
    override private[tapir] type L = String
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[String, U, TextPlain], i: Info[U]): PathCapture[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"/[${name.getOrElse("")}]", codec.schema)

    def name(n: String): PathCapture[T] = copy(name = Some(n))
  }

  case class PathsCapture[T](codec: Codec[List[String], T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = PathsCapture[X]
    override private[tapir] type L = List[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[String], U, TextPlain], i: Info[U]): PathsCapture[U] = copy(codec = c, info = i)
    override def show = s"/..."
  }

  case class Query[T](name: String, codec: Codec[List[String], T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = Query[X]
    override private[tapir] type L = List[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[String], U, TextPlain], i: Info[U]): Query[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"?$name", codec.schema)
  }

  case class QueryParams[T](codec: Codec[sttp.model.QueryParams, T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = QueryParams[X]
    override private[tapir] type L = sttp.model.QueryParams
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[sttp.model.QueryParams, U, TextPlain], i: Info[U]): QueryParams[U] =
      copy(codec = c, info = i)
    override def show: String = s"?..."
  }

  case class Cookie[T](name: String, codec: Codec[Option[String], T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = Cookie[X]
    override private[tapir] type L = Option[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Option[String], U, TextPlain], i: Info[U]): Cookie[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"{cookie $name}", codec.schema)
  }

  case class ExtractFromRequest[T](codec: Codec[ServerRequest, T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = ExtractFromRequest[X]
    override private[tapir] type L = ServerRequest
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[ServerRequest, U, TextPlain], i: Info[U]): ExtractFromRequest[U] =
      copy(codec = c, info = i)
    override def show: String = s"{data from request}"
  }

  //

  /** An input with authentication credentials metadata, used when generating documentation. */
  case class Auth[T, INFO <: AuthInfo](
      input: Single[T],
      securitySchemeName: Option[String],
      challenge: WWWAuthenticateChallenge,
      authInfo: INFO
  ) extends Single[T] {
    override private[tapir] type ThisType[X] = Auth[X, INFO]
    override def show: String = authInfo match {
      case AuthInfo.Http(scheme)       => s"auth($scheme http, via ${input.show})"
      case AuthInfo.ApiKey()           => s"auth(api key, via ${input.show})"
      case AuthInfo.OAuth2(_, _, _, _) => s"auth(oauth2, via ${input.show})"
      case AuthInfo.ScopedOAuth2(_, _) => s"auth(scoped oauth2, via ${input.show})"
      case _                           => throw new RuntimeException("Impossible, but the compiler complains.")
    }
    override def map[U](mapping: Mapping[T, U]): Auth[U, INFO] = copy(input = input.map(mapping))

    def securitySchemeName(name: String): Auth[T, INFO] = copy(securitySchemeName = Some(name))
    def challengeRealm(realm: String): Auth[T, INFO] = copy(challenge = challenge.realm(realm))
    def requiredScopes(requiredScopes: Seq[String])(implicit ev: INFO =:= AuthInfo.OAuth2): Auth[T, AuthInfo.ScopedOAuth2] =
      copy(authInfo = authInfo.requiredScopes(requiredScopes))
  }

  sealed trait AuthInfo
  object AuthInfo {
    case class Http(scheme: String) extends AuthInfo {
      def scheme(s: String): Http = copy(scheme = s)
    }
    case class ApiKey() extends AuthInfo
    case class OAuth2(
        authorizationUrl: Option[String],
        tokenUrl: Option[String],
        scopes: ListMap[String, String],
        refreshUrl: Option[String]
    ) extends AuthInfo {
      def requiredScopes(requiredScopes: Seq[String]): ScopedOAuth2 = ScopedOAuth2(this, requiredScopes)
    }
    case class ScopedOAuth2(oauth2: OAuth2, requiredScopes: Seq[String]) extends AuthInfo {
      require(requiredScopes.forall(oauth2.scopes.keySet.contains), "all requiredScopes have to be defined on outer Oauth2#scopes")
    }
  }

  //

  case class MappedPair[T, U, TU, V](input: Pair[T, U, TU], mapping: Mapping[TU, V]) extends Single[V] {
    override private[tapir] type ThisType[X] = MappedPair[T, U, TU, X]
    override def show: String = input.show
    override def map[W](m: Mapping[V, W]): MappedPair[T, U, TU, W] = copy[T, U, TU, W](input, mapping.map(m))
  }

  case class Pair[T, U, TU](
      left: EndpointInput[T],
      right: EndpointInput[U],
      private[tapir] val combine: CombineParams,
      private[tapir] val split: SplitParams
  ) extends EndpointInput[TU]
      with EndpointTransput.Pair[TU] {
    override private[tapir] type ThisType[X] = EndpointInput[X]
    override def map[V](m: Mapping[TU, V]): EndpointInput[V] = MappedPair[T, U, TU, V](this, m)
  }
}

sealed trait EndpointOutput[T] extends EndpointTransput[T] {
  private[tapir] type ThisType[X] <: EndpointOutput[X]

  def and[J, IJ](other: EndpointOutput[J])(implicit concat: ParamConcat.Aux[T, J, IJ]): EndpointOutput[IJ] =
    EndpointOutput.Pair(this, other, mkCombine(concat), mkSplit(concat))
}

object EndpointOutput extends EndpointOutputMacros {
  sealed trait Single[T] extends EndpointOutput[T]
  sealed trait Basic[T] extends Single[T] with EndpointTransput.Basic[T]
  sealed trait Atom[T] extends Basic[T] with EndpointTransput.Atom[T]

  //

  case class StatusCode[T](
      documentedCodes: Map[sttp.model.StatusCode, Info[Unit]],
      codec: Codec[sttp.model.StatusCode, T, TextPlain],
      info: Info[T]
  ) extends Atom[T] {
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

  case class FixedStatusCode[T](statusCode: sttp.model.StatusCode, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = FixedStatusCode[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedStatusCode[U] = copy(codec = c, info = i)
    override def show: String = s"status code ($statusCode)"
  }

  case class WebSocketBodyWrapper[PIPE_REQ_RESP, T](wrapped: WebSocketBodyOutput[PIPE_REQ_RESP, _, _, T, _]) extends Atom[T] {
    override private[tapir] type ThisType[X] = WebSocketBodyWrapper[PIPE_REQ_RESP, X]
    override private[tapir] type L = PIPE_REQ_RESP
    override private[tapir] type CF = CodecFormat
    override private[tapir] def copyWith[U](c: Codec[PIPE_REQ_RESP, U, CodecFormat], i: Info[U]): WebSocketBodyWrapper[PIPE_REQ_RESP, U] =
      copy(
        wrapped.copyWith(c, i)
      )

    override def codec: Codec[PIPE_REQ_RESP, T, CodecFormat] = wrapped.codec
    override def info: Info[T] = wrapped.info

    override def show: String = wrapped.show
  }

  /** Specifies one possible `output`.
    *
    * When encoding to a response, this output is used if:
    *   1. `appliesTo` applied to the output value (as returned by the server logic) returns `true`.
    *   1. when a fixed content type specified by the output matches the request's `Accept` header
    *
    * When decoding from a response, this output is used if it decodes successfully.
    *
    * The `appliesTo` function should determine, whether a runtime value matches the type `O`. This check cannot be in general done by
    * checking the run-time class of the value, due to type erasure (if `O` has type parameters).
    */
  case class OneOfVariant[O] private[tapir] (
      output: EndpointOutput[O],
      appliesTo: Any => Boolean
  )

  case class OneOf[O, T](variants: List[OneOfVariant[_ <: O]], mapping: Mapping[O, T]) extends Single[T] {
    override private[tapir] type ThisType[X] = OneOf[O, X]
    override def map[U](_mapping: Mapping[T, U]): OneOf[O, U] = copy[O, U](mapping = mapping.map(_mapping))
    override def show: String = showOneOf(variants.map(_.output.show))
  }

  //

  case class Void[T]() extends EndpointOutput[T] {
    override private[tapir] type ThisType[X] = Void[X]
    override def show: String = "void"
    override def map[U](mapping: Mapping[T, U]): Void[U] = Void()

    override def and[U, TU](other: EndpointOutput[U])(implicit concat: ParamConcat.Aux[T, U, TU]): EndpointOutput[TU] =
      other.asInstanceOf[EndpointOutput[TU]]
  }

  //

  case class MappedPair[T, U, TU, V](output: Pair[T, U, TU], mapping: Mapping[TU, V]) extends Single[V] {
    override private[tapir] type ThisType[X] = MappedPair[T, U, TU, X]
    override def show: String = output.show
    override def map[W](m: Mapping[V, W]): MappedPair[T, U, TU, W] = copy[T, U, TU, W](output, mapping.map(m))
  }

  case class Pair[T, U, TU](
      left: EndpointOutput[T],
      right: EndpointOutput[U],
      private[tapir] val combine: CombineParams,
      private[tapir] val split: SplitParams
  ) extends EndpointOutput[TU]
      with EndpointTransput.Pair[TU] {
    override private[tapir] type ThisType[X] = EndpointOutput[X]
    override def map[V](m: Mapping[TU, V]): EndpointOutput[V] = MappedPair[T, U, TU, V](this, m)
  }
}

sealed trait EndpointIO[T] extends EndpointInput[T] with EndpointOutput[T] {
  private[tapir] type ThisType[X] <: EndpointIO[X]

  def and[J, IJ](other: EndpointIO[J])(implicit concat: ParamConcat.Aux[T, J, IJ]): EndpointIO[IJ] =
    EndpointIO.Pair(this, other, mkCombine(concat), mkSplit(concat))
}

object EndpointIO {
  sealed trait Single[I] extends EndpointIO[I] with EndpointInput.Single[I] with EndpointOutput.Single[I] {
    private[tapir] type ThisType[X] <: EndpointIO.Single[X]
  }
  sealed trait Basic[I] extends Single[I] with EndpointInput.Basic[I] with EndpointOutput.Basic[I]
  sealed trait Atom[I] extends Basic[I] with EndpointInput.Atom[I] with EndpointOutput.Atom[I]

  case class Body[R, T](bodyType: RawBodyType[R], codec: Codec[R, T, CodecFormat], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = Body[R, X]
    override private[tapir] type L = R
    override private[tapir] type CF = CodecFormat
    override private[tapir] def copyWith[U](c: Codec[R, U, CodecFormat], i: Info[U]): Body[R, U] = copy(codec = c, info = i)
    override def show: String = {
      val charset = bodyType.asInstanceOf[RawBodyType[_]] match {
        case RawBodyType.StringBody(charset) => s" (${charset.toString})"
        case _                               => ""
      }
      val format = codec.format.mediaType
      addValidatorShow(s"{body as $format$charset}", codec.schema)
    }
  }

  case class StreamBodyWrapper[BS, T](wrapped: StreamBodyIO[BS, T, _]) extends Atom[T] {
    override private[tapir] type ThisType[X] = StreamBodyWrapper[BS, X]
    override private[tapir] type L = BS
    override private[tapir] type CF = CodecFormat
    override private[tapir] def copyWith[U](c: Codec[BS, U, CodecFormat], i: Info[U]): StreamBodyWrapper[BS, U] = copy(
      wrapped.copyWith(c, i)
    )

    override def codec: Codec[BS, T, CodecFormat] = wrapped.codec
    override def info: Info[T] = wrapped.info

    override def show: String = wrapped.show
  }

  case class OneOfBodyVariant[O](range: ContentTypeRange, body: Body[_, O])
  case class OneOfBody[O, T](variants: List[OneOfBodyVariant[O]], mapping: Mapping[O, T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = OneOfBody[O, X]
    override def show: String = showOneOf(variants.map { variant =>
      val prefix =
        if ((ContentTypeRange.exactNoCharset(variant.body.codec.format.mediaType)) == variant.range) "" else s"${variant.range} -> "
      prefix + variant.body.show
    })
    override def map[U](m: Mapping[T, U]): OneOfBody[O, U] = copy[O, U](mapping = mapping.map(m))
  }

  case class FixedHeader[T](h: sttp.model.Header, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = FixedHeader[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedHeader[U] = copy(codec = c, info = i)
    override def show = s"{header ${h.name}: ${h.value}}"
  }

  case class Header[T](name: String, codec: Codec[List[String], T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = Header[X]
    override private[tapir] type L = List[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[String], U, TextPlain], i: Info[U]): Header[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"{header $name}", codec.schema)
  }

  case class Headers[T](codec: Codec[List[sttp.model.Header], T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = Headers[X]
    override private[tapir] type L = List[sttp.model.Header]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[sttp.model.Header], U, TextPlain], i: Info[U]): Headers[U] =
      copy(codec = c, info = i)
    override def show = "{multiple headers}"
  }

  case class Empty[T](codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Atom[T] {
    override private[tapir] type ThisType[X] = Empty[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): Empty[U] = copy(codec = c, info = i)
    override def show = "-"
  }

  //

  case class MappedPair[T, U, TU, V](io: Pair[T, U, TU], mapping: Mapping[TU, V]) extends Single[V] {
    override private[tapir] type ThisType[X] = MappedPair[T, U, TU, X]
    override def show: String = io.show
    override def map[W](m: Mapping[V, W]): MappedPair[T, U, TU, W] = copy[T, U, TU, W](io, mapping.map(m))
  }

  case class Pair[T, U, TU](
      left: EndpointIO[T],
      right: EndpointIO[U],
      private[tapir] val combine: CombineParams,
      private[tapir] val split: SplitParams
  ) extends EndpointIO[TU]
      with EndpointTransput.Pair[TU] {
    override private[tapir] type ThisType[X] = EndpointIO[X]
    override def map[V](m: Mapping[TU, V]): EndpointIO[V] = MappedPair[T, U, TU, V](this, m)
  }

  //

  case class Example[+T](value: T, name: Option[String], summary: Option[String]) {
    def map[B](f: T => B): Example[B] = copy(value = f(value))
  }

  object Example {
    def of[T](t: T, name: Option[String] = None, summary: Option[String] = None): Example[T] = Example(t, name, summary)
  }

  case class Info[T](
      description: Option[String],
      examples: List[Example[T]],
      deprecated: Boolean,
      attributes: AttributeMap
  ) {
    def description(d: String): Info[T] = copy(description = Some(d))
    def example: Option[T] = examples.headOption.map(_.value)
    def example(t: T): Info[T] = example(Example.of(t))
    def example(example: Example[T]): Info[T] = copy(examples = examples :+ example)
    def examples(ts: List[Example[T]]): Info[T] = copy(examples = ts)
    def deprecated(d: Boolean): Info[T] = copy(deprecated = d)
    def attribute[A](k: AttributeKey[A]): Option[A] = attributes.get(k)
    def attribute[A](k: AttributeKey[A], v: A): Info[T] = copy(attributes = attributes.put(k, v))

    def map[U](codec: Mapping[T, U]): Info[U] =
      Info(
        description,
        examples.map(e => e.copy(value = codec.decode(e.value))).collect { case Example(DecodeResult.Value(ee), name, summary) =>
          Example(ee, name, summary)
        },
        deprecated,
        attributes
      )
  }
  object Info {
    def empty[T]: Info[T] = Info[T](None, Nil, deprecated = false, attributes = AttributeMap.Empty)
  }

  /** Annotations which are used by [[EndpointInput.derived]] and [[EndpointOutput.derived]] to specify how a case class maps to an endpoint
    * input/output.
    */
  object annotations {
    sealed trait EndpointInputAnnotation extends StaticAnnotation
    sealed trait EndpointOutputAnnotation extends StaticAnnotation

    class path extends EndpointInputAnnotation
    class query(val name: String = "") extends EndpointInputAnnotation
    class params extends EndpointInputAnnotation
    class header(val name: String = "") extends EndpointInputAnnotation with EndpointOutputAnnotation
    class headers extends EndpointInputAnnotation with EndpointOutputAnnotation
    class cookie(val name: String = "") extends EndpointInputAnnotation
    class cookies extends EndpointInputAnnotation with EndpointOutputAnnotation
    class setCookie(val name: String = "") extends EndpointOutputAnnotation
    class setCookies extends EndpointOutputAnnotation
    class statusCode extends EndpointOutputAnnotation
    class body[R, CF <: CodecFormat](val bodyType: RawBodyType[R], val cf: CF) extends EndpointInputAnnotation with EndpointOutputAnnotation
    class jsonbody extends body(StringBody(StandardCharsets.UTF_8), CodecFormat.Json())
    class xmlbody extends body(StringBody(StandardCharsets.UTF_8), CodecFormat.Xml())
    class apikey(val challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge("ApiKey")) extends StaticAnnotation
    class basic(val challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.basic) extends StaticAnnotation
    class bearer(val challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.bearer) extends StaticAnnotation
    class securitySchemeName(val name: String) extends StaticAnnotation

    /** A class-level annotation, specifies the path to the endpoint. To capture segments of the path, surround the segment's name with
      * `{...}` (curly braces), and reference the name using [[annotations.path]].
      */
    class endpointInput(val path: String = "") extends EndpointInputAnnotation

    /** Specifies the example value of the endpoint input/output. Note that this is distinct from [[Schema.annotations.encodedExample]],
      * which sets the example on the schema associated with the input/output.
      */
    class example(val example: Any) extends EndpointInputAnnotation with EndpointOutputAnnotation

    /** Specifies the description of the endpoint input/output. Note that this is distinct from [[Schema.annotations.description]], which
      * sets the description on the schema associated with the input/output.
      */
    class description(val text: String) extends EndpointInputAnnotation with EndpointOutputAnnotation
  }
}

/*
Streaming body is a special kind of input, as it influences the 4th type parameter of `Endpoint`. Other inputs
(`EndpointInput`s and `EndpointIO`s aren't parametrised with the type of streams that they use (to make them simpler),
so we need to pass the streaming information directly between the streaming body input and the endpoint.

That's why the streaming body input is a separate trait, unrelated to `EndpointInput`: it can't be combined with
other inputs, and the `Endpoint.in(EndpointInput)` method can't be used to add a streaming body. Instead, there's an
overloaded variant `Endpoint.in(StreamBody)`, which takes into account the streaming type.

Internally, the streaming body is converted into a wrapper `EndpointIO`, which "forgets" about the streaming
information. The `EndpointIO.StreamBodyWrapper` should only be used internally, not by the end user: there's no
factory method in `Tapir` which would directly create an instance of it.

BS == streams.BinaryStream, but we can't express this using dependent types here.
 */
case class StreamBodyIO[BS, T, S](
    streams: Streams[S],
    codec: Codec[BS, T, CodecFormat],
    info: Info[T],
    charset: Option[Charset],
    encodedExamples: List[Example[Any]]
) extends EndpointTransput.Atom[T] {
  override private[tapir] type ThisType[X] = StreamBodyIO[BS, X, S]
  override private[tapir] type L = BS
  override private[tapir] type CF = CodecFormat
  override private[tapir] def copyWith[U](c: Codec[BS, U, CodecFormat], i: Info[U]) = copy(codec = c, info = i)

  private[tapir] def toEndpointIO: EndpointIO.StreamBodyWrapper[BS, T] = EndpointIO.StreamBodyWrapper(this)

  /** Add an example of a "deserialized" stream value. This should be given in an encoded form, e.g. in case of json - as a [[String]], as
    * the stream body doesn't have access to the codec that will be later used for deserialization.
    */
  def encodedExample(e: Any): ThisType[T] = copy(encodedExamples = encodedExamples ++ List(Example.of(e)))
  def encodedExample(example: Example[Any]): ThisType[T] = copy(encodedExamples = encodedExamples ++ List(example))
  def encodedExample(examples: List[Example[Any]]): ThisType[T] = copy(encodedExamples = encodedExamples ++ examples)

  override def show: String = "{body as stream}"
}

/*
Same rationale as for StreamBodyIO applies.

P == streams.Pipe, but we can't express this using dependent types here.
 */
case class WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S](
    streams: Streams[S],
    requests: Codec[WebSocketFrame, REQ, CodecFormat],
    responses: Codec[WebSocketFrame, RESP, CodecFormat],
    codec: Codec[PIPE_REQ_RESP, T, CodecFormat],
    info: Info[T],
    requestsInfo: Info[REQ],
    responsesInfo: Info[RESP],
    concatenateFragmentedFrames: Boolean,
    ignorePong: Boolean,
    autoPongOnPing: Boolean,
    decodeCloseRequests: Boolean,
    decodeCloseResponses: Boolean,
    autoPing: Option[(FiniteDuration, WebSocketFrame.Ping)]
) extends EndpointTransput.Atom[T] {
  override private[tapir] type ThisType[X] = WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, X, S]
  override private[tapir] type L = PIPE_REQ_RESP
  override private[tapir] type CF = CodecFormat
  override private[tapir] def copyWith[U](c: Codec[PIPE_REQ_RESP, U, CodecFormat], i: Info[U]) = copy(codec = c, info = i)

  private[tapir] def toEndpointOutput: EndpointOutput.WebSocketBodyWrapper[PIPE_REQ_RESP, T] = EndpointOutput.WebSocketBodyWrapper(this)

  def requestsSchema(s: Schema[REQ]): ThisType[T] = copy(requests = requests.schema(s))
  def requestsSchema(s: Option[Schema[REQ]]): ThisType[T] = copy(requests = requests.schema(s))
  def requestsSchema(modify: Schema[REQ] => Schema[REQ]): ThisType[T] = copy(requests = requests.schema(modify))

  def responsesSchema(s: Schema[RESP]): ThisType[T] = copy(responses = responses.schema(s))
  def responsesSchema(s: Option[Schema[RESP]]): ThisType[T] = copy(responses = responses.schema(s))
  def responsesSchema(modify: Schema[RESP] => Schema[RESP]): ThisType[T] = copy(responses = responses.schema(modify))

  def requestsDescription(d: String): ThisType[T] = copy(requestsInfo = requestsInfo.description(d))
  def requestsExample(e: REQ): ThisType[T] = copy(requestsInfo = requestsInfo.example(e))
  def requestsExamples(examples: List[REQ]): ThisType[T] = copy(requestsInfo = requestsInfo.examples(examples.map(Example(_, None, None))))

  def responsesDescription(d: String): ThisType[T] = copy(responsesInfo = responsesInfo.description(d))
  def responsesExample(e: RESP): ThisType[T] = copy(responsesInfo = responsesInfo.example(e))
  def responsesExamples(examples: List[RESP]): ThisType[T] =
    copy(responsesInfo = responsesInfo.examples(examples.map(Example(_, None, None))))

  /** @param c
    *   If `true`, fragmented frames will be concatenated, and the data frames that the `requests` & `responses` codecs decode will always
    *   have `finalFragment` set to `true`. Note that only some interpreters expose fragmented frames.
    */
  def concatenateFragmentedFrames(c: Boolean): WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S] =
    this.copy(concatenateFragmentedFrames = c)

  /** Note: some interpreters ignore this setting.
    * @param i
    *   If `true`, [[WebSocketFrame.Pong]] frames will be ignored and won't be passed to the codecs for decoding. Note that only some
    *   interpreters expose ping-pong frames.
    */
  def ignorePong(i: Boolean): WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S] = this.copy(ignorePong = i)

  /** Note: some interpreters ignore this setting.
    * @param a
    *   If `true`, [[WebSocketFrame.Ping]] frames will cause a matching [[WebSocketFrame.Pong]] frame to be sent back, and won't be passed
    *   to codecs for decoding. Note that only some interpreters expose ping-pong frames.
    */
  def autoPongOnPing(a: Boolean): WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S] = this.copy(autoPongOnPing = a)

  /** Note: some interpreters ignore this setting.
    * @param d
    *   If `true`, [[WebSocketFrame.Close]] frames will be passed to the request codec for decoding (in server interpreters).
    */
  def decodeCloseRequests(d: Boolean): WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S] = this.copy(decodeCloseRequests = d)

  /** Note: some interpreters ignore this setting.
    * @param d
    *   If `true`, [[WebSocketFrame.Close]] frames will be passed to the response codec for decoding (in client interpreters).
    */
  def decodeCloseResponses(d: Boolean): WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S] = this.copy(decodeCloseResponses = d)

  /** Note: some interpreters ignore this setting.
    * @param p
    *   If `Some`, send the given `Ping` frame at the given interval. If `None`, do not automatically send pings.
    */
  def autoPing(p: Option[(FiniteDuration, WebSocketFrame.Ping)]): WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S] =
    this.copy(autoPing = p)

  override def show: String = "{body as web socket}"
}
