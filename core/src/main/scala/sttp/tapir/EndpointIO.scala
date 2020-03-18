package sttp.tapir

import sttp.model.{Method, MultiQueryParams}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecForMany.PlainCodecForMany
import sttp.tapir.CodecForOptional.PlainCodecForOptional
import sttp.tapir.EndpointIO.{Example, Info}
import sttp.tapir.internal._
import sttp.tapir.model.ServerRequest
import sttp.tapir.typelevel.{FnComponents, ParamConcat}

import scala.collection.immutable.ListMap

sealed trait EndpointInput[I] {
  def and[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput[IJ]
  def /[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput[IJ] = and(other)

  def show: String

  def map[II](f: I => II)(g: II => I): EndpointInput[II] =
    EndpointInput.Mapped(this, f, g)

  def mapTo[COMPANION, CASE_CLASS <: Product](
      c: COMPANION
  )(implicit fc: FnComponents[COMPANION, I, CASE_CLASS]): EndpointInput[CASE_CLASS] = {
    map[CASE_CLASS](fc.tupled(c).apply)(ProductToParams(_, fc.arity).asInstanceOf[I])
  }
}

object EndpointInput {
  sealed trait Single[I] extends EndpointInput[I] {
    def and[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput[IJ] =
      other match {
        case s: Single[_]             => Multiple(Vector(this, s))
        case Multiple(inputs)         => Multiple(this +: inputs)
        case EndpointIO.Multiple(ios) => Multiple(this +: ios)
      }
  }

  sealed trait Basic[I] extends Single[I]

  case class FixedMethod(m: Method) extends Basic[Unit] {
    def show: String = m.method
  }

  case class FixedPath(s: String) extends Basic[Unit] {
    def show = s"/$s"
  }

  case class PathCapture[T](codec: PlainCodec[T], name: Option[String], info: EndpointIO.Info[T]) extends Basic[T] {
    def name(n: String): PathCapture[T] = copy(name = Some(n))
    def description(d: String): PathCapture[T] = copy(info = info.description(d))
    def example(t: T): PathCapture[T] = copy(info = info.example(t))
    def example(example: Example[T]): PathCapture[T] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): PathCapture[T] = copy(info = info.examples(examples))
    def validate(v: Validator[T]): PathCapture[T] = copy(codec = codec.validate(v))
    def show: String = addValidatorShow(s"/[${name.getOrElse("")}]", codec.validator)
  }

  case class PathsCapture(info: EndpointIO.Info[Seq[String]]) extends Basic[Seq[String]] {
    def description(d: String): PathsCapture = copy(info = info.description(d))
    def example(t: Seq[String]): PathsCapture = copy(info = info.example(t))
    def example(example: Example[Seq[String]]): PathsCapture = copy(info = info.example(example))
    def examples(examples: List[Example[Seq[String]]]): PathsCapture = copy(info = info.examples(examples))
    def show = s"/..."
  }

  case class Query[T](name: String, codec: PlainCodecForMany[T], info: EndpointIO.Info[T]) extends Basic[T] {
    def description(d: String): Query[T] = copy(info = info.description(d))
    def example(t: T): Query[T] = copy(info = info.example(t))
    def example(example: Example[T]): Query[T] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): Query[T] = copy(info = info.examples(examples))
    def deprecated(): Query[T] = copy(info = info.deprecated(true))
    def validate(v: Validator[T]): Query[T] = copy(codec = codec.validate(v))
    def show: String = addValidatorShow(s"?$name", codec.validator)
  }

  case class QueryParams(info: EndpointIO.Info[MultiQueryParams]) extends Basic[MultiQueryParams] {
    def description(d: String): QueryParams = copy(info = info.description(d))
    def example(t: MultiQueryParams): QueryParams = copy(info = info.example(t))
    def example(example: Example[MultiQueryParams]): QueryParams = copy(info = info.example(example))
    def examples(examples: List[Example[MultiQueryParams]]): QueryParams = copy(info = info.examples(examples))
    def deprecated(): QueryParams = copy(info = info.deprecated(true))
    def show = s"?..."
  }

  case class Cookie[T](name: String, codec: PlainCodecForOptional[T], info: EndpointIO.Info[T]) extends Basic[T] {
    def description(d: String): Cookie[T] = copy(info = info.description(d))
    def example(t: T): Cookie[T] = copy(info = info.example(t))
    def example(example: Example[T]): Cookie[T] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): Cookie[T] = copy(info = info.examples(examples))
    def deprecated(): Cookie[T] = copy(info = info.deprecated(true))
    def validate(v: Validator[T]): Cookie[T] = copy(codec = codec.validate(v))
    def show: String = addValidatorShow(s"{cookie $name}", codec.validator)
  }

