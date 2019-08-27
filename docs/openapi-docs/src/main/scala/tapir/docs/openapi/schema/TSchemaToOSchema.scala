package tapir.docs.openapi.schema

import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{Schema => OSchema, _}
import tapir.{Validator, Schema => TSchema}

/**
  * Converts a tapir schema to an OpenAPI schema, using the given map to resolve references.
  */
private[schema] class TSchemaToOSchema(schemaReferenceMapper: SchemaReferenceMapper, discriminatorToOpenApi: DiscriminatorToOpenApi) {
  def apply(schema: TSchema, validator: Validator[_]): ReferenceOr[OSchema] = {
    val result = schema match {
      case TSchema.SInteger => Right(OSchema(SchemaType.Integer))
      case TSchema.SNumber  => Right(OSchema(SchemaType.Number))
      case TSchema.SBoolean => Right(OSchema(SchemaType.Boolean))
      case TSchema.SString  => Right(OSchema(SchemaType.String))
      case TSchema.SProduct(_, fields, required) =>
        Right(
          OSchema(SchemaType.Object).copy(
            required = required.toList,
            properties = fields.map {
              case (fieldName, s: TSchema.SObject) =>
                fieldName -> Left(schemaReferenceMapper.map(s.info))
              case (fieldName, fieldSchema) =>
                fieldName -> apply(fieldSchema, fieldValidator(validator, fieldName))
            }.toListMap
          )
        )
      case TSchema.SArray(el: TSchema.SObject) =>
        Right(OSchema(SchemaType.Array).copy(items = Some(Left(schemaReferenceMapper.map(el.info)))))
      case TSchema.SArray(el)     => Right(OSchema(SchemaType.Array).copy(items = Some(apply(el, elementValidator(validator)))))
      case TSchema.SBinary        => Right(OSchema(SchemaType.String).copy(format = Some(SchemaFormat.Binary)))
      case TSchema.SDate          => Right(OSchema(SchemaType.String).copy(format = Some(SchemaFormat.Date)))
      case TSchema.SDateTime      => Right(OSchema(SchemaType.String).copy(format = Some(SchemaFormat.DateTime)))
      case TSchema.SRef(fullName) => Left(schemaReferenceMapper.map(fullName))
      case TSchema.SCoproduct(_, schemas, d) =>
        Right(
          OSchema.apply(
            schemas.collect { case s: TSchema.SProduct => Left(schemaReferenceMapper.map(s.info)) }.toList,
            d.map(discriminatorToOpenApi.apply)
          )
        )
      case TSchema.SOpenProduct(_, valueSchema) =>
        Right(
          OSchema(SchemaType.Object).copy(
            required = List.empty,
            additionalProperties = Some(valueSchema match {
              case so: TSchema.SObject => Left(schemaReferenceMapper.map(so.info))
              case s                   => apply(s, elementValidator(validator))
            })
          )
        )
    }

    result.map(addConstraints(_, asPrimitiveValidators(validator), schema.isInstanceOf[TSchema.SInteger.type]))
  }

  private def addConstraints(oschema: OSchema, vs: Seq[Validator.Primitive[_]], wholeNumbers: Boolean): OSchema =
    vs.foldLeft(oschema)(addConstraints(_, _, wholeNumbers))

  private def addConstraints(oschema: OSchema, v: Validator.Primitive[_], wholeNumbers: Boolean): OSchema = {
    v match {
      case m @ Validator.Min(v)     => oschema.copy(minimum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)))
      case Validator.Pattern(value) => oschema.copy(pattern = Some(value))
      case Validator.MinSize(value) => oschema.copy(minSize = Some(value))
      case Validator.Custom(_, _)   => oschema
    }
  }

  private def toBigDecimal[N](v: N, vIsNumeric: Numeric[N], wholeNumber: Boolean): BigDecimal = {
    if (wholeNumber) BigDecimal(vIsNumeric.toLong(v)) else BigDecimal(vIsNumeric.toDouble(v))
  }

  private def fieldValidator(v: Validator[_], fieldName: String): Validator[_] = {
    Validator.all(asSingleValidators(v).collect {
      case Validator.Product(fields) if fields.isDefinedAt(fieldName) => fields(fieldName).validator
    }: _*)
  }
}
