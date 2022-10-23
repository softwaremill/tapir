package sttp.tapir.grpc.protobuf.model

import sttp.tapir.grpc.protobuf.ProtobufType

//Simplified model for PoC, should be used only for scalar values
case class ProtobufMessageField(
                                 `type`: ProtobufType,
                                 name: String,
                                 maybeId: Option[Int]
                               )
