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

  implicit val string: MatchType[String] = matchTypeFromPartial { case _: String => true }
  implicit val bool: MatchType[Boolean] = matchTypeFromPartial[Boolean] { case _: Boolean => true }
  implicit val char: MatchType[Char] = matchTypeFromPartial[Char] { case _: Char => true }
  implicit val byte: MatchType[Byte] = matchTypeFromPartial[Byte] { case _: Byte => true }
  implicit val short: MatchType[Short] = matchTypeFromPartial[Short] { case _: Short => true }
  implicit val long: MatchType[Long] = matchTypeFromPartial { case _: Long => true }
  implicit val float: MatchType[Float] = matchTypeFromPartial[Float] { case _: Float => true }
  implicit val double: MatchType[Double] = matchTypeFromPartial[Double] { case _: Double => true }
  implicit val int: MatchType[Int] = matchTypeFromPartial[Int] { case _: Int => true }

  private[typelevel] def matchTypeFromPartial[T](pf: PartialFunction[Any, Boolean]): MatchType[T] = { a => pf.lift(a).getOrElse(false) }
}
