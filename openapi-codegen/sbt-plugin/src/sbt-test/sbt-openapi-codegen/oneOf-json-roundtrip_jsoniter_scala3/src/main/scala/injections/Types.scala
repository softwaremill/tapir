package injections

object Types {
  type A[T] = cats.effect.IO[T]

}
