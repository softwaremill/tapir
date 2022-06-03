package sttp.tapir.typelevel

import magnolia1.{Magnolia, ReadOnlyCaseClass, ReadOnlyParam, SealedTrait}

import scala.reflect.ClassTag

trait MatchTypeMacros extends MatchTypeMagnoliaDerivation {
  implicit def gen[T]: MatchType[T] = macro Magnolia.gen[T]
}

trait MatchTypeMagnoliaDerivation {
  type Typeclass[T] = MatchType[T]

  def join[T: ClassTag](ctx: ReadOnlyCaseClass[Typeclass, T]): Typeclass[T] = {
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

  def split[T: ClassTag](ctx: SealedTrait[Typeclass, T]): Typeclass[T] = {
    val ct = implicitly[ClassTag[T]]

    { value: Any =>
      ct.runtimeClass.isInstance(value) && ctx.split(value.asInstanceOf[T]) { sub => sub.typeclass(sub.cast(value.asInstanceOf[T])) }
    }
  }
}
