package sttp.tapir

import sttp.tapir.Codec.JsonCodec

case class DocsExtension[A](key: String, value: A, codec: JsonCodec[A]) {
  def rawValue: String = codec.encode(value)
}
object DocsExtension {
  def of[A](key: String, value: A)(implicit codec: JsonCodec[A]): DocsExtension[A] = DocsExtension(key, value, codec)
}
