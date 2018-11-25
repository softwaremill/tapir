package tapir

import tapir.TypeMapper.{RequiredTextTypeMapper, TextTypeMapper}
import tapir.typelevel.{ParamConcat, ParamsAsArgs}

sealed trait EndpointInput[I] {
  def and[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput[IJ]
  def /[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput[IJ] = and(other)
  def show: String
  def map[T](f: I => T)(g: T => I)(implicit paramsAsArgs: ParamsAsArgs[I]): EndpointInput[T] =
    EndpointInput.Mapped(this, f, g, paramsAsArgs)
}

object EndpointInput {
  sealed trait Single[I] extends EndpointInput[I] {
    def and[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput[IJ] =
      other match {
        case s: Single[_]     => Multiple(Vector(this, s))
        case Multiple(inputs) => Multiple(this +: inputs)
      }
  }

  case class PathSegment(s: String) extends Single[Unit] {
    def show = s"/$s"
  }

  case class PathCapture[T](m: RequiredTextTypeMapper[T], name: Option[String], description: Option[String], example: Option[T])
      extends Single[T] { // TODO rename "name" as it's only neede for docs
    def name(n: String): PathCapture[T] = copy(name = Some(n))
    def description(d: String): PathCapture[T] = copy(description = Some(d))
    def example(t: T): PathCapture[T] = copy(example = Some(t))
    def show = s"/[${name.getOrElse("")}]"
  }

  case class Query[T](name: String, m: TextTypeMapper[T], description: Option[String], example: Option[T]) extends Single[T] {
    def description(d: String): Query[T] = copy(description = Some(d))
    def example(t: T): Query[T] = copy(example = Some(t))
    def show = s"?$name"
  }

  case class Mapped[I, T](wrapped: EndpointInput[I], f: I => T, g: T => I, paramsAsArgs: ParamsAsArgs[I]) extends Single[T] {
    override def show: String = s"map(${wrapped.show})"
  }

  case class Multiple[I](inputs: Vector[Single[_]]) extends EndpointInput[I] {
    override def and[J, IJ](other: EndpointInput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointInput.Multiple[IJ] =
      other match {
        case s: Single[_] => Multiple(inputs :+ s)
        case Multiple(m)  => Multiple(inputs ++ m)
      }
    def show: String = if (inputs.isEmpty) "-" else inputs.map(_.show).mkString(" ")
  }
  object Multiple {
    def apply[I](input: EndpointInput[I]): EndpointInput.Multiple[I] = input match {
      case s: Single[I] => Multiple(Vector(s))
      case m: Multiple[_] => m
    }
  }
}

sealed trait EndpointOutput[I] {
  def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointOutput[IJ]
  def show: String
}

object EndpointOutput {
  sealed trait Single[I] extends EndpointOutput[I] {
    def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointOutput[IJ] =
      other match {
        case s: Single[_]      => Multiple(Vector(this, s))
        case Multiple(outputs) => Multiple(this +: outputs)
      }
  }

  case class Multiple[I](outputs: Vector[Single[_]]) extends EndpointOutput[I] {
    override def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[I, J, IJ]): EndpointOutput.Multiple[IJ] =
      other match {
        case s: Single[_] => Multiple(outputs :+ s)
        case Multiple(m)  => Multiple(outputs ++ m)
      }
    def show: String = if (outputs.isEmpty) "-" else outputs.map(_.show).mkString(" ")
  }
}

object EndpointIO {
  case class Body[T, M <: MediaType](m: TypeMapper[T, M], description: Option[String], example: Option[T])
      extends EndpointInput.Single[T]
      with EndpointOutput.Single[T] {
    def description(d: String): Body[T, M] = copy(description = Some(d))
    def example(t: T): Body[T, M] = copy(example = Some(t))
    def show = s"{body as ${m.mediaType.mediaType}}"
  }

  case class Header[T](name: String, m: TextTypeMapper[T], description: Option[String], example: Option[T])
      extends EndpointInput.Single[T]
      with EndpointOutput.Single[T] {
    def description(d: String): Header[T] = copy(description = Some(d))
    def example(t: T): Header[T] = copy(example = Some(t))
    def show = s"{header $name}"
  }
}