  case class ExtractFromRequest[T](f: ServerRequest => T) extends Basic[T] {
    def show = s"{data from request}"
  }

  //

  trait Auth[T] extends EndpointInput.Single[T] {
    def input: EndpointInput.Single[T]
  }

  object Auth {
    case class ApiKey[T](input: EndpointInput.Single[T]) extends Auth[T] {
      def show = s"auth(api key, via ${input.show})"
    }
    case class Http[T](scheme: String, input: EndpointInput.Single[T]) extends Auth[T] {
      def show = s"auth($scheme http, via ${input.show})"
    }
    case class Oauth2[T](
        authorizationUrl: String,
        tokenUrl: String,
        scopes: ListMap[String, String],
        refreshUrl: Option[String] = None,
        input: EndpointInput.Single[T]
    ) extends Auth[T] {
      def show = s"auth(oauth2, via ${input.show})"
      def requiredScopes(requiredScopes: Seq[String]): ScopedOauth2[T] = ScopedOauth2(this, requiredScopes)
    }

    case class ScopedOauth2[T](oauth2: Oauth2[T], requiredScopes: Seq[String]) extends Auth[T] {
      require(requiredScopes.forall(oauth2.scopes.keySet.contains), "all requiredScopes have to be defined on outer Oauth2#scopes")
      def show = s"scoped(${oauth2.show})"
      override def input: Single[T] = oauth2.input
    }
  }

  //

  case class Mapped[I, T](wrapped: EndpointInput[I], f: I => T, g: T => I) extends Single[T] {
    override def show: String = s"map(${wrapped.show})"
  }

  //

  case class Multiple[I](inputs: Vector[Single[_]]) extends EndpointInput[I] {
    override def and[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput.Multiple[IJ] =
      other match {
        case s: Single[_]           => Multiple(inputs :+ s)
        case Multiple(m)            => Multiple(inputs ++ m)
        case EndpointIO.Multiple(m) => Multiple(inputs ++ m)
      }
    def show: String = if (inputs.isEmpty) "-" else inputs.map(_.show).mkString(" ")
  }
}

sealed trait EndpointOutput[I] {
  def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointOutput[IJ]

  def show: String

  def map[II](f: I => II)(g: II => I): EndpointOutput[II] =
    EndpointOutput.Mapped(this, f, g)

  def mapTo[COMPANION, CASE_CLASS <: Product](
      c: COMPANION
  )(implicit fc: FnComponents[COMPANION, I, CASE_CLASS]): EndpointOutput[CASE_CLASS] = {
    map[CASE_CLASS](fc.tupled(c).apply)(ProductToParams(_, fc.arity).asInstanceOf[I])
  }
}

object EndpointOutput {
  sealed trait Single[I] extends EndpointOutput[I] {
    def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointOutput[IJ] =
      other match {
        case s: Single[_]             => Multiple(Vector(this, s))
        case Void()                   => this.asInstanceOf[EndpointOutput[IJ]]
        case Multiple(outputs)        => Multiple(this +: outputs)
        case EndpointIO.Multiple(ios) => Multiple(this +: ios)
      }
  }

  sealed trait Basic[I] extends Single[I]

  //

