package sttp.tapir.docs.apispec.schema

import sttp.tapir.apispec.{Schema => ASchema, _}
import sttp.tapir._

import scala.collection.immutable.ListMap

object SchemasForEndpoints {
  def apply(es: Iterable[Endpoint[_, _, _, _]]): (ListMap[ObjectKey, ReferenceOr[ASchema]], Schemas) = {
    val sObjects = ObjectTypeData.unique(es.flatMap(e => forInput(e.input) ++ forOutput(e.errorOutput) ++ forOutput(e.output)))
    val infoToKey = calculateUniqueKeys(sObjects.map(_._1), objectInfoToName)
    val objectToSchemaReference = new ObjectToSchemaReference(infoToKey)
    val tschemaToASchema = new TSchemaToASchema(objectToSchemaReference)
    val schemas = new Schemas(tschemaToASchema, objectToSchemaReference)
    val infosToSchema = sObjects.map(td => (td._1, tschemaToASchema(td._2))).toListMap

    val schemaKeys = infosToSchema.map { case (k, v) => k -> ((infoToKey(k), v)) }
    (schemaKeys.values.toListMap, schemas)
  }

  private def forInput(input: EndpointInput[_, _]): List[ObjectTypeData] = {
    input match {
      case EndpointInput.FixedMethod(_, _, _)     => List.empty
      case EndpointInput.FixedPath(_, _, _)       => List.empty
      case EndpointInput.PathCapture(_, codec, _) => ObjectTypeData(codec)
      case EndpointInput.PathsCapture(_, _)       => List.empty
      case EndpointInput.Query(_, codec, _)       => ObjectTypeData(codec)
      case EndpointInput.Cookie(_, codec, _)      => ObjectTypeData(codec)
      case EndpointInput.QueryParams(_, _)        => List.empty
      case _: EndpointInput.Auth[_, _]            => List.empty
      case _: EndpointInput.ExtractFromRequest[_] => List.empty
      case EndpointInput.MappedPair(wrapped, _)   => forInput(wrapped)
      case EndpointInput.Pair(left, right, _, _)  => forInput(left) ++ forInput(right)
      case op: EndpointIO[_, _]                   => forIO(op)
    }
  }
  private def forOutput(output: EndpointOutput[_, _]): List[ObjectTypeData] = {
    output match {
      case EndpointOutput.OneOf(mappings, _)       => mappings.flatMap(mapping => forOutput(mapping.output)).toList
      case EndpointOutput.StatusCode(_, _, _)      => List.empty
      case EndpointOutput.FixedStatusCode(_, _, _) => List.empty
      case EndpointOutput.MappedPair(wrapped, _)   => forOutput(wrapped)
      case EndpointOutput.Void()                   => List.empty
      case EndpointOutput.Pair(left, right, _, _)  => forOutput(left) ++ forOutput(right)
      case EndpointOutput.WebSocketBodyWrapper(wrapped) =>
        ObjectTypeData(wrapped.codec) ++ ObjectTypeData(wrapped.requests) ++ ObjectTypeData(wrapped.responses)
      case op: EndpointIO[_, _] => forIO(op)
    }
  }

  private def forIO(io: EndpointIO[_, _]): List[ObjectTypeData] = {
    io match {
      case EndpointIO.Pair(left, right, _, _) => forIO(left) ++ forIO(right)
      case EndpointIO.Header(_, codec, _)     => ObjectTypeData(codec)
      case EndpointIO.Headers(_, _)           => List.empty
      case EndpointIO.Body(_, codec, _)       => ObjectTypeData(codec)
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, _)) =>
        ObjectTypeData(TypeData(codec.schema, Validator.pass))
      case EndpointIO.MappedPair(wrapped, _) => forIO(wrapped)
      case EndpointIO.FixedHeader(_, _, _)   => List.empty
      case EndpointIO.Empty(_, _)            => List.empty
    }
  }
}
