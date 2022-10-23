package sttp.tapir.grpc.protobuf

import enumeratum._

sealed trait ProtobufScalarType extends EnumEntry {
  def protoName: String
}

object ProtobufScalarType extends Enum[ProtobufScalarType] {
  case object ProtobufString extends ProtobufScalarType {
    override val protoName: String = "string"
  }

  case object ProtobufInt64 extends ProtobufScalarType {
    override val protoName: String = "int64"
  }

  case object ProtobufInt32 extends ProtobufScalarType {
    override val protoName: String = "int32"
  }

  case object ProtobufFloat extends ProtobufScalarType {
    override val protoName: String = "float"
  }

  case object ProtobufDouble extends ProtobufScalarType {
    override val protoName: String = "double"
  }

  case object ProtobufBool extends ProtobufScalarType {
    override val protoName: String = "bool"
  }

  case object ProtobufEmpty extends ProtobufScalarType {
    override val protoName: String = "google.protobuf.Empty"
  }

  case object ProtobufBytes extends ProtobufScalarType {
    override val protoName: String = "bytes"
  }

  override def values: IndexedSeq[ProtobufScalarType] = findValues
}
