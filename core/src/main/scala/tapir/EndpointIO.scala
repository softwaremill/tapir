package tapir

import tapir.Codec.PlainCodec
import tapir.CodecForMany.PlainCodecForMany
import tapir.CodecForOptional.PlainCodecForOptional
import tapir.internal.ProductToParams
import tapir.model.{Method, MultiQueryParams, ServerRequest}
import tapir.typelevel.{FnComponents, ParamConcat, ParamsAsArgs}

sealed trait EndpointInput[I] {
  def and[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput[IJ]
  def /[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput[IJ] = and(other)

  def show: String

  def map[II](f: I => II)(g: II => I)(implicit paramsAsArgs: ParamsAsArgs[I]): EndpointInput[II] =
    EndpointInput.Mapped(this, f, g, paramsAsArgs)

  def mapTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, I, CASE_CLASS],
                                                            paramsAsArgs: ParamsAsArgs[I]): EndpointInput[CASE_CLASS] = {
    map[CASE_CLASS](fc.tupled(c).apply)(ProductToParams(_, fc.arity).asInstanceOf[I])(paramsAsArgs)
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

  case class RequestMethod(m: Method) extends Basic[Unit] {
    def show: String = m.m
  }

  case class PathSegment(s: String) extends Basic[Unit] {
    def show = s"/$s"
  }

  case class PathCapture[T](codec: PlainCodec[T], name: Option[String], info: EndpointIO.Info[T]) extends Basic[T] {
    def name(n: String): PathCapture[T] = copy(name = Some(n))
    def description(d: String): PathCapture[T] = copy(info = info.description(d))
    def example(t: T): PathCapture[T] = copy(info = info.example(t))
    def show = s"/[${name.getOrElse("")}]"
  }

  case class PathsCapture(info: EndpointIO.Info[Seq[String]]) extends Basic[Seq[String]] {
    def description(d: String): PathsCapture = copy(info = info.description(d))
    def example(t: Seq[String]): PathsCapture = copy(info = info.example(t))
    def show = s"/[multiple paths]"
  }

  case class Query[T](name: String, codec: PlainCodecForMany[T], info: EndpointIO.Info[T]) extends Basic[T] {
    def description(d: String): Query[T] = copy(info = info.description(d))
    def example(t: T): Query[T] = copy(info = info.example(t))
    def show = s"?$name"
  }

  case class QueryParams(info: EndpointIO.Info[MultiQueryParams]) extends Basic[MultiQueryParams] {
    def description(d: String): QueryParams = copy(info = info.description(d))
    def example(t: MultiQueryParams): QueryParams = copy(info = info.example(t))
    def show = s"?{multiple params}"
  }

  case class Cookie[T](name: String, codec: PlainCodecForOptional[T], info: EndpointIO.Info[T]) extends Basic[T] {
    def description(d: String): Cookie[T] = copy(info = info.description(d))
    def example(t: T): Cookie[T] = copy(info = info.example(t))
    def show = s"{cookie $name}"
  }

  case class ExtractFromRequest[T](f: ServerRequest => T) extends Basic[T] {
    def show = s"{from request}"
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
  }

  //

  case class Mapped[I, T](wrapped: EndpointInput[I], f: I => T, g: T => I, paramsAsArgs: ParamsAsArgs[I]) extends Single[T] {
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

  def map[II](f: I => II)(g: II => I)(implicit paramsAsArgs: ParamsAsArgs[I]): EndpointOutput[II] =
    EndpointOutput.Mapped(this, f, g, paramsAsArgs)

  def mapTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, I, CASE_CLASS],
                                                            paramsAsArgs: ParamsAsArgs[I]): EndpointOutput[CASE_CLASS] = {
    map[CASE_CLASS](fc.tupled(c).apply)(ProductToParams(_, fc.arity).asInstanceOf[I])(paramsAsArgs)
  }
}

object EndpointOutput {
  sealed trait Single[I] extends EndpointOutput[I] {
    def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointOutput[IJ] =
      other match {
        case s: Single[_]             => Multiple(Vector(this, s))
        case Multiple(outputs)        => Multiple(this +: outputs)
        case EndpointIO.Multiple(ios) => Multiple(this +: ios)
      }
  }

  sealed trait Basic[I] extends Single[I]

  //

  case class StatusCode() extends Basic[tapir.StatusCode] {
    override def show: String = "{status code}"
  }

  //

