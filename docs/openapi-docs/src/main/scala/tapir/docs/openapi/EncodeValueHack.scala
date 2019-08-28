package tapir.docs.openapi

import tapir.{Codec, CodecForMany, CodecForOptional}

trait EncodeValueHack {
  type EncodeAny[T] = T => Option[Any]

  private def encodeValue[T](v: Any): Any = v.toString
  def encodeValue[T](codec: Codec[T, _, _], e: T): Option[Any] = Some(encodeValue(codec.encode(e)))
  def encodeValue[T](codec: CodecForOptional[T, _, _], e: T): Option[Any] = codec.encode(e).map(encodeValue)
  def encodeValue[T](codec: CodecForMany[T, _, _], e: T): Option[Any] =
    codec.encode(e).headOption.map(encodeValue)
}
