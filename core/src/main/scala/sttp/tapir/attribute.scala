package sttp.tapir

import sttp.tapir.macros.AttributeKeyMacros

// TODO: use AttributeKey & AttributeMap from sttp-shared in Tapir2

/** @param typeName
  *   The fully qualified name of `T`.
  * @tparam T
  *   Type of the value of the attribute.
  */
class AttributeKey[T](val typeName: String) {
  override def equals(other: Any): Boolean = other match {
    case that: AttributeKey[_] => typeName == that.typeName
    case _                     => false
  }

  override def hashCode(): Int = typeName.hashCode
}

object AttributeKey extends AttributeKeyMacros

/** An attribute is arbitrary data that is attached to an endpoint or endpoint input/output. The data is not interpreted by tapir's core in
  * any way, but might be used by interpreters.
  *
  * Typically, you'll add attributes using [[Endpoint.attribute]] and [[EndpointTransput.Atom.attribute]]. The attribute keys should be
  * defined by the interpreters which are using them, and made available for import.
  */
case class AttributeMap private (private val storage: Map[String, Any]) {
  def get[T](k: AttributeKey[T]): Option[T] = storage.get(k.typeName).asInstanceOf[Option[T]]
  def put[T](k: AttributeKey[T], v: T): AttributeMap = copy(storage = storage + (k.typeName -> v))
  def remove[T](k: AttributeKey[T]): AttributeMap = copy(storage = storage - k.typeName)

  def isEmpty: Boolean = storage.isEmpty
  def nonEmpty: Boolean = storage.nonEmpty
}

object AttributeMap {
  val Empty: AttributeMap = AttributeMap(Map.empty)
}
