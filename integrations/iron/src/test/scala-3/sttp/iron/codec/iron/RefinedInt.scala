package sttp.iron.codec.iron

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type RefinedIntConstraint = Interval.ClosedOpen[0, 10]
object RefinedInt extends RefinedType[Int, RefinedIntConstraint]
type RefinedInt = RefinedInt.T
