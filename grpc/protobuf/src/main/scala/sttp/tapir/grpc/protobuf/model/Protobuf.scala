package sttp.tapir.grpc.protobuf

final case class Protobuf(messages: List[ProtobufMessage], services: List[ProtobufService])
