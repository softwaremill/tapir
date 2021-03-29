package sttp.tapir.docs.apispec.schema

import sttp.tapir.apispec.{ReferenceOr, Schema => ASchema, _}
import sttp.tapir.docs.apispec.ValidatorUtil.asPrimitiveValidators
import sttp.tapir.docs.apispec.{exampleValue, rawToString}
import sttp.tapir.internal.IterableToListMap
import sttp.tapir.{Validator, Schema => TSchema, SchemaType => TSchemaType}

/** Converts a tapir schema to an OpenAPI/AsyncAPI schema, using the given map to resolve references. */
private[schema] class TSchemaToASchema(
    objectToSchemaReference: ObjectToSchemaReference
) {
  def apply(schema: TSchema[_]): ReferenceOr[ASchema] = {
    val result = schema.schemaType match {
      case TSchemaType.SInteger() => Right(ASchema(SchemaType.Integer))
      case TSchemaType.SNumber()  => Right(ASchema(SchemaType.Number))
      case TSchemaType.SBoolean() => Right(ASchema(SchemaType.Boolean))
      case TSchemaType.SString()  => Right(ASchema(SchemaType.String))
      case p @ TSchemaType.SProduct(_, fields) =>
        Right(
          ASchema(SchemaType.Object).copy(
            required = p.required.map(_.encodedName),
            properties = fields.map { f =>
              f.schema match {
                case TSchema(s: TSchemaType.SObject[_], _, _, _, _, _, _, _) =>
                  f.name.encodedName -> Left(objectToSchemaReference.map(s.info))
                case fieldSchema =>
                  f.name.encodedName -> apply(fieldSchema)
              }
            }.toListMap
          )
        )
      case TSchemaType.SArray(TSchema(el: TSchemaType.SObject[_], _, _, _, _, _, _, _)) =>
        Right(ASchema(SchemaType.Array).copy(items = Some(Left(objectToSchemaReference.map(el.info)))))
      case TSchemaType.SArray(el) =>
        Right(ASchema(SchemaType.Array).copy(items = Some(apply(el))))
      case TSchemaType.SOption(el)    => apply(el)
      case TSchemaType.SBinary()      => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.Binary))
      case TSchemaType.SDate()        => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.Date))
      case TSchemaType.SDateTime()    => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.DateTime))
      case TSchemaType.SRef(fullName) => Left(objectToSchemaReference.map(fullName))
      case TSchemaType.SCoproduct(_, schemas, d) =>
        Right(
          ASchema.apply(
            schemas.values.toList.collect { case TSchema(s: TSchemaType.SProduct[_], _, _, _, _, _, _, _) =>
              Left(objectToSchemaReference.map(s.info))
            },
            d.map(tDiscriminatorToADiscriminator)
          )
        )
      case TSchemaType.SOpenProduct(_, valueSchema) =>
        Right(
          ASchema(SchemaType.Object).copy(
            required = List.empty,
            additionalProperties = Some(valueSchema.schemaType match {
              case so: TSchemaType.SObject[_] => Left(objectToSchemaReference.map(so.info))
              case _                          => apply(valueSchema)
            })
          )
        )
    }

    val primitiveValidators = asPrimitiveValidators(schema.validator)
    val wholeNumbers = schema.schemaType match {
      case TSchemaType.SInteger() => true
      case _                      => false
    }

    result
      .map(addMetadata(_, schema))
      .map(addConstraints(_, primitiveValidators, wholeNumbers))
  }

  private def addMetadata(oschema: ASchema, tschema: TSchema[_]): ASchema = {
    oschema.copy(
      description = tschema.description.orElse(oschema.description),
      default = tschema.default.flatMap { case (_, raw) => raw.flatMap(r => exampleValue(tschema, r)) }.orElse(oschema.default),
      example = tschema.encodedExample.flatMap(exampleValue(tschema, _)).orElse(oschema.example),
      format = tschema.format.orElse(oschema.format),
      deprecated = (if (tschema.deprecated) Some(true) else None).orElse(oschema.deprecated)
    )
  }

  private def addConstraints(
      oschema: ASchema,
      vs: Seq[Validator.Primitive[_]],
      wholeNumbers: Boolean
  ): ASchema = vs.foldLeft(oschema)(addConstraints(_, _, wholeNumbers))

  private def addConstraints(oschema: ASchema, v: Validator.Primitive[_], wholeNumbers: Boolean): ASchema = {
    v match {
      case m @ Validator.Min(v, exclusive) =>
        oschema.copy(
          minimum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)),
          exclusiveMinimum = Option(exclusive).filter(identity)
        )
      case m @ Validator.Max(v, exclusive) =>
        oschema.copy(
          maximum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)),
          exclusiveMaximum = Option(exclusive).filter(identity)
        )
      case Validator.Pattern(value)   => oschema.copy(pattern = Some(value))
      case Validator.MinLength(value) => oschema.copy(minLength = Some(value))
      case Validator.MaxLength(value) => oschema.copy(maxLength = Some(value))
      case Validator.MinSize(value)   => oschema.copy(minItems = Some(value))
      case Validator.MaxSize(value)   => oschema.copy(maxItems = Some(value))
      case Validator.Enum(_, None)    => oschema
      case Validator.Enum(v, Some(encode)) =>
        val values = v.flatMap(x => encode(x).map(rawToString))
        oschema.copy(enum = if (values.nonEmpty) Some(values) else None)
    }
  }

  private def toBigDecimal[N](v: N, vIsNumeric: Numeric[N], wholeNumber: Boolean): BigDecimal = {
    if (wholeNumber) BigDecimal(vIsNumeric.toLong(v)) else BigDecimal(vIsNumeric.toDouble(v))
  }

  private def tDiscriminatorToADiscriminator(discriminator: TSchemaType.SDiscriminator): Discriminator = {
    val schemas = Some(
      discriminator.mapping.map { case (k, TSchemaType.SRef(fullName)) =>
        k -> objectToSchemaReference.map(fullName).$ref
      }.toListMap
    )
    Discriminator(discriminator.name.encodedName, schemas)
  }
}
