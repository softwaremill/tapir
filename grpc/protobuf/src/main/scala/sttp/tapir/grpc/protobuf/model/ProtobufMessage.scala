package sttp.tapir.grpc.protobuf

import  sttp.tapir.grpc.protobuf.model._

case class ProtobufMessage(name: MessageName, fields: Iterable[ProtobufMessageField])
