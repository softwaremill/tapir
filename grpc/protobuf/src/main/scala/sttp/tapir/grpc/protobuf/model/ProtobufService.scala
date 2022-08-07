package sttp.tapir.grpc.protobuf.model

case class ProtobufService(name: ServiceName, methods: List[ProtobufServiceMethod])