  case class StatusCode(documentedCodes: Map[sttp.model.StatusCode, Info[Unit]] = Map.empty) extends Basic[sttp.model.StatusCode] {
    def description(code: sttp.model.StatusCode, d: String): StatusCode = {
      val updatedCodes = documentedCodes + (code -> Info.empty[Unit].description(d))
      copy(documentedCodes = updatedCodes)
    }
    override def show: String = s"status code - possible codes ($documentedCodes)"
  }

  //

  case class FixedStatusCode(statusCode: sttp.model.StatusCode, info: Info[Unit]) extends Basic[Unit] {
    def description(d: String): FixedStatusCode = copy(info = info.description(d))

    override def show: String = s"status code ($statusCode)"
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

  case class OneOf[I](mappings: Seq[StatusMapping[_ <: I]]) extends Single[I] {
    override def show: String = s"status one of(${mappings.map(_.output.show).mkString("|")})"
  }

  case class Mapped[I, T](wrapped: EndpointOutput[I], f: I => T, g: T => I) extends Single[T] {
    override def show: String = s"map(${wrapped.show})"
  }

  //

  case class Void() extends EndpointOutput[Nothing] {
    override def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[Nothing, J, IJ]): EndpointOutput[IJ] =
      other.asInstanceOf[EndpointOutput[IJ]]
    def show: String = "void"
  }
  case class Multiple[I](outputs: Vector[Single[_]]) extends EndpointOutput[I] {
    override def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointOutput.Multiple[IJ] =
      other match {
        case s: Single[_]           => Multiple(outputs :+ s)
        case Void()                 => this.asInstanceOf[EndpointOutput.Multiple[IJ]]
        case Multiple(m)            => Multiple(outputs ++ m)
        case EndpointIO.Multiple(m) => Multiple(outputs ++ m)
      }
    def show: String = if (outputs.isEmpty) "-" else outputs.map(_.show).mkString(" ")
  }
}

sealed trait EndpointIO[I] extends EndpointInput[I] with EndpointOutput[I] {
  def and[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointIO[IJ]

  def show: String
  override def map[II](f: I => II)(g: II => I): EndpointIO[II] =
    EndpointIO.Mapped(this, f, g)

  override def mapTo[COMPANION, CASE_CLASS <: Product](
      c: COMPANION
  )(implicit fc: FnComponents[COMPANION, I, CASE_CLASS]): EndpointIO[CASE_CLASS] = {
    map[CASE_CLASS](fc.tupled(c).apply)(ProductToParams(_, fc.arity).asInstanceOf[I])
  }
}

object EndpointIO {
  sealed trait Single[I] extends EndpointIO[I] with EndpointInput.Single[I] with EndpointOutput.Single[I] {
    def and[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointIO[IJ] =
      other match {
        case s: Single[_]      => Multiple(Vector(this, s))
        case Multiple(outputs) => Multiple(this +: outputs)
      }
  }

  sealed trait Basic[I] extends Single[I] with EndpointInput.Basic[I] with EndpointOutput.Basic[I]

  case class Body[T, CF <: CodecFormat, R](codec: CodecForOptional[T, CF, R], info: Info[T]) extends Basic[T] {
    def description(d: String): Body[T, CF, R] = copy(info = info.description(d))
    def example(t: T): Body[T, CF, R] = copy(info = info.example(t))
    def example(example: Example[T]): Body[T, CF, R] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): Body[T, CF, R] = copy(info = info.examples(examples))
    def validate(v: Validator[T]): Body[T, CF, R] = copy(codec = codec.validate(v))
    def show: String = addValidatorShow(s"{body as ${codec.meta.format.mediaType}}", codec.validator)
  }

  case class StreamBodyWrapper[S, F <: CodecFormat](wrapped: StreamingEndpointIO.Body[S, F]) extends Basic[S] {
    def show = s"{body as stream, ${wrapped.format.mediaType}}"
  }

  case class FixedHeader(name: String, value: String, info: Info[Unit]) extends Basic[Unit] {
    def description(d: String): FixedHeader = copy(info = info.description(d))
    def deprecated(): FixedHeader = copy(info = info.deprecated(true))
    def show = s"{header $name: $value}"
  }

