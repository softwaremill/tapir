package sttp.tapir

import java.nio.charset.Charset

import sttp.model.{Method, MultiQueryParams}
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.EndpointIO.Info
import sttp.tapir.internal._
import sttp.tapir.model.ServerRequest
import sttp.tapir.typelevel.{FnComponents, ParamConcat}

import scala.collection.immutable.ListMap

sealed trait EndpointInput[T] extends CodecMappable[T] {
  private[tapir] type ThisType[X] <: EndpointInput[X]

  def and[U, TU](other: EndpointInput[U])(implicit ts: ParamConcat.Aux[T, U, TU]): EndpointInput[TU]
  def /[U, TU](other: EndpointInput[U])(implicit ts: ParamConcat.Aux[T, U, TU]): EndpointInput[TU] = and(other)

  def show: String
}

object EndpointInput {
  sealed trait Single[T] extends EndpointInput[T] {
    private[tapir] type ThisType[X] <: EndpointInput.Single[X]

    def and[U, TU](other: EndpointInput[U])(implicit ts: ParamConcat.Aux[T, U, TU]): EndpointInput[TU] =
      other match {
        case s: Single[_]          => Tuple(Vector(this, s))
        case Tuple(inputs)         => Tuple(this +: inputs)
        case EndpointIO.Tuple(ios) => Tuple(this +: ios)
      }
  }

  sealed trait Basic[T] extends Single[T]

