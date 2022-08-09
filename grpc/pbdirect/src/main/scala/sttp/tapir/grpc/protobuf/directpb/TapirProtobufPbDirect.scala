package sttp.tapir.grpc.protobuf.pbdirect

import pbdirect._
import sttp.tapir._

trait TapirProtobufPbDirect {
  def grpcBody[T](implicit codec: Codec[Array[Byte], T, CodecFormat.OctetStream]): EndpointIO.Body[Array[Byte], T] =
    EndpointIO.Body(RawBodyType.ByteArrayBody, codec, EndpointIO.Info.empty)

  implicit def protobufPbDirectCodec[T](implicit
      schema: Schema[T],
      writer: PBMessageWriter[T],
      reader: PBMessageReader[T]
  ): Codec[Array[Byte], T, CodecFormat.OctetStream] =
    Codec.fromDecodeAndMeta[Array[Byte], T, CodecFormat.OctetStream](CodecFormat.OctetStream()) { input =>

      // it's only PoC support for gRPC in tapir, so I decided to not support multiple data frames in a single body at this point.
      // proper implementation that we will need to adopt in tapir can be found in akka.grpc.internal.AbstractGrpcProtocol#decoder
      println(s"RECEIVED [${{ input.mkString }}]")
      val sikppedDataFrameMetadata = input.drop(5)
      println(s"RECEIVED [${{ sikppedDataFrameMetadata.mkString }}]")

      DecodeResult.Value(reader.read(sikppedDataFrameMetadata))

    }(_.toPB)
}
