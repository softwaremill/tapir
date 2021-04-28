package sttp.tapir.typelevel

private[typelevel] trait GenericMatchType {
  implicit def gen[T]: MatchType[T] = ??? // TODO
}
