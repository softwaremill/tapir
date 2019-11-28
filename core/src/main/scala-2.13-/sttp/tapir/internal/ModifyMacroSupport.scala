package sttp.tapir.internal

import scala.annotation.compileTimeOnly
import scala.collection.TraversableLike
import scala.collection.generic.CanBuildFrom

trait ModifyMacroSupport extends ModifyMacroFunctorSupport {
  implicit def traversableModifyFunctor[F[_], A](
      implicit cbf: CanBuildFrom[F[A], A, F[A]],
      ev: F[A] => TraversableLike[A, F[A]]
  ): ModifyFunctor[F, A] =
    new ModifyFunctor[F, A] {}

  trait ModifyMapAtFunctor[F[_, _], K, T] {
    @compileTimeOnly(canOnlyBeUsedInsideModify("each"))
    def each(fa: F[K, T])(f: T => T): F[K, T] = sys.error("")
  }

  implicit def mapModifyFunctor[M[KT, TT] <: Map[KT, TT], K, T](
      implicit cbf: CanBuildFrom[M[K, T], (K, T), M[K, T]]
  ): ModifyMapAtFunctor[M, K, T] = new ModifyMapAtFunctor[M, K, T] {}

  implicit class ModifyEachMap[F[_, _], K, T](t: F[K, T])(implicit f: ModifyMapAtFunctor[F, K, T]) {
    @compileTimeOnly(canOnlyBeUsedInsideModify("each"))
    def each: T = sys.error("")
  }
}
