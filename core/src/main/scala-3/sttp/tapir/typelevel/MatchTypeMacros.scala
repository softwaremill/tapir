package sttp.tapir.typelevel

import scala.deriving.Mirror
import scala.reflect.ClassTag
import scala.reflect.classTag
import magnolia.*


trait MatchTypeMacros {
  inline implicit def gen[T: ClassTag](using Mirror.Of[T]): MatchType[T] = {
    val derivation = new MatchTypeDerivation {
      val ct: ClassTag[T] = classTag[T]

      override def join[A](ctx: CaseClass[Typeclass, A]): Typeclass[A] = (value: Any) =>
       ct.runtimeClass.isInstance(value) && 
       ctx.params.forall { (param: CaseClass.Param[Typeclass, A]) =>
        param.typeclass(param.deref(value.asInstanceOf[A]))
      }

      override def split[A](ctx: SealedTrait[Typeclass, A]): Typeclass[A] = (value: Any) =>
        ct.runtimeClass.isInstance(value) && ctx.choose(value.asInstanceOf[A]) { sub => sub.typeclass(sub.cast(value.asInstanceOf[A])) }
    }

    derivation.derived[T]
  }
}