  case class FixedMethod[T](m: Method, codec: Codec[Unit, T, TextPlain]) extends Basic[T] {
    override private[tapir] type ThisType[X] = FixedMethod[X]
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): FixedMethod[U] = copy(codec = codec.map(nextCodec))
    override def show: String = m.method
  }

  case class FixedPath[T](s: String, codec: Codec[Unit, T, TextPlain]) extends Basic[T] {
    override private[tapir] type ThisType[X] = FixedPath[X]
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): FixedPath[U] = copy(codec = codec.map(nextCodec))
    override def show = s"/$s"
  }

  case class PathCapture[T](name: Option[String], codec: Codec[String, T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = PathCapture[X]
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): PathCapture[U] = copy(codec = codec.map(nextCodec), info = info.map(nextCodec))
    override def show: String = addValidatorShow(s"/[${name.getOrElse("")}]", codec.validator)

    // TODO: move to Basic? (all meta-methods)
    def name(n: String): PathCapture[T] = copy(name = Some(n))
    def description(d: String): PathCapture[T] = copy(info = info.description(d))
    def example(t: T): PathCapture[T] = copy(info = info.example(t))
    def example(example: Example[T]): PathCapture[T] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): PathCapture[T] = copy(info = info.examples(examples))
    def validate(v: Validator[T]): PathCapture[T] = copy(codec = codec.validate(v))
  }

  case class PathsCapture[T](codec: Codec[List[String], T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = PathsCapture[X]
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): PathsCapture[U] = copy(codec = codec.map(nextCodec), info = info.map(nextCodec))
    override def show = s"/..."

    def description(d: String): PathsCapture[T] = copy(info = info.description(d))
    def example(t: T): PathsCapture[T] = copy(info = info.example(t))
    def example(example: Example[T]): PathsCapture = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): PathsCapture = copy(info = info.examples(examples))
    def deprecated(): PathsCapture[T] = copy(info = info.deprecated(true))
    def validate(v: Validator[T]): PathsCapture[T] = copy(codec = codec.validate(v))
  }

  case class Query[T](name: String, codec: Codec[List[String], T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = Query[X]
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): Query[U] = copy(codec = codec.map(nextCodec), info = info.map(nextCodec))
    override def show: String = addValidatorShow(s"?$name", codec.validator)

    def description(d: String): Query[T] = copy(info = info.description(d))
    def example(t: T): Query[T] = copy(info = info.example(t))
    def example(example: Example[T]): Query[T] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): Query[T] = copy(info = info.examples(examples))
    def deprecated(): Query[T] = copy(info = info.deprecated(true))
    def validate(v: Validator[T]): Query[T] = copy(codec = codec.validate(v))
  }

  case class QueryParams[T](codec: Codec[MultiQueryParams, T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = QueryParams[X]
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): QueryParams[U] = copy(codec = codec.map(nextCodec), info = info.map(nextCodec))
    override def show: String = s"?..."

    def description(d: String): QueryParams[T] = copy(info = info.description(d))
    def example(t: T): QueryParams[T] = copy(info = info.example(t))
    def example(example: Example[T]): PathsCapture = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): PathsCapture = copy(info = info.examples(examples))
    def deprecated(): QueryParams[T] = copy(info = info.deprecated(true))
    def validate(v: Validator[T]): QueryParams[T] = copy(codec = codec.validate(v))
  }

  case class Cookie[T](name: String, codec: Codec[Option[String], T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = Cookie[X]
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): Cookie[U] = copy(codec = codec.map(nextCodec), info = info.map(nextCodec))
    override def show: String = addValidatorShow(s"{cookie $name}", codec.validator)

    def description(d: String): Cookie[T] = copy(info = info.description(d))
    def example(t: T): Cookie[T] = copy(info = info.example(t))
    def example(example: Example[T]): Cookie[T] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): Cookie[T] = copy(info = info.examples(examples))
    def deprecated(): Cookie[T] = copy(info = info.deprecated(true))
    def validate(v: Validator[T]): Cookie[T] = copy(codec = codec.validate(v))
  }

  case class ExtractFromRequest[T](codec: Codec[ServerRequest, T, TextPlain]) extends Basic[T] {
    override private[tapir] type ThisType[X] = ExtractFromRequest[X]
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): ExtractFromRequest[U] = copy(codec = codec.map(nextCodec))
    override def show: String = s"{data from request}"

    // TODO: validator etc.?
  }

  //

  trait Auth[T] extends EndpointInput.Single[T] {
    def input: EndpointInput.Single[T]
  }

  object Auth {
    case class ApiKey[T](input: EndpointInput.Single[T]) extends Auth[T] {
      override private[tapir] type ThisType[X] = ApiKey[X]
      override def show: String = s"auth(api key, via ${input.show})"
      override def map[U: IsUnit](nextCodec: Codec[T, U, _]): ApiKey[U] = copy(input = input.map(nextCodec))
    }
    case class Http[T](scheme: String, input: EndpointInput.Single[T]) extends Auth[T] {
      override private[tapir] type ThisType[X] = Http[X]
      override def show: String = s"auth($scheme http, via ${input.show})"
      override def map[U: IsUnit](nextCodec: Codec[T, U, _]): Http[U] = copy(input = input.map(nextCodec))
    }
    case class Oauth2[T](
        authorizationUrl: String,
        tokenUrl: String,
        scopes: ListMap[String, String],
        refreshUrl: Option[String] = None,
        input: EndpointInput.Single[T]
    ) extends Auth[T] {
      override private[tapir] type ThisType[X] = Oauth2[X]
      override def show: String = s"auth(oauth2, via ${input.show})"
      override def map[U: IsUnit](nextCodec: Codec[T, U, _]): Oauth2[U] = copy(input = input.map(nextCodec))

      def requiredScopes(requiredScopes: Seq[String]): ScopedOauth2[T] = ScopedOauth2(this, requiredScopes)
    }

    case class ScopedOauth2[T](oauth2: Oauth2[T], requiredScopes: Seq[String]) extends Auth[T] {
      require(requiredScopes.forall(oauth2.scopes.keySet.contains), "all requiredScopes have to be defined on outer Oauth2#scopes")

      override private[tapir] type ThisType[X] = ScopedOauth2[X]
      override def show: String = s"scoped(${oauth2.show})"
      override def map[U: IsUnit](nextCodec: Codec[T, U, _]): ScopedOauth2[U] = copy(oauth2 = oauth2.map(nextCodec))

      override def input: Single[T] = oauth2.input
    }
  }

  //

  case class MappedTuple[TUPLE, T](input: Tuple[TUPLE], codec: Codec[TUPLE, T, _ <: CodecFormat]) extends EndpointInput.Single[T] {
    override private[tapir] type ThisType[X] = MappedTuple[TUPLE, X]
    override def show: String = input.show
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): MappedTuple[TUPLE, U] = copy[TUPLE, U](input, codec.map(nextCodec))
  }

  case class Tuple[TUPLE](inputs: Vector[Single[_]]) extends EndpointInput[TUPLE] {
    override private[tapir] type ThisType[X] = EndpointInput[X]
    override def show: String = if (inputs.isEmpty) "-" else inputs.map(_.show).mkString(" ")
    override def map[U: IsUnit](nextCodec: Codec[TUPLE, U, _]): EndpointInput[U] =
      MappedTuple[TUPLE, TUPLE](this, Codec.idNoMeta).map(nextCodec)

    override def and[U, TU](other: EndpointInput[U])(implicit ts: ParamConcat.Aux[TUPLE, U, TU]): EndpointInput.Tuple[TU] =
      other match {
        case s: Single[_]        => Tuple(inputs :+ s)
        case Tuple(m)            => Tuple(inputs ++ m)
        case EndpointIO.Tuple(m) => Tuple(inputs ++ m)
      }
  }
}

