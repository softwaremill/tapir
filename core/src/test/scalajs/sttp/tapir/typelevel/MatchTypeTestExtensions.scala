package sttp.tapir.typelevel

trait MatchTypeTestExtensions {
  // In JS land, double is the only primitive numeric type so we just ignore the other numeric types here.
  val matcherAndTypes: Seq[(MatchType[_], Any)] = Seq(
    implicitly[MatchType[String]] -> "string",
    implicitly[MatchType[Boolean]] -> true,
    implicitly[MatchType[Char]] -> 'c',
    implicitly[MatchType[Double]] -> 42.2d,
  )
}
