package sttp.tapir.typelevel

trait MatchTypeTestExtensions {
  val t: Byte = 0xf.toByte
  val s: Short = 1

  val matcherAndTypes: Seq[(MatchType[_], Any)] = Seq(
    implicitly[MatchType[String]] -> "string",
    implicitly[MatchType[Boolean]] -> true,
    implicitly[MatchType[Char]] -> 'c',
    implicitly[MatchType[Byte]] -> t,
    implicitly[MatchType[Short]] -> s,
    implicitly[MatchType[Float]] -> 42.2f,
    implicitly[MatchType[Double]] -> 42.2d,
    implicitly[MatchType[Int]] -> 42
  )
}