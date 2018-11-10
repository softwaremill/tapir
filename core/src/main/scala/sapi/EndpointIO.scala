package sapi

import sapi.TypeMapper.{RequiredTextTypeMapper, TextTypeMapper}
import shapeless.{::, HList, HNil}
import shapeless.ops.hlist.Prepend

sealed trait EndpointInput[I <: HList] {
  def and[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput[IJ]
  def /[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput[IJ] = and(other)
}

object EndpointInput {
  sealed trait Single[I <: HList] extends EndpointInput[I] {
    def and[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput[IJ] =
      other match {
        case s: Single[_]     => Multiple(Vector(this, s))
        case Multiple(inputs) => Multiple(this +: inputs)
      }
  }

  case class PathSegment(s: String) extends Single[HNil]

  case class PathCapture[T](name: String, m: RequiredTextTypeMapper[T], description: Option[String], example: Option[T])
      extends Single[T :: HNil] {
    def description(d: String): PathCapture[T] = copy(description = Some(d))
    def example(t: T): PathCapture[T] = copy(example = Some(t))
  }

  case class Query[T](name: String, m: TextTypeMapper[T], description: Option[String], example: Option[T]) extends Single[T :: HNil] {
    def description(d: String): Query[T] = copy(description = Some(d))
    def example(t: T): Query[T] = copy(example = Some(t))
  }

  case class Multiple[I <: HList](inputs: Vector[Single[_]]) extends EndpointInput[I] {
    override def and[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput.Multiple[IJ] =
      other match {
        case s: Single[_] => Multiple(inputs :+ s)
        case Multiple(m)  => Multiple(inputs ++ m)
      }
  }
}

sealed trait EndpointOutput[I <: HList] {
  def and[J <: HList, IJ <: HList](other: EndpointOutput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointOutput[IJ]
}

object EndpointOutput {
  sealed trait Single[I <: HList] extends EndpointOutput[I] {
    def and[J <: HList, IJ <: HList](other: EndpointOutput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointOutput[IJ] =
      other match {
        case s: Single[_]      => Multiple(Vector(this, s))
        case Multiple(outputs) => Multiple(this +: outputs)
      }
  }

  case class Multiple[I <: HList](outputs: Vector[Single[_]]) extends EndpointOutput[I] {
    override def and[J <: HList, IJ <: HList](other: EndpointOutput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointOutput.Multiple[IJ] =
      other match {
        case s: Single[_] => Multiple(outputs :+ s)
        case Multiple(m)  => Multiple(outputs ++ m)
      }
  }
}

object EndpointIO {
  case class Body[T, M <: MediaType](m: TypeMapper[T, M], description: Option[String], example: Option[T])
      extends EndpointInput.Single[T :: HNil]
      with EndpointOutput.Single[T :: HNil] {
    def description(d: String): Body[T, M] = copy(description = Some(d))
    def example(t: T): Body[T, M] = copy(example = Some(t))
  }

  case class Header[T](name: String, m: TextTypeMapper[T], description: Option[String], example: Option[T])
      extends EndpointInput.Single[T :: HNil]
      with EndpointOutput.Single[T :: HNil] {
    def description(d: String): Header[T] = copy(description = Some(d))
    def example(t: T): Header[T] = copy(example = Some(t))
  }
}
