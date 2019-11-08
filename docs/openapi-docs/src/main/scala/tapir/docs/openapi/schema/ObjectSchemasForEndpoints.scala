package tapir.docs.openapi.schema

import tapir.Codec.PlainCodec
import tapir.CodecForMany.PlainCodecForMany
import tapir.docs.openapi.uniqueName
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{Schema => OSchema}
import tapir.{SchemaType => TSchemaType, _}

import scala.collection.immutable
import scala.collection.immutable.ListMap

object ObjectSchemasForEndpoints {
  private type ObjectTypeData = (TSchemaType.SObjectInfo, AnyTypeData[_])

  def apply(es: Iterable[Endpoint[_, _, _, _]]): (ListMap[SchemaKey, ReferenceOr[OSchema]], ObjectSchemas) = {
    val sObjects = es.flatMap(e => forInput(e.input) ++ forOutput(e.errorOutput) ++ forOutput(e.output))
    val infoToKey = calculateUniqueKeys(sObjects.map(_._1))
    val schemaReferences = new SchemaReferenceMapper(infoToKey)
    val discriminatorToOpenApi = new DiscriminatorToOpenApi(schemaReferences)
    val tschemaToOSchema = new TSchemaToOSchema(schemaReferences, discriminatorToOpenApi)
    val schemas = new ObjectSchemas(tschemaToOSchema, schemaReferences)
    val infosToSchema = sObjects.map(td => (td._1, tschemaToOSchema(td._2))).toMap

    val schemaKeys = infosToSchema.map { case (k, v) => k -> ((infoToKey(k), v)) }
    (schemaKeys.values.toListMap, schemas)
  }

  private def calculateUniqueKeys(infos: Iterable[TSchemaType.SObjectInfo]): Map[TSchemaType.SObjectInfo, SchemaKey] = {
    case class SchemaKeyAssignment1(keyToInfo: Map[SchemaKey, TSchemaType.SObjectInfo], infoToKey: Map[TSchemaType.SObjectInfo, SchemaKey])
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

  private def objectSchemas(typeData: AnyTypeData[_]): List[ObjectTypeData] = {
    typeData match {
      case TypeData(s, st: TSchemaType.SProduct, validator) =>
        List(st.info -> TypeData(s, st, validator): ObjectTypeData) ++ fieldsSchemaWithValidator(st, validator)
          .flatMap(objectSchemas)
          .toList
      case TypeData(_, TSchemaType.SArray(o), validator) =>
        objectSchemas(TypeData(o, o.schemaType, elementValidator(validator)))
      case TypeData(s, st: TSchemaType.SCoproduct, validator) =>
        (st.info -> TypeData(s, st, validator): ObjectTypeData) +: st.schemas.flatMap(
          c => objectSchemas(TypeData(c, c.schemaType, Validator.pass))
        )
      case TypeData(s, st: TSchemaType.SOpenProduct, validator) =>
        (st.info -> TypeData(s, st, validator): ObjectTypeData) +: objectSchemas(
          TypeData(st.valueSchema, st.valueSchema.schemaType, elementValidator(validator))
        )
      case _ => List.empty
    }
  }

  private def fieldsSchemaWithValidator(p: TSchemaType.SProduct, v: Validator[_]): immutable.Seq[AnyTypeData[_]] = {
    v match {
      case Validator.Product(validatedFields) =>
        p.fields.map { f =>
          TypeData(f._2, f._2.schemaType, validatedFields.get(f._1).map(_.validator).getOrElse(Validator.pass))
        }.toList
      case _ => p.fields.map(f => TypeData(f._2, f._2.schemaType, Validator.pass)).toList
    }
  }

  private def forInput(input: EndpointInput[_]): List[ObjectTypeData] = {
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
  private def forOutput(output: EndpointOutput[_]): List[ObjectTypeData] = {
    output match {
      case EndpointOutput.OneOf(mappings)       => mappings.flatMap(mapping => forOutput(mapping.output)).toList
      case EndpointOutput.StatusCode(_)         => List.empty
      case EndpointOutput.FixedStatusCode(_, _) => List.empty
      case EndpointOutput.Mapped(wrapped, _, _) => forOutput(wrapped)
      case EndpointOutput.Void()                => List.empty
      case EndpointOutput.Multiple(outputs)     => outputs.toList.flatMap(forOutput)
      case op: EndpointIO[_]                    => forIO(op)
    }
  }

  private def forIO(io: EndpointIO[_]): List[ObjectTypeData] = {
    io match {
      case EndpointIO.Multiple(ios)       => ios.toList.flatMap(ios2 => forInput(ios2) ++ forOutput(ios2))
      case EndpointIO.Header(_, codec, _) => forCodec(codec)
      case EndpointIO.Headers(_)          => List.empty
      case EndpointIO.Body(codec, _)      => forCodec(codec)
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(schema, _, _)) =>
        objectSchemas(TypeData(schema, schema.schemaType, Validator.pass))
      case EndpointIO.Mapped(wrapped, _, _) => forInput(wrapped) ++ forOutput(wrapped)
      case EndpointIO.FixedHeader(_, _, _)  => List.empty
    }
  }

  private def forCodec[T](codec: CodecForOptional[T, _, _]): List[ObjectTypeData] = objectSchemas(TypeData(codec))
  private def forCodec[T](codec: PlainCodec[T]): List[ObjectTypeData] = objectSchemas(TypeData(codec))
  private def forCodec[T](codec: PlainCodecForMany[T]): List[ObjectTypeData] = objectSchemas(TypeData(codec))

  private def objectInfoToName(info: TSchemaType.SObjectInfo): String = {
    val shortName = info.fullName.split('.').last
    (shortName +: info.typeParameterShortNames).mkString("_")
  }
}
