package tapir.docs.openapi.schema

import tapir.Schema.SObjectInfo
import tapir.{Schema => TSchema, _}

object ObjectSchemasForEndpoints {

  def apply(es: Iterable[Endpoint[_, _, _]]): ObjectSchemas = {
    val sObjects = es.flatMap(e => forInput(e.input) ++ forIO(e.errorOutput) ++ forIO(e.output))
    val infoToKey = calculateUniqueKeys(sObjects.map(_.info))
    val tschemaToOSchema = new TSchemaToOSchema(infoToKey.map { case (k, v) => k.fullName -> v })
    val infosToSchema = sObjects.map(so => (so.info, tschemaToOSchema(so))).toMap
    val schemaKeys = infosToSchema.map { case (k, v) => k -> ((infoToKey(k), v)) }

    new ObjectSchemas(tschemaToOSchema, schemaKeys)
  }

  private def calculateUniqueKeys(infos: Iterable[SObjectInfo]): Map[SObjectInfo, SchemaKey] = {
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

  private def filterIsObjectSchema(schema: TSchema): List[TSchema.SObject] = {
    schema match {
      case s: TSchema.SObject =>
        List(s)
      case _ => List.empty
    }
  }

  private def forInput(input: EndpointInput[_]): List[TSchema.SObject] = {
    input match {
      case EndpointInput.PathSegment(_) =>
        List.empty
      case EndpointInput.PathCapture(tm, _, _) =>
        filterIsObjectSchema(tm.meta.schema)
      case EndpointInput.PathsCapture(_) =>
        List.empty
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

  private def forIO(io: EndpointIO[_]): List[TSchema.SObject] = {
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
