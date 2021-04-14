package sttp.tapir

import sttp.tapir.Codec.JsonCodec

case class Extension[A](key: String, value: A, codec: JsonCodec[A]) {
  def rawValue: String = codec.encode(value)
}
object Extension {
  def of[A](key: String, value: A)(implicit codec: JsonCodec[A]): Extension[A] = Extension(key, value, codec)
}