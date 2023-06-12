package sttp.tapir.grpc.protobuf.model

import sttp.tapir.grpc.protobuf.ProtobufMessageRef

sealed trait ProtobufMessage {
  def name: MessageName
}

case class ProtobufProductMessage(name: MessageName, fields: Iterable[ProtobufMessageField]) extends ProtobufMessage
case class ProtobufCoproductMessage(name: MessageName, alternatives: Iterable[ProtobufMessageField]) extends ProtobufMessage