  case class StatusFrom[I](output: EndpointOutput[I],
                           default: tapir.StatusCode,
                           defaultSchema: Option[Schema],
                           when: Vector[(When[I], tapir.StatusCode)])
      extends Single[I] {
    def defaultSchema(s: Schema): StatusFrom[I] = this.copy(defaultSchema = Some(s))
    override def show: String = s"status from(${output.show}, $default or ${when.map(_._2).mkString("/")})"
  }

  case class Mapped[I, T](wrapped: EndpointOutput[I], f: I => T, g: T => I, paramsAsArgs: ParamsAsArgs[I]) extends Single[T] {
    override def show: String = s"map(${wrapped.show})"
  }

  //

  case class Multiple[I](outputs: Vector[Single[_]]) extends EndpointOutput[I] {
    override def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointOutput.Multiple[IJ] =
      other match {
        case s: Single[_]           => Multiple(outputs :+ s)
        case Multiple(m)            => Multiple(outputs ++ m)
        case EndpointIO.Multiple(m) => Multiple(outputs ++ m)
      }
    def show: String = if (outputs.isEmpty) "-" else outputs.map(_.show).mkString(" ")
  }
}

sealed trait EndpointIO[I] extends EndpointInput[I] with EndpointOutput[I] {
  def and[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointIO[IJ]

  def show: String
  override def map[II](f: I => II)(g: II => I)(implicit paramsAsArgs: ParamsAsArgs[I]): EndpointIO[II] =
    EndpointIO.Mapped(this, f, g, paramsAsArgs)

  override def mapTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, I, CASE_CLASS],
                                                                     paramsAsArgs: ParamsAsArgs[I]): EndpointIO[CASE_CLASS] = {
    map[CASE_CLASS](fc.tupled(c).apply)(ProductToParams(_, fc.arity).asInstanceOf[I])(paramsAsArgs)
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

  case class Body[T, M <: MediaType, R](codec: CodecForOptional[T, M, R], info: Info[T]) extends Basic[T] {
    def description(d: String): Body[T, M, R] = copy(info = info.description(d))
    def example(t: T): Body[T, M, R] = copy(info = info.example(t))
    def show = s"{body as ${codec.meta.mediaType.mediaType}}"
  }

  case class StreamBodyWrapper[S, M <: MediaType](wrapped: StreamingEndpointIO.Body[S, M]) extends Basic[S] {
    def show = s"{body as stream, ${wrapped.mediaType.mediaType}}"
  }

  case class Header[T](name: String, codec: PlainCodecForMany[T], info: Info[T]) extends Basic[T] {
    def description(d: String): Header[T] = copy(info = info.description(d))
    def example(t: T): Header[T] = copy(info = info.example(t))
    def show = s"{header $name}"
  }

  case class Headers(info: Info[Seq[(String, String)]]) extends Basic[Seq[(String, String)]] {
    def description(d: String): Headers = copy(info = info.description(d))
    def example(t: Seq[(String, String)]): Headers = copy(info = info.example(t))
    def show = s"{multiple headers}"
  }

  case class Mapped[I, T](wrapped: EndpointIO[I], f: I => T, g: T => I, paramsAsArgs: ParamsAsArgs[I]) extends Single[T] {
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

  case class Info[T](description: Option[String], example: Option[T]) {
    def description(d: String): Info[T] = copy(description = Some(d))
    def example(t: T): Info[T] = copy(example = Some(t))
  }
  object Info {
    def empty[T]: Info[T] = Info[T](None, None)
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
  def map[II](f: I => II)(g: II => I)(implicit paramsAsArgs: ParamsAsArgs[I]): StreamingEndpointIO[II, S] =
    StreamingEndpointIO.Mapped(this, f, g, paramsAsArgs)

  private[tapir] def toEndpointIO: EndpointIO[I]
}

object StreamingEndpointIO {
  case class Body[S, M <: MediaType](schema: Schema, mediaType: M, info: EndpointIO.Info[String]) extends StreamingEndpointIO[S, S] {
    def description(d: String): Body[S, M] = copy(info = info.description(d))
    def example(t: String): Body[S, M] = copy(info = info.example(t))

    private[tapir] override def toEndpointIO: EndpointIO.StreamBodyWrapper[S, M] = EndpointIO.StreamBodyWrapper(this)
  }

  case class Mapped[I, T, S](wrapped: StreamingEndpointIO[I, S], f: I => T, g: T => I, paramsAsArgs: ParamsAsArgs[I])
      extends StreamingEndpointIO[T, S] {
    private[tapir] override def toEndpointIO: EndpointIO[T] = wrapped.toEndpointIO.map(f)(g)
  }
}
