package tapir.docs.openapi.schema

import tapir.Codec.PlainCodec
import tapir.CodecForMany.PlainCodecForMany
import tapir.docs.openapi.uniqueName
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{Schema => OSchema}
import tapir.{Schema => TSchema, _}

import scala.collection.immutable
import scala.collection.immutable.ListMap

object ObjectSchemasForEndpoints {
  def apply(es: Iterable[Endpoint[_, _, _, _]]): (ListMap[SchemaKey, ReferenceOr[OSchema]], ObjectSchemas) = {
    val sObjects = es.flatMap(e => forInput(e.input) ++ forOutput(e.errorOutput) ++ forOutput(e.output))
    val infoToKey = calculateUniqueKeys(sObjects.map(_.schema.info))
    val schemaReferences = new SchemaReferenceMapper(infoToKey)
    val discriminatorToOpenApi = new DiscriminatorToOpenApi(schemaReferences)
    val tschemaToOSchema = new TSchemaToOSchema(schemaReferences, discriminatorToOpenApi)
    val schemas = new ObjectSchemas(tschemaToOSchema, schemaReferences)
    val infosToSchema = sObjects.map(td => (td.schema.info, tschemaToOSchema(td))).toMap

    val schemaKeys = infosToSchema.map { case (k, v) => k -> ((infoToKey(k), v)) }
    (schemaKeys.values.toListMap, schemas)
  }

  private def calculateUniqueKeys(infos: Iterable[TSchema.SObjectInfo]): Map[TSchema.SObjectInfo, SchemaKey] = {
    case class SchemaKeyAssignment1(keyToInfo: Map[SchemaKey, TSchema.SObjectInfo], infoToKey: Map[TSchema.SObjectInfo, SchemaKey])
    infos
      .foldLeft(SchemaKeyAssignment1(Map.empty, Map.empty)) {
        case (SchemaKeyAssignment1(keyToInfo, infoToKey), objectInfo) =>
          val key = uniqueName(objectInfoToName(objectInfo), n => !keyToInfo.contains(n) || keyToInfo.get(n).contains(objectInfo))

          SchemaKeyAssignment1(
            keyToInfo + (key -> objectInfo),
            infoToKey + (objectInfo -> key)
          )
      }
      .infoToKey
  }

  private def objectSchemas(typeData: AnyTypeData[_]): List[ObjectTypeData[_]] = {
    typeData match {
      case TypeData(s: TSchema.SProduct, validator) =>
        List(TypeData(s, validator): ObjectTypeData[_]) ++ fieldsSchemaWithValidator(s, validator)
          .flatMap(objectSchemas)
          .toList
      case TypeData(TSchema.SArray(o), validator) =>
        objectSchemas(TypeData(o, elementValidator(validator)))
      case TypeData(s: TSchema.SCoproduct, validator) =>
        (TypeData(s, validator): ObjectTypeData[_]) +: s.schemas.flatMap(c => objectSchemas(TypeData(c, Validator.pass))).toList
      case TypeData(s: TSchema.SOpenProduct, validator) =>
        (TypeData(s, validator): ObjectTypeData[_]) +: objectSchemas(TypeData(s.valueSchema, elementValidator(validator)))
      case _ => List.empty
    }
  }

  private def fieldsSchemaWithValidator(p: TSchema.SProduct, v: Validator[_]): immutable.Seq[AnyTypeData[_]] = {
    v match {
      case Validator.Product(validatedFields) =>
        p.fields.map { f =>
          TypeData(f._2, validatedFields.get(f._1).map(_.validator).getOrElse(Validator.pass))
        }.toList
      case _ => p.fields.map(f => TypeData(f._2, Validator.pass)).toList
    }
  }

  private def forInput(input: EndpointInput[_]): List[ObjectTypeData[_]] = {
    input match {
      case EndpointInput.FixedMethod(_)           => List.empty
      case EndpointInput.FixedPath(_)             => List.empty
      case EndpointInput.PathCapture(codec, _, _) => forCodec(codec)
      case EndpointInput.PathsCapture(_)          => List.empty
      case EndpointInput.Query(_, codec, _)       => forCodec(codec)
      case EndpointInput.Cookie(_, codec, _)      => forCodec(codec)
      case EndpointInput.QueryParams(_)           => List.empty
      case _: EndpointInput.Auth[_]               => List.empty
      case _: EndpointInput.ExtractFromRequest[_] => List.empty
      case EndpointInput.Mapped(wrapped, _, _)    => forInput(wrapped)
      case EndpointInput.Multiple(inputs)         => inputs.toList.flatMap(forInput)
      case op: EndpointIO[_]                      => forIO(op)
    }
  }
  private def forOutput(output: EndpointOutput[_]): List[ObjectTypeData[_]] = {
    output match {
      case EndpointOutput.OneOf(mappings)       => mappings.flatMap(mapping => forOutput(mapping.output)).toList
      case EndpointOutput.StatusCode()          => List.empty
      case EndpointOutput.FixedStatusCode(_, _) => List.empty
      case EndpointOutput.Mapped(wrapped, _, _) => forOutput(wrapped)
      case EndpointOutput.Void()                => List.empty
      case EndpointOutput.Multiple(outputs)     => outputs.toList.flatMap(forOutput)
      case op: EndpointIO[_]                    => forIO(op)
    }
  }

  private def forIO(io: EndpointIO[_]): List[ObjectTypeData[_]] = {
    io match {
      case EndpointIO.Multiple(ios)                                             => ios.toList.flatMap(ios2 => forInput(ios2) ++ forOutput(ios2))
      case EndpointIO.Header(_, codec, _)                                       => forCodec(codec)
      case EndpointIO.Headers(_)                                                => List.empty
      case EndpointIO.Body(codec, _)                                            => forCodec(codec)
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(schema, _, _)) => objectSchemas(TypeData(schema, Validator.pass))
      case EndpointIO.Mapped(wrapped, _, _)                                     => forInput(wrapped) ++ forOutput(wrapped)
      case EndpointIO.FixedHeader(_, _, _)                                      => List.empty
    }
  }

  private def forCodec[T](codec: CodecForOptional[T, _, _]): List[ObjectTypeData[_]] = objectSchemas(TypeData(codec))
  private def forCodec[T](codec: PlainCodec[T]): List[ObjectTypeData[_]] = objectSchemas(TypeData(codec))
  private def forCodec[T](codec: PlainCodecForMany[T]): List[ObjectTypeData[_]] = objectSchemas(TypeData(codec))

  private def objectInfoToName(info: TSchema.SObjectInfo): String = {
    val shortName = info.fullName.split('.').last
    (shortName +: info.typeParameterShortNames).mkString("_")
  }
}
