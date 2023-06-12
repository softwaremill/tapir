package sttp.tapir.docs.apispec.schema

import sttp.apispec.{Schema => ASchema, _}
import sttp.tapir.Schema.SName
import sttp.tapir._
import sttp.tapir.internal.IterableToListMap

import scala.collection.immutable.ListMap

class SchemasForEndpoints(
    es: Iterable[AnyEndpoint],
    schemaName: SName => String,
    toKeyedSchemas: ToKeyedSchemas,
    markOptionsAsNullable: Boolean,
    additionalOutputs: List[EndpointOutput[_]]
) {

  def apply(): (ListMap[SchemaId, ReferenceOr[ASchema]], Schemas) = {
    val keyedSchemas = ToKeyedSchemas.unique(
      es.flatMap(e =>
        forInput(e.securityInput) ++ forInput(e.input) ++ forOutput(e.errorOutput) ++ forOutput(e.output)
      ) ++ additionalOutputs.flatMap(forOutput(_))
    )
    val keysToIds = calculateUniqueIds(keyedSchemas.map(_._1), (key: SchemaKey) => schemaName(key.name))

    val toSchemaReference = new ToSchemaReference(keysToIds)
    val tschemaToASchema = new TSchemaToASchema(toSchemaReference, markOptionsAsNullable)
    val schemas = new Schemas(tschemaToASchema, toSchemaReference, markOptionsAsNullable)
    val keysToSchemas = keyedSchemas.map(td => (td._1, tschemaToASchema(td._2))).toListMap

    val schemaIds = keysToSchemas.map { case (k, v) => k -> ((keysToIds(k), v)) }
    (schemaIds.values.toListMap, schemas)
  }

  private def forInput(input: EndpointInput[_]): List[KeyedSchema] = {
    input match {
      case EndpointInput.FixedMethod(_, _, _)     => List.empty
      case EndpointInput.FixedPath(_, _, _)       => List.empty
      case EndpointInput.PathCapture(_, codec, _) => toKeyedSchemas(codec)
      case EndpointInput.PathsCapture(_, _)       => List.empty
      case EndpointInput.Query(_, _, codec, _)    => toKeyedSchemas(codec)
      case EndpointInput.Cookie(_, codec, _)      => toKeyedSchemas(codec)
      case EndpointInput.QueryParams(_, _)        => List.empty
      case _: EndpointInput.Auth[_, _]            => List.empty
      case _: EndpointInput.ExtractFromRequest[_] => List.empty
      case EndpointInput.MappedPair(wrapped, _)   => forInput(wrapped)
      case EndpointInput.Pair(left, right, _, _)  => forInput(left) ++ forInput(right)
      case op: EndpointIO[_]                      => forIO(op)
    }
  }
  private def forOutput(output: EndpointOutput[_]): List[KeyedSchema] = {
    output match {
      case EndpointOutput.OneOf(variants, _)       => variants.flatMap(variant => forOutput(variant.output)).toList
      case EndpointOutput.StatusCode(_, _, _)      => List.empty
      case EndpointOutput.FixedStatusCode(_, _, _) => List.empty
      case EndpointOutput.MappedPair(wrapped, _)   => forOutput(wrapped)
      case EndpointOutput.Void()                   => List.empty
      case EndpointOutput.Pair(left, right, _, _)  => forOutput(left) ++ forOutput(right)
      case EndpointOutput.WebSocketBodyWrapper(wrapped) =>
        toKeyedSchemas(wrapped.codec) ++ toKeyedSchemas(wrapped.requests) ++ toKeyedSchemas(wrapped.responses)
      case op: EndpointIO[_] => forIO(op)
    }
  }

  private def forIO(io: EndpointIO[_]): List[KeyedSchema] = {
    io match {
      case EndpointIO.Pair(left, right, _, _)                            => forIO(left) ++ forIO(right)
      case EndpointIO.Header(_, codec, _)                                => toKeyedSchemas(codec)
      case EndpointIO.Headers(_, _)                                      => List.empty
      case EndpointIO.Body(_, codec, _)                                  => toKeyedSchemas(codec)
      case EndpointIO.OneOfBody(variants, _)                             => variants.flatMap(v => forIO(v.bodyAsAtom))
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, _, _)) => toKeyedSchemas(codec.schema)
      case EndpointIO.MappedPair(wrapped, _)                             => forIO(wrapped)
      case EndpointIO.FixedHeader(_, _, _)                               => List.empty
      case EndpointIO.Empty(_, _)                                        => List.empty
    }
  }
}
