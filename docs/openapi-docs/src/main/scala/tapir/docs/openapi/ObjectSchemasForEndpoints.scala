package tapir.docs.openapi

import tapir.Schema.{SObject, SObjectInfo}
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{Schema => OSchema}
import tapir.{Schema => SSchema, _}

import scala.collection.immutable

object ObjectSchemasForEndpoints {
  type SchemaKey = String
  type SchemaKeys = Map[SObjectInfo, (SchemaKey, ReferenceOr[OSchema])]

  def apply(es: Iterable[Endpoint[_, _, _]]): SchemaKeys = {
    val schemas = foldLists(es.map(e => forInput(e.input) ++ forIO(e.errorOutput) ++ forIO(e.output)))
    val infos = schemas.collect { case SObject(info, _, _) => info }
    val infoToKey = calculateUniqueKeys(infos)
    val sschemaToOSchema = new SSchemaToOSchema(infoToKey.map { case (k, v) => k.fullName -> v })
    val infosToSchema = schemas.flatMap(objectSchemaToOSchema(sschemaToOSchema)).toMap
    infosToSchema.map { case (k, v) => k -> (infoToKey(k) -> v) }
  }

  private def calculateUniqueKeys(infos: immutable.Seq[SObjectInfo]) = {
    case class SchemaKeyAssignment1(keyToInfo: Map[SchemaKey, SObjectInfo], infoToKey: Map[SObjectInfo, SchemaKey])
    infos
      .foldLeft(SchemaKeyAssignment1(Map.empty, Map.empty)) {
        case (SchemaKeyAssignment1(keyToInfo, infoToKey), objectInfo) =>
          var key = objectInfo.shortName
          var i = 0
          while (keyToInfo.contains(key) && !keyToInfo.get(key).contains(objectInfo)) {
            i += 1
            key = objectInfo.shortName + i
          }

          SchemaKeyAssignment1(
            keyToInfo + (key -> objectInfo),
            infoToKey + (objectInfo -> key)
          )
      }
      .infoToKey
  }
  private def objectSchemaToOSchema(sschemaToOSSchema: SSchemaToOSchema)(schema: SSchema): Map[SObjectInfo, ReferenceOr[OSchema]] = {
    schema match {
      case SSchema.SObject(info, _, _) =>
        Map(info -> sschemaToOSSchema(schema))
      case _ => Map.empty
    }
  }

  private def filterIsObjectSchema(schema: SSchema): List[SSchema] = {
    schema match {
      case SSchema.SObject(info, _, _) =>
        List(schema)
      case _ => List.empty
    }
  }

  private def forInput(input: EndpointInput[_]): List[SSchema] = {
    input match {
      case EndpointInput.PathSegment(_) =>
        List.empty
      case EndpointInput.PathCapture(tm, _, _) =>
        filterIsObjectSchema(tm.meta.schema)
      case EndpointInput.Query(_, tm, _) =>
        filterIsObjectSchema(tm.meta.schema)
      case EndpointInput.QueryParams(_) =>
        List.empty
      case EndpointInput.Mapped(wrapped, _, _, _) =>
        forInput(wrapped)
      case EndpointInput.Multiple(inputs) =>
        foldLists(inputs.map(forInput))
      case op: EndpointIO[_] => forIO(op)
    }
  }

  private def forIO(io: EndpointIO[_]): List[SSchema] = {
    io match {
      case EndpointIO.Multiple(inputs) =>
        foldLists(inputs.map(forInput))
      case EndpointIO.Header(_, tm, _) =>
        filterIsObjectSchema(tm.meta.schema)
      case EndpointIO.Headers(_) =>
        List.empty
      case EndpointIO.Body(tm, _) =>
        filterIsObjectSchema(tm.meta.schema)
      case EndpointIO.Mapped(wrapped, _, _, _) =>
        forInput(wrapped)
    }
  }

  private def foldLists[K](maps: Iterable[List[K]]): List[K] = {
    maps.foldLeft(List.empty[K])(_ ++ _)
  }
}
