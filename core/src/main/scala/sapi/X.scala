package sapi
import sapi.TypeMapper.{RequiredTextTypeMapper, TextTypeMapper}
import shapeless.{::, HNil}

object X {

  //

  object Tuple {
    trait Concat[T, U, TU]

    trait ToFn[T] {
      type Out[X]
    }

    implicit def tuple2ToFn[T1, T2]: ToFn[(T1, T2)] = new ToFn[(T1, T2)] {
      type Out[X] = Function2[T1, T2, X]
    }
  }

  //

  sealed trait EndpointInput[I] { // I: a single type or a 2+ tuple
    def and[J, IJ](other: EndpointInput[J])(implicit ts: Tuple.Concat[I, J, IJ]): EndpointInput[IJ]
    def /[J, IJ](other: EndpointInput[J])(implicit ts: Tuple.Concat[I, J, IJ]): EndpointInput[IJ] = and(other)
  }

  object EndpointInput {
    sealed trait Single[I] extends EndpointInput[I] {
      def and[J, IJ](other: EndpointInput[J])(implicit ts: Tuple.Concat[I, J, IJ]): EndpointInput[IJ] =
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

    case class Multiple[I](inputs: Vector[Single[_]]) extends EndpointInput[I] {
      override def and[J, IJ](other: EndpointInput[J])(implicit ts: Tuple.Concat[I, J, IJ]): EndpointInput.Multiple[IJ] =
        other match {
          case s: Single[_] => Multiple(inputs :+ s)
          case Multiple(m)  => Multiple(inputs ++ m)
        }
    }
  }

  def path[T: RequiredTextTypeMapper](name: String): EndpointInput[T :: HNil] =
    EndpointInput.PathCapture(name, implicitly[RequiredTextTypeMapper[T]], None, None)
  implicit def stringToPath(s: String): EndpointInput[HNil] = EndpointInput.PathSegment(s)

  def query[T: TextTypeMapper](name: String): EndpointInput.Query[T] = EndpointInput.Query(name, implicitly[TextTypeMapper[T]], None, None)
}
