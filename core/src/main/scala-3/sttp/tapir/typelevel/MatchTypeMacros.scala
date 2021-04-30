package sttp.tapir.typelevel

trait MatchTypeMacros {
  implicit def gen[T]: MatchType[T] = ??? // TODO
}
