package sttp.tapir.grpc.protobuf

import enumeratum._
import sttp.tapir.Schema.SName

sealed trait ProtobufType {
  def filedTypeName: String
}

case class ProtobufMessageRef(refName: SName) extends ProtobufType {
  override def filedTypeName: String = refName.show.split('.').last // FIXME we need to a better way for generating messages names
}
sealed trait ProtobufScalarType extends ProtobufType

object ProtobufScalarType {
  case object ProtobufString extends ProtobufScalarType {
    override val filedTypeName: String = "string"
  }

  case object ProtobufInt64 extends ProtobufScalarType {
    override val filedTypeName: String = "int64"
  }

  case object ProtobufInt32 extends ProtobufScalarType {
    override val filedTypeName: String = "int32"
  }

  case object ProtobufFloat extends ProtobufScalarType {
    override val filedTypeName: String = "float"
  }

  case object ProtobufDouble extends ProtobufScalarType {
    override val filedTypeName: String = "double"
  }

  case object ProtobufBool extends ProtobufScalarType {
    override val filedTypeName: String = "bool"
  }

  case object ProtobufEmpty extends ProtobufScalarType {
    override val filedTypeName: String = "google.protobuf.Empty"
  }

  case object ProtobufBytes extends ProtobufScalarType {
    override val filedTypeName: String = "bytes"
  }
}