  case class Header[T](name: String, codec: PlainCodecForMany[T], info: Info[T]) extends Basic[T] {
    def description(d: String): Header[T] = copy(info = info.description(d))
    def example(t: T): Header[T] = copy(info = info.example(t))
    def example(example: Example[T]): Header[T] = copy(info = info.example(example))
    def examples(examples: List[Example[T]]): Header[T] = copy(info = info.examples(examples))
    def deprecated(): Header[T] = copy(info = info.deprecated(true))
    def validate(v: Validator[T]): Header[T] = copy(codec = codec.validate(v))
    def show: String = addValidatorShow(s"{header $name}", codec.validator)
  }

  case class Headers(info: Info[Seq[(String, String)]]) extends Basic[Seq[(String, String)]] {
    def description(d: String): Headers = copy(info = info.description(d))
    def example(t: Seq[(String, String)]): Headers = copy(info = info.example(t))
    def example(example: Example[Seq[(String, String)]]): Headers = copy(info = info.example(example))
    def examples(examples: List[Example[Seq[(String, String)]]]): Headers = copy(info = info.examples(examples))
    def show = s"{multiple headers}"
  }

  case class Mapped[I, T](wrapped: EndpointIO[I], f: I => T, g: T => I) extends Single[T] {
    override def show: String = s"map(${wrapped.show})"
  }

  //

  case class Multiple[I](ios: Vector[Single[_]]) extends EndpointIO[I] {
    override def and[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput.Multiple[IJ] =
      other match {
        case s: EndpointInput.Single[_] => EndpointInput.Multiple((ios: Vector[EndpointInput.Single[_]]) :+ s)
        case EndpointInput.Multiple(m)  => EndpointInput.Multiple((ios: Vector[EndpointInput.Single[_]]) ++ m)
        case EndpointIO.Multiple(m)     => EndpointInput.Multiple((ios: Vector[EndpointInput.Single[_]]) ++ m)
      }
    override def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointOutput.Multiple[IJ] =
      other match {
        case s: EndpointOutput.Single[_] => EndpointOutput.Multiple((ios: Vector[EndpointOutput.Single[_]]) :+ s)
        case EndpointOutput.Void()       => this.asInstanceOf[EndpointOutput.Multiple[IJ]]
        case EndpointOutput.Multiple(m)  => EndpointOutput.Multiple((ios: Vector[EndpointOutput.Single[_]]) ++ m)
        case EndpointIO.Multiple(m)      => EndpointOutput.Multiple((ios: Vector[EndpointOutput.Single[_]]) ++ m)
      }
    override def and[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): Multiple[IJ] =
      other match {
        case s: Single[_] => Multiple(ios :+ s)
        case Multiple(m)  => Multiple(ios ++ m)
      }
    def show: String = if (ios.isEmpty) "-" else ios.map(_.show).mkString(" ")
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
sealed trait StreamingEndpointIO[I, +S] {
  def map[II](f: I => II)(g: II => I): StreamingEndpointIO[II, S] =
    StreamingEndpointIO.Mapped(this, f, g)

  private[tapir] def toEndpointIO: EndpointIO[I]
}

object StreamingEndpointIO {
  case class Body[S, CF <: CodecFormat](schema: Schema[_], format: CF, info: EndpointIO.Info[String]) extends StreamingEndpointIO[S, S] {
    def description(d: String): Body[S, CF] = copy(info = info.description(d))
    def example(t: String): Body[S, CF] = copy(info = info.example(t))
    def example(example: Example[String]): Body[S, CF] = copy(info = info.example(example))
    def examples(examples: List[Example[String]]): Body[S, CF] = copy(info = info.examples(examples))

    private[tapir] override def toEndpointIO: EndpointIO.StreamBodyWrapper[S, CF] = EndpointIO.StreamBodyWrapper(this)
  }

  case class Mapped[I, T, S](wrapped: StreamingEndpointIO[I, S], f: I => T, g: T => I) extends StreamingEndpointIO[T, S] {
    private[tapir] override def toEndpointIO: EndpointIO[T] = wrapped.toEndpointIO.map(f)(g)
  }
}
