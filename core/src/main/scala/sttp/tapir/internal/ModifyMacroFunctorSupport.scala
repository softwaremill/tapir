package sttp.tapir.internal

import scala.annotation.compileTimeOnly

trait ModifyMacroFunctorSupport {
  implicit class ModifyEach[F[_], T](t: F[T])(implicit f: ModifyFunctor[F, T]) {
    @compileTimeOnly(canOnlyBeUsedInsideModify("each"))
    def each: T = sys.error("")
  }

  trait ModifyFunctor[F[_], A] {
    @compileTimeOnly(canOnlyBeUsedInsideModify("each"))
    def each(fa: F[A])(f: A => A): F[A] = sys.error("")
  }

  implicit def optionModifyFunctor[A]: ModifyFunctor[Option, A] =
    new ModifyFunctor[Option, A] {}

  private[tapir] def canOnlyBeUsedInsideModify(method: String) =
    s"$method can only be used inside ignore"
}
