package sttp.tapir.generic

trait SealedTrait[TypeClass[_], T] {
  def dispatch(t: T): String
  def subtypes: Map[String, TypeClass[T]]
}
