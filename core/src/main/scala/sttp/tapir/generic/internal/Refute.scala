package sttp.tapir.generic.internal

/** Evidence that no implicit instance of type `T` is available
  *
  *  @author Zainab Ali
  *  @see https://github.com/milessabin/shapeless/blob/e9cf7454cf02c5af3bb0723afe0325872fe452a4/core/src/main/scala/shapeless/refute.scala
  */
@annotation.implicitNotFound(msg = "Implicit instance for ${T} in scope.")
trait Refute[T]

object Refute {

  trait Impl[A]
  object Impl {

    /** This results in  ambigous implicits if there is implicit evidence of `T` */
    implicit def amb1[T](implicit ev: T): Impl[T] = null
    implicit def amb2[T]: Impl[T] = null
  }

  /** This always declares an instance of `Refute`
    *
    * This instance will only be found when there is no evidence of `T`
    */
  implicit def refute[T](implicit dummy: Impl[T]): Refute[T] = new Refute[T] {}
}
