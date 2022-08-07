package sttp.tapir.grpc.protobuf.pbdirect

import sttp.tapir._
import pbdirect._
import java.io.InputStream
import java.io.ByteArrayInputStream

trait TapirProtobufPbDirect {
  def grpcBody[T](implicit codec: Codec[InputStream, T, CodecFormat.OctetStream]): EndpointIO.Body[InputStream, T] =
    EndpointIO.Body(RawBodyType.InputStreamBody, codec, EndpointIO.Info.empty)

  implicit def protobufPbDirectCodec[T](implicit
      schema: Schema[T],
      writer: PBMessageWriter[T],
      reader: PBMessageReader[T]
  ): Codec[InputStream, T, CodecFormat.OctetStream] =
    Codec.fromDecodeAndMeta[InputStream, T, CodecFormat.OctetStream](CodecFormat.OctetStream()) { x =>

      // it's only PoC support for gRPC in tapir, so I decided to not support multiple data frames in a single body at this point.
      // proper implementation that we will need to adopt in tapir can be found in akka.grpc.internal.AbstractGrpcProtocol#decoder
      val input = x.readAllBytes()
      println(s"RECEIVED [${{ input.mkString }}]")
      val sikppedDataFrameMetadata = input.drop(5)
      println(s"RECEIVED [${{ sikppedDataFrameMetadata.mkString }}]")

      DecodeResult.Value(reader.read(sikppedDataFrameMetadata))

    } { x =>
      val body = x.toPB
      val flags = 0.toByte // FIXME
      val lenght = body.length
      // FIXME copied form akka.util.ByteStringBuilder#putInt
      val lenghtBytes = Array(
        (lenght >>> 24).toByte,
        (lenght >>> 16).toByte,
        (lenght >>> 8).toByte,
        (lenght >>> 0).toByte
      )

      val output = lenghtBytes.prepended(flags) ++ body

      new ByteArrayInputStream(output)
    }
}
