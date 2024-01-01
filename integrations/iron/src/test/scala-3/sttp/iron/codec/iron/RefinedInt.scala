package sttp.iron.codec.iron

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type RefinedIntConstraint = Interval.ClosedOpen[0, 10]
opaque type RefinedInt <: Int = Int :| RefinedIntConstraint
object RefinedInt extends RefinedTypeOps[Int, RefinedIntConstraint, RefinedInt]
