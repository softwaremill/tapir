package sttp.tapir.typelevel

import magnolia.{Magnolia, ReadOnlyCaseClass, ReadOnlyParam, SealedTrait}

import scala.reflect.ClassTag

private[typelevel] trait GenericMatchType {
  type Typeclass[T] = MatchType[T]

  def combine[T: ClassTag](ctx: ReadOnlyCaseClass[Typeclass, T]): Typeclass[T] = {
    val ct = implicitly[ClassTag[T]]

    { value: Any =>
      ct.runtimeClass.isInstance(value) &&
      ctx.parameters.forall { param: ReadOnlyParam[Typeclass, T] =>
        {
          param.typeclass(param.dereference(value.asInstanceOf[T]))
        }
      }
    }
  }

  def dispatch[T: ClassTag](ctx: SealedTrait[Typeclass, T]): Typeclass[T] = {
    val ct = implicitly[ClassTag[T]]

    { value: Any =>
      ct.runtimeClass.isInstance(value) && ctx.dispatch(value.asInstanceOf[T]) { sub => sub.typeclass(sub.cast(value.asInstanceOf[T])) }
    }
  }

  implicit def gen[T]: MatchType[T] = macro Magnolia.gen[T]
}
