package sttp.tapir.grpc.protobuf

import sttp.tapir.AnyEndpoint
import sttp.tapir.grpc.protobuf.model._

class ProtobufInterpreter(
  endpointToMsg: EndpointToProtobufMessage,
  endpointToService: EndpointToProtobufService
) {
  def toProtobuf(es: Iterable[AnyEndpoint], maybePackageName: Option[PackageName] = None): Protobuf = {
      val messages = endpointToMsg(es.toList)
      val services = endpointToService(es.toList)
      val options = ProtobufOptions(
        maybePackageName = maybePackageName
      )

      Protobuf(
        messages = messages,
        services = services,
        options = options
      )
  }
}
