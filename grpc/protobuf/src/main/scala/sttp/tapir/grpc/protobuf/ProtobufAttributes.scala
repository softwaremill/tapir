package sttp.tapir.grpc.protobuf

import sttp.tapir.AttributeKey

object ProtobufAttributes {
  val ScalarValueAttribute = new AttributeKey[ProtobufScalarType]("scalar-type")
}
