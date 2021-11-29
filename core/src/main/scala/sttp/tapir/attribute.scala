package sttp.tapir

import scala.reflect.ClassTag

trait AttributeKey[T]

object AttributeKey {
  def apply[T: ClassTag]: AttributeKey[T] = new AttributeKey[T] {
    override def toString: String = implicitly[ClassTag[T]].runtimeClass.getName
  }
}

/** An attribute is arbitrary data that is attached to an endpoint or endpoint input/output. The data is not interpreted by tapir's core in
  * any way, but might be used by interpreters.
  *
  * Typically, you'll add attributes using [[Endpoint.attribute]] and [[EndpointTransput.Basic.attribute]]. The attribute keys should be
  * defined by the interpreters which are using them, and made available for import.
  */
case class AttributeMap private (private val storage: Map[String, Any]) {
  def get[T](k: AttributeKey[T]): Option[T] = storage.get(k.toString).asInstanceOf[Option[T]]
  def put[T](k: AttributeKey[T], v: T): AttributeMap = copy(storage = storage + (k.toString -> v))
}

object AttributeMap {
  val Empty: AttributeMap = AttributeMap(Map.empty)
}
