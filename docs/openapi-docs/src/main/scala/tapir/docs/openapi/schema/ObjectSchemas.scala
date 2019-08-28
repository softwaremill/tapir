package tapir.docs.openapi.schema

import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{SchemaType, Schema => OSchema}
import tapir.{Codec, CodecForMany, CodecForOptional, Validator, Schema => TSchema}
import tapir.docs.openapi.EncodingSupport._

class ObjectSchemas(
    tschemaToOSchema: TSchemaToOSchema,
    schemaReferenceMapper: SchemaReferenceMapper
) {
  def apply[T](codec: CodecForMany[T, _, _]): ReferenceOr[OSchema] = {
    apply(codec.meta.schema, codec.validator, Some({ t: T =>
      encodeValue(codec, t)
    }))
  }

  def apply[T](codec: Codec[T, _, _]): ReferenceOr[OSchema] = {
    apply(codec.meta.schema, codec.validator, Some({ t: T =>
      encodeValue(codec, t)
    }))
  }

  def apply[T](codec: CodecForOptional[T, _, _]): ReferenceOr[OSchema] = {
    apply(codec.meta.schema, codec.validator, Some({ t: T =>
      encodeValue(codec, t)
    }))
  }

  def apply(schema: TSchema, validator: Validator[_], encode: Option[EncodeAny[_]]): ReferenceOr[OSchema] = {
    schema match {
      case TSchema.SArray(o: TSchema.SObject) =>
        Right(OSchema(SchemaType.Array).copy(items = Some(Left(schemaReferenceMapper.map(o.info)))))
      case o: TSchema.SObject => Left(schemaReferenceMapper.map(o.info))
      case _                  => tschemaToOSchema(schema, validator, encode)
    }
  }
}