sealed trait EndpointOutput[T] extends CodecMappable[T] {
  private[tapir] type ThisType[X] <: EndpointOutput[X]

  def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[T, J, IJ]): EndpointOutput[IJ]

  def show: String
}

object EndpointOutput {
  sealed trait Single[T] extends EndpointOutput[T] {
    private[tapir] def _codec: Codec[_, T, _]

    def and[U, TU](other: EndpointOutput[U])(implicit ts: ParamConcat.Aux[T, U, TU]): EndpointOutput[TU] =
      other match {
        case s: Single[_]          => Tuple(Vector(this, s))
        case Void()                => this.asInstanceOf[EndpointOutput[TU]]
        case Tuple(outputs)        => Tuple(this +: outputs)
        case EndpointIO.Tuple(ios) => Tuple(this +: ios)
      }
  }

  sealed trait Basic[I] extends Single[I]

  //

  case class StatusCode[T](documentedCodes: Map[sttp.model.StatusCode, Info[Unit]], codec: Codec[sttp.model.StatusCode, T, TextPlain])
      extends Basic[T] {
    override private[tapir] type ThisType[X] = StatusCode[X]
    override private[tapir] def _codec: Codec[_, T, _] = codec
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): StatusCode[U] = copy(codec = codec.map(nextCodec))
    override def show: String = s"status code - possible codes ($documentedCodes)"

