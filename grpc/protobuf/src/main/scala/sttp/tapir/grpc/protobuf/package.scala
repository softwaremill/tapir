package sttp.tapir.grpc

import sttp.tapir._

package object protobuf {
  def grpcBody[T](implicit codec: Codec[Array[Byte], T, CodecFormat.OctetStream]): EndpointIO.Body[Array[Byte], T] =
     EndpointIO.Body(RawBodyType.ByteArrayBody, codec, EndpointIO.Info.empty)

  //autgenerated codec will be available after proto file compilation - does it make sense to build in derivation into tapir?
  implicit def protobufCodec[T: Schema]: Codec[Array[Byte], T, CodecFormat.OctetStream] = 
    Codec.fromDecodeAndMeta[Array[Byte], T, CodecFormat.OctetStream](CodecFormat.OctetStream())(_ => ???)(_ => ???)
}
