package sttp.tapir.grpc.protobuf.model

case class ProtobufOptions(maybePackageName: Option[PackageName])

object ProtobufOptions {
  val empty: ProtobufOptions = ProtobufOptions(None)
}