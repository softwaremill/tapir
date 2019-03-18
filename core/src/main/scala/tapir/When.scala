package tapir
import scala.reflect.ClassTag

/**
  * Describe conditions for status code mapping using `Tapir.statusFrom`.
  */
trait When[-I] {
  def matches(i: I): Boolean
}

case class WhenClass[T](ct: ClassTag[T], s: Schema) extends When[Any] {
  override def matches(i: Any): Boolean = ct.runtimeClass.isInstance(i)
}
case class WhenValue[T](p: T => Boolean) extends When[T] {
  override def matches(i: T): Boolean = p(i)
}
