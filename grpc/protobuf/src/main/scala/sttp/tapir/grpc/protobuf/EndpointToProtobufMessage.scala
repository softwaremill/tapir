package sttp.tapir.grpc.protobuf

import sttp.tapir.SchemaType.{SDate, SDateTime, SInteger, SNumber, SProduct, SProductField, SString}
import sttp.tapir.{Schema, _}
import sttp.tapir.grpc.protobuf.model._

import java.time.LocalDate

class EndpointToProtobufMessage {
  def apply(es: List[AnyEndpoint]): List[ProtobufMessage] =
    es.flatMap(forEndpoint)

  private def distinctBy[T, U](elements: List[T])(selector: T => U): List[T] = elements
    .groupBy(selector)
    .flatMap {
      case (_, Seq(el)) => Seq(el)
      case _ => Seq.empty[T]
    }
    .toList

  private def forEndpoint(e: AnyEndpoint): List[ProtobufMessage] =
    distinctBy(forInput(e.input) ++ forOutput(e.output))(_.name)

  private def forInput(input: EndpointInput[_]): List[ProtobufMessage] = {
    input match {
      case EndpointInput.FixedMethod(_, _, _) => List.empty
      case EndpointInput.FixedPath(_, _, _) => List.empty
      case EndpointInput.PathCapture(_, codec, _) => ???
      case EndpointInput.PathsCapture(_, _) => List.empty
      case EndpointInput.Query(_, _, codec, _) => ???
      case EndpointInput.Cookie(_, codec, _) => ???
      case EndpointInput.QueryParams(_, _) => List.empty
      case _: EndpointInput.Auth[_, _] => List.empty
      case _: EndpointInput.ExtractFromRequest[_] => List.empty
      case EndpointInput.MappedPair(wrapped, _) => ???
      case EndpointInput.Pair(left, right, _, _) => forInput(left) ++ forInput(right)
      case op: EndpointIO[_] => forIO(op)
    }
  }

  private def forOutput(output: EndpointOutput[_]): List[ProtobufMessage] = {
    output match {
      case EndpointOutput.OneOf(variants, _) => ???
      case EndpointOutput.StatusCode(_, _, _) => List.empty
      case EndpointOutput.FixedStatusCode(_, _, _) => List.empty
      case EndpointOutput.MappedPair(wrapped, _) => forOutput(wrapped)
      case EndpointOutput.Void() => List.empty
      case EndpointOutput.Pair(left, right, _, _) => forOutput(left) ++ forOutput(right)
      case EndpointOutput.WebSocketBodyWrapper(wrapped) => ???
      case op: EndpointIO[_] => forIO(op)
    }
  }

  private def forIO(io: EndpointIO[_]): List[ProtobufMessage] = {
    io match {
      case EndpointIO.Pair(left, right, _, _) => forIO(left) ++ forIO(right)
      case EndpointIO.Header(_, codec, _) => ???
      case EndpointIO.Headers(_, _) => List.empty
      case EndpointIO.Body(_, codec, _) => fromCodec(codec)
      case EndpointIO.OneOfBody(variants, _) => variants.flatMap(v => forIO(v.bodyAsAtom))
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, _, _)) => ???
      case EndpointIO.MappedPair(wrapped, _) => forIO(wrapped)
      case EndpointIO.FixedHeader(_, _, _) => List.empty
      case EndpointIO.Empty(_, _) => List.empty
    }
  }

  private def fromCodec(codec: Codec[_, _, _]): List[ProtobufMessage] = {
    val schema = codec.schema

    schema.schemaType match {
      case SProduct(fields) =>
        schema.name match {
          case None => ???
          case Some(name) =>
            val protoFields = fields.map { field =>
              //TODO files support?
              fromProductField(field)
            }
            List(ProtobufMessage(name.fullName.split('.').last, protoFields)) // FIXME
        }
      case _ => ???
    }

  }
  private def defaultScalarMappings(field: SProductField[_]): ProtobufScalarType = field.schema.schemaType match {
    case SString() => ProtobufScalarType.ProtobufString
    case SInteger() if field.schema.format.contains("int64") => ProtobufScalarType.ProtobufInt64
    case SInteger() => ProtobufScalarType.ProtobufInt32
    case SNumber() if field.schema.format.contains("float") => ProtobufScalarType.ProtobufFloat
    case SNumber() => ProtobufScalarType.ProtobufDouble
    case SchemaType.SBoolean() => ProtobufScalarType.ProtobufBool
    case SProduct(Nil) => ProtobufScalarType.ProtobufEmpty
    case SchemaType.SBinary() => ProtobufScalarType.ProtobufBytes
    case SDateTime() => ProtobufScalarType.ProtobufInt64
    case SDate() => ProtobufScalarType.ProtobufInt64
    case in =>
      println(s"Not supported input [$in]") //FIXME
    ???
  }



  private def fromProductField(field: SProductField[_]): ProtobufMessageField = {
    val scalarType = field.schema.attribute(ProtobufAttributes.ScalarValueAttribute).getOrElse(defaultScalarMappings(field))

    ProtobufMessageField(scalarType, field.name.name, None)
  }

}
