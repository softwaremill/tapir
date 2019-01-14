package tapir.docs.openapi

import tapir.Schema.SObjectInfo
import tapir.openapi.{Schema => OSchema}
import tapir.{Schema => SSchema, _}

object ObjectSchemasForEndpoints {
  type SchemaKey = String
  type SchemaKeys = Map[SObjectInfo, (SchemaKey, OSchema)]

  def apply(es: Iterable[Endpoint[_, _, _]]): SchemaKeys = {
    val infosToSchema = foldMaps(es.map(e => forInput(e.input) ++ forIO(e.errorOutput) ++ forIO(e.output)))

    // avoiding name clashes if two schemas
    case class SchemaKeyAssignment(keyToInfo: Map[SchemaKey, SObjectInfo], schemas: SchemaKeys)

    val result = infosToSchema.foldLeft(SchemaKeyAssignment(Map.empty, Map.empty)) {
      case (SchemaKeyAssignment(keyToInfo, schemas), (objectInfo, schema)) =>
        var key = objectInfo.shortName
        var i = 0
        while (keyToInfo.contains(key) && !keyToInfo.get(key).contains(objectInfo)) {
          i += 1
          key = objectInfo.shortName + i
        }

        SchemaKeyAssignment(
          keyToInfo + (key -> objectInfo),
          schemas + (objectInfo -> ((key, schema)))
        )
    }

    result.schemas
  }

  private def forInput(input: EndpointInput[_]): Map[SObjectInfo, OSchema] = {
    input match {
      case EndpointInput.PathSegment(_) =>
        Map.empty
      case EndpointInput.PathCapture(tm, _, _, _) =>
        objectSchemaToOSchema(tm.schema)
      case EndpointInput.Query(_, tm, _, _) =>
        objectSchemaToOSchema(tm.schema)
      case EndpointInput.Mapped(wrapped, _, _, _) =>
        forInput(wrapped)
      case EndpointInput.Multiple(inputs) =>
        foldMaps(inputs.map(forInput))
      case op: EndpointIO[_] => forIO(op)
    }
  }

  private def forIO(io: EndpointIO[_]): Map[SObjectInfo, OSchema] = {
    io match {
      case EndpointIO.Multiple(inputs) =>
        foldMaps(inputs.map(forInput))
      case EndpointIO.Header(_, tm, _, _) =>
        objectSchemaToOSchema(tm.schema)
      case EndpointIO.Body(tm, _, _) =>
        objectSchemaToOSchema(tm.schema)
      case EndpointIO.Mapped(wrapped, _, _, _) =>
        forInput(wrapped)
    }
  }

  private def objectSchemaToOSchema(schema: SSchema): Map[SObjectInfo, OSchema] = {
    schema match {
      case SSchema.SObject(info, _, _) =>
        Map(info -> SSchemaToOSchema(schema))
      case _ => Map.empty
    }
  }

  private def foldMaps[K, V](maps: Iterable[Map[K, V]]): Map[K, V] = {
    maps.foldLeft(Map.empty[K, V])(_ ++ _)
  }
}
