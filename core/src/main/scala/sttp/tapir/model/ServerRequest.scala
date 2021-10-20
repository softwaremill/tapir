package sttp.tapir.model

import sttp.model.headers.Accepts
import sttp.model.{ContentTypeRange, QueryParams, RequestMetadata}

import java.net.InetSocketAddress
import scala.collection.immutable.Seq
import scala.reflect.ClassTag

trait ServerRequest extends RequestMetadata {
  def protocol: String
  def connectionInfo: ConnectionInfo
  def underlying: Any

  /** Can differ from `uri.path`, if the endpoint is deployed in a context */
  def pathSegments: List[String]
  def queryParameters: QueryParams

  lazy val acceptsContentTypes: Either[String, Seq[ContentTypeRange]] = Accepts.parse(headers)

  /** Read an attribute of a request, if it has previously been added using [[withAttribute]], e.g. in an interceptor. */
  def attribute[T](key: AttributeKey[T]): Option[T]

  /** Add an attribute to this request. The attribute can be later read using `extractRequiredAttribute` and `extractOptionalAttribute`. */
  def withAttribute[T](key: AttributeKey[T], value: T): ServerRequest
}

case class ConnectionInfo(local: Option[InetSocketAddress], remote: Option[InetSocketAddress], secure: Option[Boolean])

object ConnectionInfo {

  /** When no info can be obtained from context. */
  val NoInfo: ConnectionInfo = ConnectionInfo(None, None, None)
}

class AttributeKey[T: ClassTag] {
  def asString: String = implicitly[ClassTag[T]].runtimeClass.getName
  def asShortString: String = implicitly[ClassTag[T]].runtimeClass.getSimpleName
}
object AttributeKey {
  def apply[T: ClassTag]: AttributeKey[T] = new AttributeKey[T]
}

/** A map keyed by types (through [[AttributeKey]]). */
class AttributeMap(underlying: Map[String, Any]) {
  def this() = this(Map.empty)

  def get[T](key: AttributeKey[T]): Option[T] = underlying.get(key.asString).asInstanceOf[Option[T]]
  def put[T](key: AttributeKey[T], value: T): AttributeMap = new AttributeMap(underlying + (key.asString -> value))
}
