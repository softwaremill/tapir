package sttp.tapir

import sttp.tapir.model.Delimited

trait CodecExtensions2 {

  /** Creates a codec which handles values delimited using `D`. The implicit `T` -codec is used for handling each individual value.
    *
    * Upon decoding, the string is split using the delimiter, and then decoded using the `T` -codec. Upon encoding, the values are first
    * encoded using the `T` -codec, and then combined using the delimiter.
    *
    * The codec's schema has the `explode` attribute set to `false`.
    */
  implicit def delimited[D <: String, T](implicit
      codec: Codec[String, T, CodecFormat.TextPlain],
      delimiter: ValueOf[D]
  ): Codec[String, Delimited[D, T], CodecFormat.TextPlain] =
    Codec.string
      .map(_.split(delimiter.value).toList)(_.mkString(delimiter.value))
      .mapDecode(ls => DecodeResult.sequence(ls.map(codec.decode)).map(_.toList))(_.map(codec.encode))
      .schema(
        codec.schema
          .asIterable[List]
          .explode(false)
          .delimiter(delimiter.value)
      )
      .map(Delimited[D, T](_))(_.values)
}
