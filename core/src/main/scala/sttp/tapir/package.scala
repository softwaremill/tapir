package sttp

import sttp.model.Part

package object tapir extends Tapir {
  // a part which contains one of the types supported by BodyType
  type RawPart = Part[_]
  type AnyPart = Part[_]
  // used in multipart codecs
  type AnyListCodec = Codec[_ <: List[_], _, _ <: CodecFormat]

  type AnyEndpoint = Endpoint[_, _, _, _, _]
  type PublicEndpoint[I, E, O, R] = Endpoint[Unit, I, E, O, R]
}
