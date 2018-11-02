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
        case s: Single[_]     => EndpointInput.Multiple(Vector(this, s))
        case Multiple(inputs) => EndpointInput.Multiple(this +: inputs)
      }
  }

  case class PathSegment(s: String) extends Single[HNil]

  case class PathCapture[T](name: String, m: RequiredTextTypeMapper[T], description: Option[String], example: Option[T])
      extends Single[T :: HNil] {
    def description(d: String): EndpointInput.PathCapture[T] = copy(description = Some(d))
    def example(t: T): EndpointInput.PathCapture[T] = copy(example = Some(t))
  }

  case class Query[T](name: String, m: TextTypeMapper[T], description: Option[String], example: Option[T]) extends Single[T :: HNil] {
    def description(d: String): EndpointInput.Query[T] = copy(description = Some(d))
    def example(t: T): EndpointInput.Query[T] = copy(example = Some(t))
  }

  case class Multiple[I <: HList](inputs: Vector[Single[_]]) extends EndpointInput[I] {
    override def and[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput.Multiple[IJ] =
      other match {
        case s: Single[_] => EndpointInput.Multiple(inputs :+ s)
        case Multiple(m)  => EndpointInput.Multiple(inputs ++ m)
      }
  }
}