    def description(code: sttp.model.StatusCode, d: String): StatusCode[T] = {
      val updatedCodes = documentedCodes + (code -> Info.empty[Unit].description(d))
      copy(documentedCodes = updatedCodes)
    }
  }

  //

  case class FixedStatusCode[T](statusCode: sttp.model.StatusCode, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = FixedStatusCode[X]
    override private[tapir] def _codec: Codec[_, T, _] = codec
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): FixedStatusCode[U] =
      copy(codec = codec.map(nextCodec), info = info.map(nextCodec))
    override def show: String = s"status code ($statusCode)"

    def description(d: String): FixedStatusCode[T] = copy(info = info.description(d))
  }

  /**
    * Specifies that for `statusCode`, the given `output` should be used.
    *
    * The `appliesTo` function should determine, whether a runtime value matches the type `O`.
    * This check cannot be in general done by checking the run-time class of the value, due to type erasure (if `O` has
    * type parameters).
    */
  case class StatusMapping[O] private[tapir] (
      statusCode: Option[sttp.model.StatusCode],
      output: EndpointOutput[O],
      appliesTo: Any => Boolean
  )

  case class OneOf[O, T](mappings: Seq[StatusMapping[_ <: O]], codec: Codec[O, T, _ <: CodecFormat]) extends Single[T] {
    override private[tapir] type ThisType[X] = OneOf[O, X]
    override private[tapir] def _codec: Codec[_, T, _] = codec
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): OneOf[O, U] = copy[O, U](codec = codec.map(nextCodec))
    override def show: String = s"status one of(${mappings.map(_.output.show).mkString("|")})"
  }

  //

  case class Void[T]() extends EndpointOutput[T] {
    override private[tapir] type ThisType[X] = Void[X]
    override def show: String = "void"
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): Void[U] = Void()

    override def and[U, TU](other: EndpointOutput[U])(implicit ts: ParamConcat.Aux[T, U, TU]): EndpointOutput[TU] =
      other.asInstanceOf[EndpointOutput[TU]]
  }

  //

  case class MappedTuple[TUPLE, T](output: Tuple[TUPLE], codec: Codec[TUPLE, T, _ <: CodecFormat]) extends EndpointOutput.Single[T] {
    override private[tapir] type ThisType[X] = MappedTuple[TUPLE, X]
    override private[tapir] def _codec: Codec[_, T, _] = codec
    override def show: String = output.show
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): MappedTuple[TUPLE, U] = copy[TUPLE, U](output, codec.map(nextCodec))
  }

  case class Tuple[TUPLE](outputs: Vector[Single[_]]) extends EndpointOutput[TUPLE] {
    override private[tapir] type ThisType[X] = EndpointOutput[X]
    override def show: String = if (outputs.isEmpty) "-" else outputs.map(_.show).mkString(" ")
    override def map[U: IsUnit](nextCodec: Codec[TUPLE, U, _]): EndpointOutput[U] =
      MappedTuple[TUPLE, TUPLE](this, Codec.idNoMeta).map(nextCodec)

    override def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[TUPLE, J, IJ]): EndpointOutput.Tuple[IJ] =
      other match {
        case s: Single[_]        => Tuple(outputs :+ s)
        case Void()              => this.asInstanceOf[EndpointOutput.Tuple[IJ]]
        case Tuple(m)            => Tuple(outputs ++ m)
        case EndpointIO.Tuple(m) => Tuple(outputs ++ m)
      }
  }
}

sealed trait EndpointIO[T] extends EndpointInput[T] with EndpointOutput[T] {
  private[tapir] type ThisType[X] <: EndpointInput[X] with EndpointOutput[X]

  def and[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[T, J, IJ]): EndpointOutput[IJ]

  def show: String
}

object EndpointIO {
  sealed trait Single[I] extends EndpointIO[I] with EndpointInput.Single[I] with EndpointOutput.Single[I] {
    private[tapir] type ThisType[X] <: EndpointIO.Single[X]

    def and[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointIO[IJ] =
      other match {
        case s: Single[_]   => Tuple(Vector(this, s))
        case Tuple(outputs) => Tuple(this +: outputs)
      }
  }

  // TODO add methods to set schema/format?
  sealed trait Basic[I] extends Single[I] with EndpointInput.Basic[I] with EndpointOutput.Basic[I]

  case class Body[R, T](bodyType: RawBodyType[R], codec: Codec[R, T, _ <: CodecFormat], info: Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = Body[R, X]
    override private[tapir] def _codec: Codec[_, T, _] = codec
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): Body[R, U] = copy(codec = codec.map(nextCodec), info = info.map(nextCodec))
    override def show: String = {
      val charset = bodyType.asInstanceOf[RawBodyType[_]] match {
        case RawBodyType.StringBody(charset) => s" (${charset.toString})"
        case _                               => ""
      }
      val format = codec.format.map(_.mediaType.toString()).getOrElse("?")
      addValidatorShow(s"{body as $format$charset}", codec.validator)
    }

