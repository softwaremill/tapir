package sttp.tapir.grpc.protobuf.model

import sttp.tapir.grpc.protobuf.ProtobufScalarType

//Simplified model for PoC, should be used only for scalar values
case class ProtobufMessageField(
                                 `type`: ProtobufScalarType,
                                 name: String,
                                 maybeId: Option[Int]
                               )
