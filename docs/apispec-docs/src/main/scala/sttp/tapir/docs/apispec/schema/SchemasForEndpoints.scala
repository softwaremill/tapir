package sttp.tapir.docs.apispec.schema

import sttp.tapir.SchemaType.SObjectInfo
import sttp.tapir._
import sttp.tapir.apispec.{Schema => ASchema, _}

import scala.collection.immutable.ListMap

class SchemasForEndpoints(
    val es: Iterable[Endpoint[_, _, _, _]],
    val schemaName: SObjectInfo => String,
    val enumsToComponents: Boolean
) {

  def apply(): (ListMap[ObjectKey, ReferenceOr[ASchema]], Schemas) = {
    val sObjects = toObjectSchema.unique(es.flatMap(e => forInput(e.input) ++ forOutput(e.errorOutput) ++ forOutput(e.output)))
    val infoToKey = calculateUniqueKeys(sObjects.map(_._1), schemaName)

    val objectToSchemaReference = new ObjectToSchemaReference(infoToKey)
    val tschemaToASchema = new TSchemaToASchema(objectToSchemaReference, enumsToComponents)
    val schemas = new Schemas(tschemaToASchema, objectToSchemaReference)
    val infosToSchema = sObjects.map(td => (td._1, tschemaToASchema(td._2))).toListMap

    val schemaKeys = infosToSchema.map { case (k, v) => k -> ((infoToKey(k), v)) }
    (schemaKeys.values.toListMap, schemas)
  }

  private def forInput(input: EndpointInput[_]): List[ObjectSchema] = {
    input match {
      case EndpointInput.FixedMethod(_, _, _)     => List.empty
      case EndpointInput.FixedPath(_, _, _)       => List.empty
      case EndpointInput.PathCapture(_, codec, _) => toObjectSchema(codec)
      case EndpointInput.PathsCapture(_, _)       => List.empty
      case EndpointInput.Query(_, codec, _)       => toObjectSchema(codec)
      case EndpointInput.Cookie(_, codec, _)      => toObjectSchema(codec)
      case EndpointInput.QueryParams(_, _)        => List.empty
      case _: EndpointInput.Auth[_]               => List.empty
      case _: EndpointInput.ExtractFromRequest[_] => List.empty
      case EndpointInput.MappedPair(wrapped, _)   => forInput(wrapped)
      case EndpointInput.Pair(left, right, _, _)  => forInput(left) ++ forInput(right)
      case op: EndpointIO[_]                      => forIO(op)
    }
  }
  private def forOutput(output: EndpointOutput[_]): List[ObjectSchema] = {
    output match {
      case EndpointOutput.OneOf(mappings, _)       => mappings.flatMap(mapping => forOutput(mapping.output)).toList
      case EndpointOutput.StatusCode(_, _, _)      => List.empty
      case EndpointOutput.FixedStatusCode(_, _, _) => List.empty
      case EndpointOutput.MappedPair(wrapped, _)   => forOutput(wrapped)
      case EndpointOutput.Void()                   => List.empty
      case EndpointOutput.Pair(left, right, _, _)  => forOutput(left) ++ forOutput(right)
      case EndpointOutput.WebSocketBodyWrapper(wrapped) =>
        toObjectSchema(wrapped.codec) ++ toObjectSchema(wrapped.requests) ++ toObjectSchema(wrapped.responses)
      case op: EndpointIO[_] => forIO(op)
    }
  }

  private def forIO(io: EndpointIO[_]): List[ObjectSchema] = {
    io match {
      case EndpointIO.Pair(left, right, _, _)                         => forIO(left) ++ forIO(right)
      case EndpointIO.Header(_, codec, _)                             => toObjectSchema(codec)
      case EndpointIO.Headers(_, _)                                   => List.empty
      case EndpointIO.Body(_, codec, _)                               => toObjectSchema(codec)
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, _)) => toObjectSchema(codec.schema)
      case EndpointIO.MappedPair(wrapped, _)                          => forIO(wrapped)
      case EndpointIO.FixedHeader(_, _, _)                            => List.empty
      case EndpointIO.Empty(_, _)                                     => List.empty
    }
  }

  private val toObjectSchema = new ToObjectSchema(enumsToComponents)
}
