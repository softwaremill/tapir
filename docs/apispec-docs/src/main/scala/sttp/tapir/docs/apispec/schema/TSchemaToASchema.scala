package sttp.tapir.docs.apispec.schema

import sttp.tapir.Validator.EncodeToRaw
import sttp.tapir.apispec.{ReferenceOr, Schema => ASchema, _}
import sttp.tapir.docs.apispec.exampleValue
import sttp.tapir.internal.{IterableToListMap, _}
import sttp.tapir.{Validator, Schema => TSchema, SchemaType => TSchemaType}

/** Converts a tapir schema to an OpenAPI/AsyncAPI schema, using the given map to resolve nested references. */
private[schema] class TSchemaToASchema(nameToSchemaReference: NameToSchemaReference, markOptionsAsNullable: Boolean) {
  def apply[T](schema: TSchema[T], isOptionElement: Boolean = false): ReferenceOr[ASchema] = {
    val nullable = markOptionsAsNullable && isOptionElement
    val result = schema.schemaType match {
      case TSchemaType.SInteger() => Right(ASchema(SchemaType.Integer))
      case TSchemaType.SNumber()  => Right(ASchema(SchemaType.Number))
      case TSchemaType.SBoolean() => Right(ASchema(SchemaType.Boolean))
      case TSchemaType.SString()  => Right(ASchema(SchemaType.String))
      case p @ TSchemaType.SProduct(fields) =>
        Right(
          ASchema(SchemaType.Object).copy(
            required = p.required.map(_.encodedName),
            properties = fields.map { f =>
              f.schema match {
                case TSchema(_, Some(name), _, _, _, _, _, _, _) => f.name.encodedName -> Left(nameToSchemaReference.map(name))
                case schema                                      => f.name.encodedName -> apply(schema)
              }
            }.toListMap
          )
        )
      case TSchemaType.SArray(TSchema(_, Some(name), _, _, _, _, _, _, _)) =>
        Right(ASchema(SchemaType.Array).copy(items = Some(Left(nameToSchemaReference.map(name)))))
      case TSchemaType.SArray(el) => Right(ASchema(SchemaType.Array).copy(items = Some(apply(el))))
      case TSchemaType.SOption(TSchema(_, Some(name), _, _, _, _, _, _, _)) => Left(nameToSchemaReference.map(name))
      case TSchemaType.SOption(el)                                          => apply(el, isOptionElement = true)
      case TSchemaType.SBinary()      => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.Binary))
      case TSchemaType.SDate()        => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.Date))
      case TSchemaType.SDateTime()    => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.DateTime))
      case TSchemaType.SRef(fullName) => Left(nameToSchemaReference.map(fullName))
      case TSchemaType.SCoproduct(schemas, d) =>
        Right(
          ASchema
            .apply(
              schemas
                .map {
                  case TSchema(_, Some(name), _, _, _, _, _, _, _) => Left(nameToSchemaReference.map(name))
                  case t                                           => apply(t)
                }
                .sortBy {
                  case Left(Reference(ref)) => ref
                  case Right(schema)        => schema.`type`.map(_.value).getOrElse("") + schema.toString
                },
              d.map(tDiscriminatorToADiscriminator)
            )
        )
      case TSchemaType.SOpenProduct(valueSchema) =>
        Right(
          ASchema(SchemaType.Object).copy(
            required = List.empty,
            additionalProperties = Some(valueSchema.name match {
              case Some(name) => Left(nameToSchemaReference.map(name))
              case _          => apply(valueSchema)
            })
          )
        )
    }

    val primitiveValidators = schema.validator.asPrimitiveValidators
    val schemaIsWholeNumber = schema.schemaType match {
      case TSchemaType.SInteger() => true
      case _                      => false
    }

    result
      .map(s => if (nullable) s.copy(nullable = Some(true)) else s)
      .map(addMetadata(_, schema))
      .map(addConstraints(_, primitiveValidators, schemaIsWholeNumber))
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
      schemaIsWholeNumber: Boolean
  ): ASchema = vs.foldLeft(oschema)(addConstraints(_, _, schemaIsWholeNumber))

  private def addConstraints(aschema: ASchema, v: Validator.Primitive[_], wholeNumbers: Boolean): ASchema = {
    v match {
      case m @ Validator.Min(v, exclusive) =>
        aschema.copy(
          minimum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)),
          exclusiveMinimum = Option(exclusive).filter(identity)
        )
      case m @ Validator.Max(v, exclusive) =>
        aschema.copy(
          maximum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)),
          exclusiveMaximum = Option(exclusive).filter(identity)
        )
      case Validator.Pattern(value)                  => aschema.copy(pattern = Some(value))
      case Validator.MinLength(value)                => aschema.copy(minLength = Some(value))
      case Validator.MaxLength(value)                => aschema.copy(maxLength = Some(value))
      case Validator.MinSize(value)                  => aschema.copy(minItems = Some(value))
      case Validator.MaxSize(value)                  => aschema.copy(maxItems = Some(value))
      case Validator.Enumeration(_, None, _)         => aschema
      case Validator.Enumeration(v, Some(encode), _) => addEnumeration(aschema, v, encode)
    }
  }

  private def addEnumeration[T](aschema: ASchema, v: List[T], encode: EncodeToRaw[T]): ASchema = {
    val values = v.flatMap(x => encode(x).map(ExampleSingleValue))
    aschema.copy(`enum` = if (values.nonEmpty) Some(values) else None)
  }

  private def toBigDecimal[N](v: N, vIsNumeric: Numeric[N], schemaIsWholeNumber: Boolean): BigDecimal = {
    v match {
      case x: Int                   => BigDecimal(x)
      case x: Long                  => BigDecimal(x)
      case x: Float                 => BigDecimal(x.toDouble)
      case x: Double                => BigDecimal(x)
      case x: BigInt                => BigDecimal(x)
      case x: java.math.BigInteger  => BigDecimal(x)
      case x: BigDecimal            => x
      case x: java.math.BigDecimal  => BigDecimal(x)
      case _ if schemaIsWholeNumber => BigDecimal(vIsNumeric.toLong(v))
      case _                        => BigDecimal(vIsNumeric.toDouble(v))
    }
  }

  private def tDiscriminatorToADiscriminator(discriminator: TSchemaType.SDiscriminator): Discriminator = {
    val schemas = Some(
      discriminator.mapping.map { case (k, TSchemaType.SRef(fullName)) =>
        k -> nameToSchemaReference.map(fullName).$ref
      }.toListMap
    )
    Discriminator(discriminator.name.encodedName, schemas)
  }
}
