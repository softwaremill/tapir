package tapir

import scala.collection.immutable

package object openapi {
  implicit class IterableToListMap[A](xs: Iterable[A]) {
    def toListMap[T, U](implicit ev: A <:< (T, U)): immutable.ListMap[T, U] = {
      val b = immutable.ListMap.newBuilder[T, U]
      for (x <- xs)
        b += x

      b.result()
    }
  }
}
