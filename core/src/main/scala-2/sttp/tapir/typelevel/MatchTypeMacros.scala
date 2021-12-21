package sttp.tapir.typelevel

import magnolia1.Magnolia
import sttp.tapir.typelevel.internal.MatchTypeMagnoliaDerivation

trait MatchTypeMacros extends MatchTypeMagnoliaDerivation {
  implicit def gen[T]: MatchType[T] = macro Magnolia.gen[T]
}
