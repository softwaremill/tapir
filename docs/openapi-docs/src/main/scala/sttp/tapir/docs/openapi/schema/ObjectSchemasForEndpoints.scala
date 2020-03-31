package sttp.tapir.docs.openapi.schema

import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecForMany.PlainCodecForMany
import sttp.tapir.SchemaType.SObjectInfo
import sttp.tapir.docs.openapi.uniqueName
import sttp.tapir.openapi.OpenAPI.ReferenceOr
import sttp.tapir.openapi.{Schema => OSchema}
import sttp.tapir.{Schema => TSchema, SchemaType => TSchemaType, _}

import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer

object ObjectSchemasForEndpoints {
  private type ObjectTypeData = (TSchemaType.SObjectInfo, TypeData[_])

  def apply(es: Iterable[Endpoint[_, _, _, _]]): (ListMap[SchemaKey, ReferenceOr[OSchema]], ObjectSchemas) = {
    val sObjects = uniqueObjects(es.flatMap(e => forInput(e.input) ++ forOutput(e.errorOutput) ++ forOutput(e.output)))
    val infoToKey = calculateUniqueKeys(sObjects.map(_._1))
    val schemaReferences = new SchemaReferenceMapper(infoToKey)
    val discriminatorToOpenApi = new DiscriminatorToOpenApi(schemaReferences)
    val tschemaToOSchema = new TSchemaToOSchema(schemaReferences, discriminatorToOpenApi)
    val schemas = new ObjectSchemas(tschemaToOSchema, schemaReferences)
    val infosToSchema = sObjects.map(td => (td._1, tschemaToOSchema(td._2))).toListMap

    val schemaKeys = infosToSchema.map { case (k, v) => k -> ((infoToKey(k), v)) }
    (schemaKeys.values.toListMap, schemas)
  }

  /**
    * Keeps only the first object data for each `SObjectInfo`. In case of recursive objects, the first one is the
    * most complete as it contains the built-up structure, unlike subsequent ones, which only represent leaves (#354).
    */
  private def uniqueObjects(objs: Iterable[(TSchemaType.SObjectInfo, TypeData[_])]): Iterable[(TSchemaType.SObjectInfo, TypeData[_])] = {
    val seen: collection.mutable.Set[TSchemaType.SObjectInfo] = collection.mutable.Set()
    val result: ListBuffer[(TSchemaType.SObjectInfo, TypeData[_])] = ListBuffer()
    objs.foreach { obj =>
      if (!seen.contains(obj._1)) {
        seen.add(obj._1)
        result += obj
      }
    }
    result.toList
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

  private def objectSchemas(typeData: TypeData[_]): List[ObjectTypeData] = {
    typeData match {
      case TypeData(TSchema(TSchemaType.SArray(o), _, _, _, _), validator) =>
        objectSchemas(TypeData(o, elementValidator(validator)))
      case TypeData(s @ TSchema(st: TSchemaType.SProduct, _, _, _, _), validator) =>
        productSchemas(s, st, validator)
      case TypeData(s @ TSchema(st: TSchemaType.SCoproduct, _, _, _, _), validator) =>
        coproductSchemas(s, st, validator)
      case TypeData(s @ TSchema(st: TSchemaType.SOpenProduct, _, _, _, _), validator) =>
        (st.info -> TypeData(s, validator): ObjectTypeData) +: objectSchemas(
          TypeData(st.valueSchema, elementValidator(validator))
        )
      case _ => List.empty
    }
  }

  private def productSchemas(s: TSchema[_], st: TSchemaType.SProduct, validator: Validator[_]): List[ObjectTypeData] = {
    (st.info -> TypeData(s, validator): ObjectTypeData) +: fieldsSchemaWithValidator(st, validator)
      .flatMap(objectSchemas)
      .toList
  }

  private def coproductSchemas(s: TSchema[_], st: TSchemaType.SCoproduct, validator: Validator[_]): List[ObjectTypeData] = {
    (st.info -> TypeData(s, validator): ObjectTypeData) +: subtypesSchemaWithValidator(st, validator)
      .flatMap(objectSchemas)
      .toList
  }

  private def fieldsSchemaWithValidator(p: TSchemaType.SProduct, v: Validator[_]): Seq[TypeData[_]] = {
    p.fields.map { f => TypeData(f._2, fieldValidator(v, f._1)) }.toList
  }

  private def subtypesSchemaWithValidator(st: TSchemaType.SCoproduct, v: Validator[_]): Seq[TypeData[_]] = {
    st.schemas.collect {
      case s @ TSchema(st: TSchemaType.SProduct, _, _, _, _) => TypeData(s, subtypeValidator(v, st.info))
    }
  }

  private def subtypeValidator(v: Validator[_], subtype: SObjectInfo): Validator[_] = v match {
    case v @ Validator.Coproduct(_)                                  => v.subtypes.getOrElse(subtype.fullName, Validator.pass)
    case Validator.CollectionElements(v @ Validator.Coproduct(_), _) => v.subtypes.getOrElse(subtype.fullName, Validator.pass)
    case _                                                           => Validator.pass
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
        objectSchemas(TypeData(schema, Validator.pass))
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
