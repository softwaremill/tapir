package sttp.tapir

import scala.reflect.ClassTag

/** @param name
  *   The fully qualified name of `T`.
  * @tparam T
  *   Type of the value of the attribute.
  */
case class AttributeKey[T](name: String)

object AttributeKey {
  def forClass[T: ClassTag]: AttributeKey[T] = AttributeKey[T](implicitly[ClassTag[T]].runtimeClass.getName)
}

/** An attribute is arbitrary data that is attached to an endpoint or endpoint input/output. The data is not interpreted by tapir's core in
  * any way, but might be used by interpreters.
  *
  * Typically, you'll add attributes using [[Endpoint.attribute]] and [[EndpointTransput.Basic.attribute]]. The attribute keys should be
  * defined by the interpreters which are using them, and made available for import.
  */
case class AttributeMap private (private val storage: Map[String, Any]) {
  def get[T](k: AttributeKey[T]): Option[T] = storage.get(k.name).asInstanceOf[Option[T]]
  def put[T](k: AttributeKey[T], v: T): AttributeMap = copy(storage = storage + (k.name -> v))

  def isEmpty: Boolean = storage.isEmpty
  def nonEmpty: Boolean = storage.nonEmpty
}

object AttributeMap {
  val Empty: AttributeMap = AttributeMap(Map.empty)
}
