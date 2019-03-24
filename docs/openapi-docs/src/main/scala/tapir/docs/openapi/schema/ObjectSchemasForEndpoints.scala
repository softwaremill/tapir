package tapir.docs.openapi.schema

import tapir.Schema.{SObject, SObjectInfo, SRef}
import tapir.{Schema => TSchema, _}
import tapir.docs.openapi.uniqueName

object ObjectSchemasForEndpoints {

  def apply(es: Iterable[Endpoint[_, _, _, _]]): ObjectSchemas = {
    val sObjectsForEndpoints = es.flatMap(e => forInput(e.input) ++ forOutput(e.errorOutput) ++ forOutput(e.output))
    val sObjectsAll = findNestedSObjects(Nil, sObjectsForEndpoints.toList).map(replaceSObjectFieldsWithSRef)
    val infoToKey = calculateUniqueKeys(sObjectsAll.map(_.info))

    val tschemaToOSchema = new TSchemaToOSchema(infoToKey.map { case (k, v) => k.fullName -> v })
    val infosToSchema = sObjectsAll.map(so => (so.info, tschemaToOSchema(so))).toMap
    val schemaKeys = infosToSchema.map { case (k, v) => k -> ((infoToKey(k), v)) }

    new ObjectSchemas(tschemaToOSchema, schemaKeys)
  }

  private def findNestedSObjects(acc: List[SObject], left: List[SObject]): List[SObject] = {
    def collectFieldObjects(x: SObject) = x.fields.collect { case (_, s: SObject) => s }.toList
    left match {
      case x :: xs =>
        findNestedSObjects(findNestedSObjects(acc :+ x, collectFieldObjects(x)), xs)
      case Nil => acc
    }
  }

  private def replaceSObjectFieldsWithSRef(obj: SObject): SObject = {
    val newFields = obj.fields map {
      case (s, o: SObject) => (s, SRef(o.info.fullName))
      case x               => x
    }
    obj.copy(fields = newFields)
  }

  private def calculateUniqueKeys(infos: Iterable[SObjectInfo]): Map[SObjectInfo, SchemaKey] = {
    case class SchemaKeyAssignment1(keyToInfo: Map[SchemaKey, SObjectInfo], infoToKey: Map[SObjectInfo, SchemaKey])
    infos
      .foldLeft(SchemaKeyAssignment1(Map.empty, Map.empty)) {
        case (SchemaKeyAssignment1(keyToInfo, infoToKey), objectInfo) =>
          val key = uniqueName(objectInfo.shortName, n => !keyToInfo.contains(n) || keyToInfo.get(n).contains(objectInfo))

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
      case EndpointInput.RequestMethod(_) =>
        List.empty
      case EndpointInput.PathSegment(_) =>
        List.empty
      case EndpointInput.PathCapture(tm, _, _) =>
        filterIsObjectSchema(tm.meta.schema)
      case EndpointInput.PathsCapture(_) =>
        List.empty
      case EndpointInput.Query(_, tm, _) =>
        filterIsObjectSchema(tm.meta.schema)
      case EndpointInput.Cookie(_, tm, _) =>
        filterIsObjectSchema(tm.meta.schema)
      case EndpointInput.QueryParams(_) =>
        List.empty
      case _: EndpointInput.Auth[_] =>
        List.empty
      case _: EndpointInput.ExtractFromRequest[_] =>
        List.empty
      case EndpointInput.Mapped(wrapped, _, _, _) =>
        forInput(wrapped)
      case EndpointInput.Multiple(inputs) =>
        inputs.toList.flatMap(forInput)
      case op: EndpointIO[_] => forIO(op)
    }
  }

  private def forOutput(output: EndpointOutput[_]): List[TSchema.SObject] = {
    output match {
      case EndpointOutput.StatusFrom(wrapped, _, defaultSchema, whens) =>
        val fromDefaultSchema = defaultSchema.toList.flatMap(filterIsObjectSchema)
        val fromWhens = whens.collect {
          case (WhenClass(_, s), _) => filterIsObjectSchema(s)
        }.flatten
        val fromInput = forOutput(wrapped)

        // if there's a default schema, we exclude the one from the input
        val fromInputOrDefault = if (fromDefaultSchema.nonEmpty) fromDefaultSchema else fromInput

        fromInputOrDefault ++ fromWhens
      case EndpointOutput.StatusCode() =>
        List.empty
      case EndpointOutput.Mapped(wrapped, _, _, _) =>
        forOutput(wrapped)
      case EndpointOutput.Multiple(outputs) =>
        outputs.toList.flatMap(forOutput)
      case op: EndpointIO[_] => forIO(op)
    }
  }

  private def forIO(io: EndpointIO[_]): List[TSchema.SObject] = {
    io match {
      case EndpointIO.Multiple(ios) =>
        ios.toList.flatMap(ios2 => forInput(ios2) ++ forOutput(ios2))
      case EndpointIO.Header(_, tm, _) =>
        filterIsObjectSchema(tm.meta.schema)
      case EndpointIO.Headers(_) =>
        List.empty
      case EndpointIO.Body(tm, _) =>
        filterIsObjectSchema(tm.meta.schema)
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(schema, _, _)) =>
        filterIsObjectSchema(schema)
      case EndpointIO.Mapped(wrapped, _, _, _) =>
        forInput(wrapped) ++ forOutput(wrapped)
    }
  }
}
