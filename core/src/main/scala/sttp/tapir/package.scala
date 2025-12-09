package sttp

import sttp.model.Part

package object tapir extends Tapir {
  // a part which contains one of the types supported by BodyType
  type RawPart = Part[?]
  type AnyPart = Part[?]
  // used in multipart codecs
  type AnyListCodec = Codec[? <: List[?], ?, ? <: CodecFormat]

  type AnyEndpoint = Endpoint[?, ?, ?, ?, ?]
  type PublicEndpoint[INPUT, ERROR_OUTPUT, OUTPUT, -R] = Endpoint[Unit, INPUT, ERROR_OUTPUT, OUTPUT, R]
}
