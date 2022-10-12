package sttp.tapir.grpc.protobuf.pbdirect

//import _root_.pbdirect._
import pbdirect.{PBMessageWriter, PBMessageReader, PBMessageWriterOps}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, EndpointIO, RawBodyType, Schema}

trait TapirProtobufPbDirect {
  def grpcBody[T: PBMessageReader: PBMessageWriter: Schema]: EndpointIO.Body[Array[Byte], T] =
    EndpointIO.Body(RawBodyType.ByteArrayBody, protobufPbDirectCodec[T], EndpointIO.Info.empty)

  implicit def protobufPbDirectCodec[T](implicit
      schema: Schema[T],
      writer: PBMessageWriter[T],
      reader: PBMessageReader[T]
  ): Codec[Array[Byte], T, CodecFormat.Grpc] =
    Codec.fromDecodeAndMeta[Array[Byte], T, CodecFormat.Grpc](CodecFormat.Grpc()) { input =>
      DecodeResult.Value(reader.read(input))
    }(_.toPB)
}
