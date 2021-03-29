package sttp.tapir

import scala.collection.immutable.ListMap

package object apispec {
  type ReferenceOr[T] = Either[Reference, T]

  // using a Vector instead of a List, as empty Lists are always encoded as nulls
  // here, we need them encoded as an empty array
  type SecurityRequirement = ListMap[String, Vector[String]]
}
