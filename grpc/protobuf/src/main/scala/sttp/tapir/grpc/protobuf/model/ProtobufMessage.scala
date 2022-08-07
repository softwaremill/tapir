package sttp.tapir.grpc.protobuf.model

case class ProtobufMessage(name: MessageName, fields: Iterable[ProtobufMessageField])
