package sttp.tapir.grpc.protobuf

import sttp.tapir.{Endpoint, EndpointIO, EndpointInput}
import sttp.tapir.StreamBodyIO
import sttp.tapir.Codec
import sttp.tapir.SchemaType.SDateTime
import sttp.tapir.SchemaType.SProduct
import sttp.tapir.SchemaType.SDate
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.SchemaType.SBinary
import sttp.tapir.SchemaType.SArray
import sttp.tapir.SchemaType.SOption
import sttp.tapir.SchemaType.SRef
import sttp.tapir.SchemaType.SNumber
import sttp.tapir.SchemaType.SOpenProduct
import sttp.tapir.SchemaType.SInteger
import sttp.tapir.SchemaType.SBoolean
import sttp.tapir.SchemaType.SString
import sttp.tapir.grpc.protobuf.model.ProtobufMessageField

class EndpointToProtobufMessage {
  type AnyEndpoint = Endpoint[_, _, _, _, _]

  def apply(es: List[AnyEndpoint]): List[ProtobufMessage] = {
    es.flatMap(forEndpoint)
  }

  private def forEndpoint(e: AnyEndpoint): List[ProtobufMessage] =
    forInput(e.input)
  //   ++ forOutput(e.output) TODO

  private def forInput(input: EndpointInput[_]): List[ProtobufMessage] = {
    input match {
      case EndpointInput.FixedMethod(_, _, _) => List.empty
      case EndpointInput.FixedPath(_, _, _)   => List.empty
      case EndpointInput.PathCapture(_, codec, _) =>
        println(s"PATH_CAPTURE")
        ???
      case EndpointInput.PathsCapture(_, _) => List.empty
      case EndpointInput.Query(_, _, codec, _) =>
        println(s"Query")
        ???
      case EndpointInput.Cookie(_, codec, _) =>
        println(s"COOKIE")
        ???
      case EndpointInput.QueryParams(_, _)        => List.empty
      case _: EndpointInput.Auth[_, _]            => List.empty
      case _: EndpointInput.ExtractFromRequest[_] => List.empty
      case EndpointInput.MappedPair(wrapped, _) =>
        println(s"MAPPED_PAIR")
        ???
      case p @ EndpointInput.Pair(left, right, _, _) =>
        println(s"PAIR $p \n\n")
        forInput(left) ++ forInput(right)

      case op: EndpointIO[_] =>
        println(s"IO $op")
        forIO(op)
    }
  }

  private def forIO(io: EndpointIO[_]): List[ProtobufMessage] = {
    io match {
      case EndpointIO.Pair(left, right, _, _) => forIO(left) ++ forIO(right)
      case EndpointIO.Header(_, codec, _) =>
        println(s"HEADER")
        ???
      case EndpointIO.Headers(_, _) => List.empty
      case EndpointIO.Body(_, codec, _) =>
        println(s"BODY")
        fromCodec(codec)
      case EndpointIO.OneOfBody(variants, _) =>
        println(s"ONE_OF_BODY")
        variants.flatMap(v => forIO(v.bodyAsAtom))
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, _, _)) =>
        println(s"StreamBodyIO")
        ???
      case EndpointIO.MappedPair(wrapped, _) => forIO(wrapped)
      case EndpointIO.FixedHeader(_, _, _)   => List.empty
      case EndpointIO.Empty(_, _)            => List.empty
    }
  }

  private def fromCodec(codec: Codec[_, _, _]): List[ProtobufMessage] = {
    println(s"CODEC: $codec")
    val schema = codec.schema
    println(s"Schema: ${schema}")

    schema.schemaType match {
      case SProduct(fields) =>
        schema.name match {
          case None =>
            println(s"Missing name TOP")
            ???
          case Some(name) =>
            println(s"NAME TOP: $name")
            val protoFields = fields.map { field =>
              println(s"FIELD : $field")
              println(s"FIELD Schema: ${field.schema}")
              field.schema.schemaType match {
                case SString() => new ProtobufMessageField("string", field.name.name, None)
                case st => 
                    println(s"NOT SUPPORTED SCHEMA FIELD TYPE $st")
                    ???
              }

            }
            List(new ProtobufMessage(name.fullName.split('.').last, protoFields))//FIXME
        }
      case st =>
        println(s"NOT SUPPORTED SCHEMA TOP LEVEL TYOE $st")
        ???
    }

  }

}
