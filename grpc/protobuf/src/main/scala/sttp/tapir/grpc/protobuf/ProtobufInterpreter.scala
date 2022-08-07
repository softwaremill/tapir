package sttp.tapir.grpc.protobuf

import sttp.tapir.AnyEndpoint
import sttp.tapir.grpc.protobuf.model._

class ProtobufInterpreter(
  endpointToMsg: EndpointToProtobufMessage,
  endpointToService: EndpointToProtobufService
) {
  def toProtobuf(es: Iterable[AnyEndpoint]): Protobuf = {
      val messages = endpointToMsg(es.toList)
      val services = endpointToService(es.toList)
      
      Protobuf(
        messages = messages,
        services = services
      )
  }
}
