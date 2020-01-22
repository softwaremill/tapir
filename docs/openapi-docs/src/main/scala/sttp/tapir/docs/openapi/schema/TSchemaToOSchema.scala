package sttp.tapir.docs.openapi.schema

import sttp.tapir.docs.openapi.rawToString
import sttp.tapir.openapi.OpenAPI.ReferenceOr
import sttp.tapir.openapi.{Schema => OSchema, _}
import sttp.tapir.{Validator, Schema => TSchema, SchemaType => TSchemaType}

/**
  * Converts a tapir schema to an OpenAPI schema, using the given map to resolve references.
  */
private[schema] class TSchemaToOSchema(schemaReferenceMapper: SchemaReferenceMapper, discriminatorToOpenApi: DiscriminatorToOpenApi) {
  def apply(typeData: TypeData[_]): ReferenceOr[OSchema] = {
    val result = typeData.schema.schemaType match {
      case TSchemaType.SInteger => Right(OSchema(SchemaType.Integer))
      case TSchemaType.SNumber  => Right(OSchema(SchemaType.Number))
      case TSchemaType.SBoolean => Right(OSchema(SchemaType.Boolean))
      case TSchemaType.SString  => Right(OSchema(SchemaType.String))
      case p @ TSchemaType.SProduct(_, fields) =>
        Right(
          OSchema(SchemaType.Object).copy(
            required = p.required.toList,
            properties = fields.map {
              case (fieldName, TSchema(s: TSchemaType.SObject, _, _, _, _)) =>
                fieldName -> Left(schemaReferenceMapper.map(s.info))
              case (fieldName, fieldSchema) =>
                fieldName -> apply(TypeData(fieldSchema, fieldValidator(typeData.validator, fieldName)))
            }.toListMap
          )
        )
      case TSchemaType.SArray(TSchema(el: TSchemaType.SObject, _, _, _, _)) =>
        Right(OSchema(SchemaType.Array).copy(items = Some(Left(schemaReferenceMapper.map(el.info)))))
      case TSchemaType.SArray(el) =>
        Right(OSchema(SchemaType.Array).copy(items = Some(apply(TypeData(el, elementValidator(typeData.validator))))))
      case TSchemaType.SBinary        => Right(OSchema(SchemaType.String).copy(format = SchemaFormat.Binary))
      case TSchemaType.SDate          => Right(OSchema(SchemaType.String).copy(format = SchemaFormat.Date))
      case TSchemaType.SDateTime      => Right(OSchema(SchemaType.String).copy(format = SchemaFormat.DateTime))
      case TSchemaType.SRef(fullName) => Left(schemaReferenceMapper.map(fullName))
      case TSchemaType.SCoproduct(_, schemas, d) =>
        Right(
          OSchema.apply(
            schemas.collect { case TSchema(s: TSchemaType.SProduct, _, _, _, _) => Left(schemaReferenceMapper.map(s.info)) },
            d.map(discriminatorToOpenApi.apply)
          )
        )
      case TSchemaType.SOpenProduct(_, valueSchema) =>
        Right(
          OSchema(SchemaType.Object).copy(
            required = List.empty,
            additionalProperties = Some(valueSchema.schemaType match {
              case so: TSchemaType.SObject => Left(schemaReferenceMapper.map(so.info))
              case _                       => apply(TypeData(valueSchema, elementValidator(typeData.validator)))
            })
          )
        )
    }

    result
      .map(addMetadata(_, typeData.schema))
      .map(
        addConstraints(_, asPrimitiveValidators(typeData.validator), typeData.schema.schemaType.isInstanceOf[TSchemaType.SInteger.type])
      )
  }

  private def addMetadata(oschema: OSchema, tschema: TSchema[_]): OSchema = {
    oschema.copy(
      description = tschema.description.orElse(oschema.description),
      format = tschema.format.orElse(oschema.format),
      deprecated = (if (tschema.deprecated) Some(true) else None).orElse(oschema.deprecated)
    )
  }

  private def addConstraints(
      oschema: OSchema,
      vs: Seq[Validator.Primitive[_]],
      wholeNumbers: Boolean
  ): OSchema = vs.foldLeft(oschema)(addConstraints(_, _, wholeNumbers))

  private def addConstraints(oschema: OSchema, v: Validator.Primitive[_], wholeNumbers: Boolean): OSchema = {
    v match {
      case m @ Validator.Min(v, exclusive) =>
        if (exclusive) {
          oschema.copy(exclusiveMinimum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)))
        } else {
          oschema.copy(minimum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)))
        }
      case m @ Validator.Max(v, exclusive) =>
        if (exclusive) {
          oschema.copy(exclusiveMaximum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)))
        } else {
          oschema.copy(maximum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)))
        }
      case Validator.Pattern(value)   => oschema.copy(pattern = Some(value))
      case Validator.MinLength(value) => oschema.copy(minLength = Some(value))
      case Validator.MaxLength(value) => oschema.copy(maxLength = Some(value))
      case Validator.MinSize(value)   => oschema.copy(minItems = Some(value))
      case Validator.MaxSize(value)   => oschema.copy(maxItems = Some(value))
      case Validator.Custom(_, _)     => oschema
      case Validator.Enum(_, None)    => oschema
      case Validator.Enum(v, Some(encode)) =>
        val values = v.flatMap(x => encode(x).map(rawToString))
        oschema.copy(enum = if (values.nonEmpty) Some(values) else None)
    }
  }

  private def toBigDecimal[N](v: N, vIsNumeric: Numeric[N], wholeNumber: Boolean): BigDecimal = {
    if (wholeNumber) BigDecimal(vIsNumeric.toLong(v)) else BigDecimal(vIsNumeric.toDouble(v))
  }
}
