package sttp.tapir

import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.bimap.BiMap

trait PathInputs {
  implicit def stringToPath(s: String): EndpointInput.FixedPath[Unit] = EndpointInput.FixedPath(s, Codec.idPlain(), EndpointIO.Info.empty)

  def path[T: Codec[String, *, TextPlain]]: EndpointInput.PathCapture[T] =
    EndpointInput.PathCapture(None, implicitly, EndpointIO.Info.empty)
  def path[T: Codec[String, *, TextPlain]](name: String): EndpointInput.PathCapture[T] =
    EndpointInput.PathCapture(Some(name), implicitly, EndpointIO.Info.empty)
  def paths: EndpointInput.PathsCapture[List[String]] = EndpointInput.PathsCapture(Codec.idPlain(), EndpointIO.Info.empty)

  def pathFromStringBiMap[V](inMap: BiMap[String, V]): EndpointInput[V] =
    EndpointInput
      .PathCapture(None, Codec.string.validate(stringBiMapValidator(inMap)), EndpointIO.Info.empty)
      .map(inMap.extractY _)(inMap.extractX)
  def pathFromStringBiMap[V](inMap: BiMap[String, V], name: String): EndpointInput[V] =
    EndpointInput
      .PathCapture(Some(name), Codec.string.validate(stringBiMapValidator(inMap)), EndpointIO.Info.empty)
      .map(inMap.extractY _)(inMap.extractX)

  def pathFromBiMap[K: PlainCodec, V](
    inMap: BiMap[K, V],
    encode: Option[Validator.EncodeToRaw[K]]
  ): EndpointInput[V] =
    EndpointInput
      .PathCapture(
        None,
        implicitly[PlainCodec[K]].validate(Validator.Enum[K](inMap.domain.toList, encode)),
        EndpointIO.Info.empty
      )
      .map(inMap.extractY _)(inMap.extractX)
  def pathFromBiMap[K: PlainCodec, V](
    inMap: BiMap[K, V],
    encode: Option[Validator.EncodeToRaw[K]],
    name: String
  ): EndpointInput[V] =
    EndpointInput
      .PathCapture(
        Some(name),
        implicitly[PlainCodec[K]].validate(Validator.Enum[K](inMap.domain.toList, encode)),
        EndpointIO.Info.empty
      )
      .map(inMap.extractY _)(inMap.extractX)

  private def stringBiMapValidator[V](inMap: BiMap[String, V]) = {
    Validator.Enum[String](inMap.domain.toList, Option(s => Some(s)))
  }
}
