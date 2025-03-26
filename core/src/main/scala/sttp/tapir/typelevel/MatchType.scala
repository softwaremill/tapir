package sttp.tapir.typelevel

trait MatchType[T] {

  /** @return
    *   is `a` a value of type `T`?
    */
  def apply(a: Any): Boolean
  def partial: PartialFunction[Any, Boolean] = { case a: Any =>
    apply(a)
  }
}

object MatchType extends MatchTypeMacros {

  implicit lazy val string: MatchType[String] = matchTypeFromPartial { case _: String => true }
  implicit lazy val bool: MatchType[Boolean] = matchTypeFromPartial[Boolean] { case _: Boolean => true }
  implicit lazy val char: MatchType[Char] = matchTypeFromPartial[Char] { case _: Char => true }
  implicit lazy val byte: MatchType[Byte] = matchTypeFromPartial[Byte] { case _: Byte => true }
  implicit lazy val short: MatchType[Short] = matchTypeFromPartial[Short] { case _: Short => true }
  implicit lazy val long: MatchType[Long] = matchTypeFromPartial { case _: Long => true }
  implicit lazy val float: MatchType[Float] = matchTypeFromPartial[Float] { case _: Float => true }
  implicit lazy val double: MatchType[Double] = matchTypeFromPartial[Double] { case _: Double => true }
  implicit lazy val int: MatchType[Int] = matchTypeFromPartial[Int] { case _: Int => true }

  private[typelevel] def matchTypeFromPartial[T](pf: PartialFunction[Any, Boolean]): MatchType[T] = { a => pf.lift(a).getOrElse(false) }
}
