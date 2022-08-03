package sttp.tapir.grpc.protobuf.model

//Simplified model for PoC, should be used only for scalar values
case class ProtobufMessageField(
    `type`: ScalarType,
    name: String,
    maybeId: Option[Int]
)
