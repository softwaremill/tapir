package tapir

import tapir.GeneralCodec.{PlainCodec, GeneralPlainCodec}
import tapir.internal.ProductToParams
import tapir.typelevel.{FnComponents, ParamConcat, ParamsAsArgs}

sealed trait EndpointInput[I] {
  def and[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput[IJ]
  def /[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput[IJ] = and(other)
  def &[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput[IJ] = and(other)

  def show: String

  def map[II](f: I => II)(g: II => I)(implicit paramsAsArgs: ParamsAsArgs[I]): EndpointInput[II] =
    EndpointInput.Mapped(this, f, g, paramsAsArgs)

  def mapTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, I, CASE_CLASS],
                                                            paramsAsArgs: ParamsAsArgs[I]): EndpointInput[CASE_CLASS] = {
    map[CASE_CLASS](fc.tupled(c).apply)(ProductToParams(_, fc.arity).asInstanceOf[I])(paramsAsArgs)
  }

  private[tapir] def asVectorOfSingle: Vector[EndpointInput.Single[_]] = this match {
    case s: EndpointInput.Single[_]   => Vector(s)
    case m: EndpointInput.Multiple[_] => m.inputs
    case m: EndpointIO.Multiple[_]    => m.ios
  }

  private[tapir] def bodyType: Option[RawValueType[_]] = this match {
    case b: EndpointIO.Body[_, _, _]            => Some(b.codec.rawValueType)
    case EndpointInput.Multiple(inputs)         => inputs.flatMap(_.bodyType).headOption
    case EndpointIO.Multiple(inputs)            => inputs.flatMap(_.bodyType).headOption
    case EndpointInput.Mapped(wrapped, _, _, _) => wrapped.bodyType
    case EndpointIO.Mapped(wrapped, _, _, _)    => wrapped.bodyType
    case _                                      => None
  }

  private[tapir] def hasForm: Boolean = this match {
    case m: EndpointIO.Multiple[_]    => m.ios.exists(_.hasForm)
    case m: EndpointInput.Multiple[_] => m.inputs.exists(_.hasForm)
    case _                            => false
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

  case class PathSegment(s: String) extends Single[Unit] {
    def show = s"/$s"
  }

  case class PathCapture[T](codec: PlainCodec[T], name: Option[String], description: Option[String], example: Option[T]) extends Single[T] {
    def name(n: String): PathCapture[T] = copy(name = Some(n))
    def description(d: String): PathCapture[T] = copy(description = Some(d))
    def example(t: T): PathCapture[T] = copy(example = Some(t))
    def show = s"/[${name.getOrElse("")}]"
  }

  case class Query[T](name: String, codec: GeneralPlainCodec[T], description: Option[String], example: Option[T]) extends Single[T] {
    def description(d: String): Query[T] = copy(description = Some(d))
    def example(t: T): Query[T] = copy(example = Some(t))
    def show = s"?$name"
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

sealed trait EndpointIO[I] extends EndpointInput[I] {
  def and[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointIO[IJ]
  def &[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointIO[IJ] = and(other)

  def show: String
  override def map[II](f: I => II)(g: II => I)(implicit paramsAsArgs: ParamsAsArgs[I]): EndpointIO[II] =
    EndpointIO.Mapped(this, f, g, paramsAsArgs)

  override def mapTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, I, CASE_CLASS],
                                                                     paramsAsArgs: ParamsAsArgs[I]): EndpointIO[CASE_CLASS] = {
    map[CASE_CLASS](fc.tupled(c).apply)(ProductToParams(_, fc.arity).asInstanceOf[I])(paramsAsArgs)
  }

  private[tapir] override def asVectorOfSingle: Vector[EndpointIO.Single[_]] = this match {
    case s: EndpointIO.Single[_]   => Vector(s)
    case m: EndpointIO.Multiple[_] => m.ios
  }
}

object EndpointIO {
  sealed trait Single[I] extends EndpointIO[I] with EndpointInput.Single[I] {
    def and[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointIO[IJ] =
      other match {
        case s: Single[_]      => Multiple(Vector(this, s))
        case Multiple(outputs) => Multiple(this +: outputs)
      }
  }

  case class Body[T, M <: MediaType, R](codec: GeneralCodec[T, M, R], description: Option[String], example: Option[T]) extends Single[T] {
    def description(d: String): Body[T, M, R] = copy(description = Some(d))
    def example(t: T): Body[T, M, R] = copy(example = Some(t))
    def show = s"{body as ${codec.mediaType.mediaType}}"
  }

  case class Header[T](name: String, codec: GeneralPlainCodec[T], description: Option[String], example: Option[T]) extends Single[T] {
    def description(d: String): Header[T] = copy(description = Some(d))
    def example(t: T): Header[T] = copy(example = Some(t))
    def show = s"{header $name}"
  }

  //

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
    override def and[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): Multiple[IJ] =
      other match {
        case s: Single[_] => Multiple(ios :+ s)
        case Multiple(m)  => Multiple(ios ++ m)
      }
    def show: String = if (ios.isEmpty) "-" else ios.map(_.show).mkString(" ")
  }
}
