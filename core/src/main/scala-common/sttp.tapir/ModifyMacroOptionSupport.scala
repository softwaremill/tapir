package sttp.tapir

import scala.annotation.compileTimeOnly

trait ModifyMacroOptionSupport {
  implicit class ModifyEach[F[_], T](t: F[T])(implicit f: ModifyFunctor[F, T]) {
    @compileTimeOnly(canOnlyBeUsedInsideIgnore("each"))
    def each: T = sys.error("")
  }

  trait ModifyFunctor[F[_], A] {
    @compileTimeOnly(canOnlyBeUsedInsideIgnore("each"))
    def each(fa: F[A])(f: A => A): F[A] = sys.error("")
  }

  implicit def optionModifyFunctor[A]: ModifyFunctor[Option, A] =
    new ModifyFunctor[Option, A] {}

  private[tapir] def canOnlyBeUsedInsideIgnore(method: String) =
    s"$method can only be used inside ignore"
}