    def description(d: String): Body[R, T] = copy(info = info.description(d))
    def example(t: T): Body[R, T] = copy(info = info.example(t))
    def example(example: Example[T]): Body[R, T] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): Body[R, T] = copy(info = info.examples(examples))
    def validate(v: Validator[T]): Body[R, T] = copy(codec = codec.validate(v))
  }

  // TODO
  case class StreamBodyWrapper[S, T](wrapped: StreamingEndpointIO.Body[S, T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = StreamBodyWrapper[S, X]
    override private[tapir] def _codec: Codec[_, T, _] = wrapped.codec
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): StreamBodyWrapper[S, U] = copy(wrapped = wrapped.map(nextCodec))
    override def show = s"{body as stream}"
  }

  case class FixedHeader[T](h: sttp.model.Header, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = FixedHeader[X]
    override private[tapir] def _codec: Codec[_, T, _] = codec
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): FixedHeader[U] = copy(codec = codec.map(nextCodec), info = info.map(nextCodec))
    override def show = s"{header ${h.name}: ${h.value}}"

    def description(d: String): FixedHeader[T] = copy(info = info.description(d))
    def deprecated(): FixedHeader[T] = copy(info = info.deprecated(true))
  }

  case class Header[T](name: String, codec: Codec[List[String], T, TextPlain], info: Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = Header[X]
    override private[tapir] def _codec: Codec[_, T, _] = codec
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): Header[U] = copy(codec = codec.map(nextCodec), info = info.map(nextCodec))
    override def show: String = addValidatorShow(s"{header $name}", codec.validator)

    def description(d: String): Header[T] = copy(info = info.description(d))
    def example(t: T): Header[T] = copy(info = info.example(t))
    def example(example: Example[T]): Header[T] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): Header[T] = copy(info = info.examples(examples))
    def deprecated(): Header[T] = copy(info = info.deprecated(true))
    def validate(v: Validator[T]): Header[T] = copy(codec = codec.validate(v))
  }

  case class Headers[T](codec: Codec[List[sttp.model.Header], T, TextPlain], info: Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = Headers[X]
    override private[tapir] def _codec: Codec[_, T, _] = codec
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): Headers[U] = copy(codec = codec.map(nextCodec), info = info.map(nextCodec))
    override def show = s"{multiple headers}"

    def description(d: String): Headers[T] = copy(info = info.description(d))
    def example(t: T): Headers[T] = copy(info = info.example(t))
    def example(example: Example[T]): Headers[T] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): Headers[T] = copy(info = info.examples(examples))
  }

  //

  case class MappedTuple[TUPLE, T](io: Tuple[TUPLE], codec: Codec[TUPLE, T, _ <: CodecFormat]) extends EndpointIO.Single[T] {
    override private[tapir] type ThisType[X] = MappedTuple[TUPLE, X]
    override private[tapir] def _codec: Codec[_, T, _] = codec
    override def show: String = io.show
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): MappedTuple[TUPLE, U] = copy[TUPLE, U](io, codec.map(nextCodec))
  }

  case class Tuple[TUPLE](ios: Vector[Single[_]]) extends EndpointIO[TUPLE] {
    override private[tapir] type ThisType[X] = EndpointIO[X]
    override def show: String = if (ios.isEmpty) "-" else ios.map(_.show).mkString(" ")
    override def map[U: IsUnit](nextCodec: Codec[TUPLE, U, _]): EndpointIO[U] =
      MappedTuple[TUPLE, TUPLE](this, Codec.idNoMeta).map(nextCodec)

    override def and[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[TUPLE, J, IJ]): EndpointInput.Tuple[IJ] =
      other match {
        case s: EndpointInput.Single[_] => EndpointInput.Tuple((ios: Vector[EndpointInput.Single[_]]) :+ s)
        case EndpointInput.Tuple(m)     => EndpointInput.Tuple((ios: Vector[EndpointInput.Single[_]]) ++ m)
        case EndpointIO.Tuple(m)        => EndpointInput.Tuple((ios: Vector[EndpointInput.Single[_]]) ++ m)
      }
    override def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[TUPLE, J, IJ]): EndpointOutput.Tuple[IJ] =
      other match {
        case s: EndpointOutput.Single[_] => EndpointOutput.Tuple((ios: Vector[EndpointOutput.Single[_]]) :+ s)
        case EndpointOutput.Void()       => this.asInstanceOf[EndpointOutput.Tuple[IJ]]
        case EndpointOutput.Tuple(m)     => EndpointOutput.Tuple((ios: Vector[EndpointOutput.Single[_]]) ++ m)
        case EndpointIO.Tuple(m)         => EndpointOutput.Tuple((ios: Vector[EndpointOutput.Single[_]]) ++ m)
      }
    override def and[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[TUPLE, J, IJ]): Tuple[IJ] =
      other match {
        case s: Single[_] => Tuple(ios :+ s)
        case Tuple(m)     => Tuple(ios ++ m)
      }
  }

  //

  case class Example[+T](value: T, name: Option[String], summary: Option[String])

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

    def map[U](codec: Codec[T, U, _]): Info[U] =
      Info(description, example.map(e => codec.decode(e)).collect {
        case DecodeResult.Value(ee) => ee
      }, deprecated)
  }
  object Info {
    def empty[T]: Info[T] = Info[T](None, Nil, deprecated = false)
  }
}

