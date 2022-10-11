package sttp.tapir.grpc.protobuf.model

case class ProtobufServiceMethod(name: MethodName, input: MessageReference, output: MessageReference)
