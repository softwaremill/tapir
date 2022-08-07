package sttp.tapir.grpc.protobuf.model

final case class Protobuf(messages: List[ProtobufMessage], services: List[ProtobufService])