/*
Streaming body is a special kind of input, as it influences the 4th type parameter of `Endpoint`. Other inputs
(`EndpointInput`s and `EndpointIO`s aren't parametrised with the type of streams that they use (to make them simpler),
so we need to pass the streaming information directly between the streaming body input and the endpoint.

That's why the streaming body input is a separate trait, unrelated to `EndpointInput`: it can't be combined with
other inputs, and the `Endpoint.in(EndpointInput)` method can't be used to add a streaming body. Instead, there's an
overloaded variant `Endpoint.in(StreamingEndpointIO)`, which takes into account the streaming type.

Internally, the streaming body is converted into a wrapper `EndpointIO`, which "forgets" about the streaming
information. The `EndpointIO.StreamBodyWrapper` should only be used internally, not by the end user: there's no
factory method in `Tapir` which would directly create an instance of it.
 */
sealed trait StreamingEndpointIO[T, +S] extends CodecMappable[T] {
  private[tapir] def toEndpointIO: EndpointIO[T]
}

object StreamingEndpointIO {
  case class Body[S, T](codec: Codec[S, T, _ <: CodecFormat], info: EndpointIO.Info[T], charset: Option[Charset])
      extends StreamingEndpointIO[T, S] {
    override private[tapir] type ThisType[X] = Body[S, X]
    override def map[U: IsUnit](nextCodec: Codec[T, U, _]): Body[S, U] = copy(codec = codec.map(nextCodec), info = info.map(nextCodec))

    def description(d: String): Body[S, T] = copy(info = info.description(d))
    def example(t: T): Body[S, T] = copy(info = info.example(t))
    def example(example: Example[T]): Body[S, T] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): Body[S, T] = copy(info = info.examples(examples))

    private[tapir] override def toEndpointIO: EndpointIO.StreamBodyWrapper[S, T] = EndpointIO.StreamBodyWrapper(this)
  }
}

trait CodecMappable[T] {
  private[tapir] type ThisType[X]

  def map[U: IsUnit](nextCodec: Codec[T, U, _]): ThisType[U]
  def map[U: IsUnit](f: T => U)(g: U => T): ThisType[U] = map(Codec.fromNoMeta(f)(g))
  def mapDecode[U: IsUnit](f: T => DecodeResult[U])(g: U => T): ThisType[U] = map(Codec.fromDecodeNoMeta(f)(g))
  def mapTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, T, CASE_CLASS]): ThisType[CASE_CLASS] = {
    map[CASE_CLASS](fc.tupled(c).apply(_))(ProductToParams(_, fc.arity).asInstanceOf[T])
  }

  def mapOption[TT, UU](nextCodec: Codec[TT, UU, _ <: CodecFormat])(implicit ev: T =:= Option[TT]): ThisType[Option[UU]] =
    map(Codec.option(nextCodec).asInstanceOf[Codec[T, Option[UU], CodecFormat]])
  def mapList[TT, UU](nextCodec: Codec[TT, UU, _ <: CodecFormat])(implicit ev: T =:= List[TT]): ThisType[List[UU]] =
    map(Codec.list(nextCodec).asInstanceOf[Codec[T, List[UU], CodecFormat]])
}
