package sttp.tapir.generic

trait SealedTrait[TypeClass[_], T] {
  def dispatch(t: T): TypeClass[T]

  def subtypes: Map[String, TypeClass[scala.Any]]
}
