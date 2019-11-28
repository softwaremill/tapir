package sttp.tapir.internal

import scala.annotation.compileTimeOnly
import scala.collection.Factory

trait ModifyMacroSupport extends ModifyMacroFunctorSupport {
  implicit def traversableModifyFunctor[F[_], A](
                                                  implicit fac: Factory[A, F[A]],
                                                  ev: F[A] => Iterable[A]
                                                ): ModifyFunctor[F, A] =
    new ModifyFunctor[F, A] {}

  implicit class ModifyEachMap[F[_, _], K, T](t: F[K, T])(implicit fac: Factory[(K, T), F[K, T]]) {
    @compileTimeOnly(canOnlyBeUsedInsideModify("each"))
    def each: T = sys.error("")
  }
}
