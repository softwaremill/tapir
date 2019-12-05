package sttp.tapir.typelevel

import magnolia.{CaseClass, Magnolia, SealedTrait}

import scala.reflect.ClassTag

trait MatchType[T] {
  def apply(a: Any): Boolean
}

trait GenericMatchType {
  type Typeclass[T] = MatchType[T]

  def combine[T: ClassTag](ctx: CaseClass[Typeclass, T]): Typeclass[T] = { value: Any =>
    val ct = implicitly[ClassTag[T]]
    ct.runtimeClass.isInstance(value) && ctx.parameters.forall {
      param => param.typeclass(param.dereference(value.asInstanceOf[T]))
    }
  }

  def dispatch[T: ClassTag](ctx: SealedTrait[Typeclass, T]): Typeclass[T] = { value: Any =>
    val ct = implicitly[ClassTag[T]]
    ct.runtimeClass.isInstance(value) && ctx.dispatch(value.asInstanceOf[T]) { sub =>
      sub.typeclass(sub.cast(value.asInstanceOf[T]))
    }
  }

  implicit def gen[T]: MatchType[T] = macro Magnolia.gen[T]
}

object MatchType extends GenericMatchType {

  implicit val string: MatchType[String] = matchTypeFromPartial{case _:String => true}
  implicit val bool: MatchType[Boolean] = matchTypeFromPartial[Boolean]{case _:Boolean => true}
  implicit val char: MatchType[Char] = matchTypeFromPartial[Char]{case _:Char => true}
  implicit val byte: MatchType[Byte] = matchTypeFromPartial[Byte]{case _:Byte => true}
  implicit val short: MatchType[Short] = matchTypeFromPartial[Short]{case _:Short => true}
  implicit val long: MatchType[Long] = matchTypeFromPartial{case _:Long => true}
  implicit val float: MatchType[Float] = matchTypeFromPartial[Float]{case _:Float => true}
  implicit val double: MatchType[Double] = matchTypeFromPartial[Double]{case _:Double => true}
  implicit val int: MatchType[Int] = matchTypeFromPartial[Int]{case _:Int => true}

  private def matchTypeFromPartial[T](pf: PartialFunction[Any, Boolean]): MatchType[T] =  { a =>
    pf.lift(a).getOrElse(false)
  }
}
