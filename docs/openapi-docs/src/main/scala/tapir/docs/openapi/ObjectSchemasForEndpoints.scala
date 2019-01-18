package tapir.docs.openapi

import tapir.Schema.{SObjectInfo}
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{Schema => OSchema}
import tapir.{Schema => SSchema, _}

object ObjectSchemasForEndpoints {
  type SchemaKey = String
  type SchemaKeys = Map[SObjectInfo, (SchemaKey, ReferenceOr[OSchema])]

  def apply(es: Iterable[Endpoint[_, _, _]]): SchemaKeys = {
    val sObjects = es.flatMap(e => forInput(e.input) ++ forIO(e.errorOutput) ++ forIO(e.output))
    val infoToKey = calculateUniqueKeys(sObjects.map(_.info))
    val sschemaToOSchema = new SSchemaToOSchema(infoToKey.map { case (k, v) => k.fullName -> v })
    val infosToSchema = sObjects.flatMap(objectSchemaToOSchema(sschemaToOSchema)).toMap
    infosToSchema.map { case (k, v) => k -> (infoToKey(k) -> v) }
  }

  private def calculateUniqueKeys(infos: Iterable[SObjectInfo]) = {
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
  private def objectSchemaToOSchema(sschemaToOSSchema: SSchemaToOSchema)(
      schema: SSchema.SObject): Map[SObjectInfo, ReferenceOr[OSchema]] = {
    Map(schema.info -> sschemaToOSSchema(schema))
  }

  private def filterIsObjectSchema(schema: SSchema): List[SSchema.SObject] = {
    schema match {
      case s: SSchema.SObject =>
        List(s)
      case _ => List.empty
    }
  }

  private def forInput(input: EndpointInput[_]): List[SSchema.SObject] = {
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
        inputs.toList.flatMap(forInput)
      case op: EndpointIO[_] => forIO(op)
    }
  }

  private def forIO(io: EndpointIO[_]): List[SSchema.SObject] = {
    io match {
      case EndpointIO.Multiple(inputs) =>
        inputs.toList.flatMap(forInput)
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
}
