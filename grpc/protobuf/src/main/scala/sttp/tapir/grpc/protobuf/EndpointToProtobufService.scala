package sttp.tapir.grpc.protobuf

import sttp.tapir._
import sttp.tapir.grpc.protobuf.model._
import sttp.tapir.EndpointIO.Pair
import sttp.tapir.EndpointIO.Empty
import sttp.tapir.EndpointIO.Body
import sttp.tapir.EndpointIO.Headers
import sttp.tapir.EndpointIO.Header
import sttp.tapir.EndpointIO.StreamBodyWrapper
import sttp.tapir.EndpointIO.FixedHeader
import sttp.tapir.EndpointIO.OneOfBody
import sttp.tapir.EndpointIO.MappedPair
import sttp.tapir.EndpointInput.Pair
import sttp.tapir.EndpointInput.Auth
import sttp.tapir.EndpointInput.FixedPath
import sttp.tapir.EndpointInput.QueryParams
import sttp.tapir.EndpointInput.Query
import sttp.tapir.EndpointInput.PathsCapture
import sttp.tapir.EndpointInput.FixedMethod
import sttp.tapir.EndpointInput.ExtractFromRequest
import sttp.tapir.EndpointInput.PathCapture
import sttp.tapir.EndpointInput.Cookie
import sttp.tapir.EndpointInput.MappedPair
import scala.collection.immutable
import cats.instances.list

class EndpointToProtobufService {
  def apply(es: List[AnyEndpoint]): List[ProtobufService] = mergeServices(es.map(forEndpoint))

  private def mergeServices(services: List[ProtobufService]): List[ProtobufService] =
    services
      .groupBy(_.name)
      .map { case (name, services) => ProtobufService(name, services.flatMap(_.methods)) }
      .toList

  private def forEndpoint(e: AnyEndpoint): ProtobufService = {
    val (serviceName, methodName) = extractServiceNameAndMethodName(e.input).headOption.getOrElse(???)
    val inputBody = extractInput(e.input).headOption.getOrElse(???)
    val outputBody = extractOutput(e.output).headOption.getOrElse(???)

    ProtobufService(
      name = serviceName,
      methods = List(
        ProtobufServiceMethod(
          name = methodName,
          input = inputBody,
          output = outputBody
        )
      )
    )
  }

  private def extractServiceNameAndMethodName(input: EndpointInput[_]): List[(ServiceName, MethodName)] = {
    input match {
      case EndpointInput.Pair(EndpointInput.FixedPath(serviceName, _, _), EndpointInput.FixedPath(methodName, _, _), _, _) =>
        List((serviceName, methodName))
      case EndpointInput.Pair(left, right, _, _) => extractServiceNameAndMethodName(left) ++ extractServiceNameAndMethodName(right)
      case _                                     => List.empty
    }
  }

  private def extractInput(input: EndpointInput[_]): List[MessageReference] = {
    input match {
      case EndpointInput.Pair(left, right, _, _) => extractInput(left) ++ extractInput(right)
      case op: EndpointIO[_] => forIO(op)
      case _                 => List.empty
    }
  }

  private def extractOutput(output: EndpointOutput[_]): List[MessageReference] = {
    output match {
      case EndpointOutput.MappedPair(wrapped, _)        => extractOutput(wrapped)
      case EndpointOutput.Pair(left, right, _, _)       => extractOutput(left) ++ extractOutput(right)
      case op: EndpointIO[_]                     => forIO(op)
      case _                                     => List.empty
    }
  }

  private def forIO(io: EndpointIO[_]): List[MessageReference] = {
    io match {
      case EndpointIO.Body(_, codec, _)      => List(fromCodec(codec))
      case EndpointIO.MappedPair(wrapped, _) => forIO(wrapped)
      case _                                 => List.empty
    }
  }

  private def fromCodec(codec: Codec[_, _, _]): MessageReference = {
    val schema = codec.schema

    schema.name match {
      case None       => ???
      case Some(name) => name.fullName.split('.').last // FIXME
    }

  }
}
