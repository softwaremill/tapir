package sttp.tapir.grpc.protobuf

import sttp.tapir.AnyEndpoint

class ProtobufInterpreter(
  endpointToMsg: EndpointToProtobufMessage
) {
  def toProtobuf(es: Iterable[AnyEndpoint]): Protobuf = {
      val messages = endpointToMsg(es.toList)
      
      Protobuf(
        messages = messages,
        services = List.empty
      )
  }
}
