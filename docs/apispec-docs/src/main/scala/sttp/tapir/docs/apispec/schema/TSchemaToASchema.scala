package sttp.tapir.docs.apispec.schema

import sttp.apispec.{Schema => ASchema, _}
import sttp.tapir.Schema.{SName, Title, UniqueItems}
import sttp.tapir.Validator.EncodeToRaw
import sttp.tapir.docs.apispec.DocsExtensionAttribute.RichSchema
import sttp.tapir.docs.apispec.schema.TSchemaToASchema.{tConstToAConst, tDefaultToADefault, tExampleToAExample}
import sttp.tapir.docs.apispec.{DocsExtensions, exampleValue}
import sttp.tapir.internal._
import sttp.tapir.{Codec, Validator, Schema => TSchema, SchemaType => TSchemaType}

/** Converts a tapir schema to an OpenAPI/AsyncAPI schema, using `toSchemaReference` to resolve references. */
private[docs] class TSchemaToASchema(
    fallbackSchemaTitle: SName => String,
    toSchemaReference: ToSchemaReference,
    markOptionsAsNullable: Boolean
) {

  def apply[T](codec: Codec[T, _, _]): ASchema = apply(codec.schema, allowReference = true)

  /** @param allowReference
    *   Can a reference schema be generated, if this is a named schema - should be `false` for top-level component definitions (otherwise
    *   the definitions are infinitely recursive)
    */
  def apply[T](schema: TSchema[T], allowReference: Boolean, isOptionElement: Boolean = false): ASchema = {
    val nullable = markOptionsAsNullable && isOptionElement

    val result = schema.name match {
      case Some(name) if allowReference => toSchemaReference.map(schema, name)
      case _ =>
        schema.schemaType match {
          case TSchemaType.SInteger() => ASchema(SchemaType.Integer)
          case TSchemaType.SNumber()  => ASchema(SchemaType.Number)
          case TSchemaType.SBoolean() => ASchema(SchemaType.Boolean)
          case TSchemaType.SString()  => ASchema(SchemaType.String)
          case TSchemaType.SProduct(fields) if schema.attribute(TSchema.Tuple.Attribute).map(_.isTuple).getOrElse(false) =>
            ASchema(SchemaType.Array).copy(
              prefixItems = Some(fields.map(f => apply(f.schema, allowReference = true)))
            )
          case p @ TSchemaType.SProduct(fields) =>
            ASchema(SchemaType.Object).copy(
              required = p.required.map(_.encodedName),
              properties = extractProperties(fields)
            )
          case TSchemaType.SArray(el) => ASchema(SchemaType.Array).copy(items = Some(apply(el, allowReference = true)))
          case opt @ TSchemaType.SOption(nested @ TSchema(_, Some(name), _, _, _, _, _, _, _, _, _, _)) =>
            // #3288: in case there are multiple different customisations of the nested schema, we need to propagate the
            // metadata to properly customise the reference. These are also propagated in ToKeyedSchemas when computing
            // the initial list of schemas.
            val propagated = propagateMetadataForOption(schema, opt).element
            val ref = toSchemaReference.map(propagated, name)
            if (!markOptionsAsNullable) ref else ref.nullable
          case TSchemaType.SOption(el)    => apply(el, allowReference = true, isOptionElement = true)
          case TSchemaType.SBinary()      => ASchema(SchemaType.String).copy(format = Some(SchemaFormat.Binary))
          case TSchemaType.SDate()        => ASchema(SchemaType.String).copy(format = Some(SchemaFormat.Date))
          case TSchemaType.SDateTime()    => ASchema(SchemaType.String).copy(format = Some(SchemaFormat.DateTime))
          case TSchemaType.SRef(fullName) => toSchemaReference.mapDirect(fullName)
          case TSchemaType.SCoproduct(schemas, d) =>
            ASchema.oneOf(
              schemas
                .filterNot(_.hidden)
                .map(apply(_, allowReference = true))
                .sortBy {
                  case schema if schema.$ref.isDefined => schema.$ref.get
                  case schema =>
                    schema.`type`
                      .collect {
                        case List(t)                  => t.value
                        case List(t, SchemaType.Null) => t.value
                      }
                      .getOrElse("") + schema.toString
                },
              d.map(tDiscriminatorToADiscriminator)
            )
          case p @ TSchemaType.SOpenProduct(fields, valueSchema) =>
            ASchema(SchemaType.Object).copy(
              required = p.required.map(_.encodedName),
              properties = extractProperties(fields),
              additionalProperties = Some(apply(valueSchema, allowReference = true)).filterNot(_ => valueSchema.hidden)
            )
        }
    }

    if (result.$ref.isEmpty) {
      // only customising non-reference schemas; references might get enriched with some meta-data if there
      // are multiple different customisations of the referenced schema in ToSchemaReference (#1203)

      val primitiveValidators = schema.validator.asPrimitiveValidators
      val schemaIsWholeNumber = schema.schemaType match {
        case TSchemaType.SInteger() => true
        case _                      => false
      }

      var s = result
      s = if (nullable) s.nullable else s
      s = addMetadata(s, schema)
      s = addAttributes(s, schema)
      s = addConstraints(s, primitiveValidators, schemaIsWholeNumber)
      s
    } else result
  }

  private def extractProperties[T](fields: List[TSchemaType.SProductField[T]]) = {
    fields
      .filterNot(_.schema.hidden)
      .map(f => f.name.encodedName -> apply(f.schema, allowReference = true))
      .toListMap
  }

  private def addAttributes(oschema: ASchema, tschema: TSchema[_]): ASchema = {
    val titleFromAttr = tschema.attributes.get(Title.Attribute).map(_.value)
    // The primary motivation for using schema name as fallback title is to improve Swagger UX with
    // `oneOf` schemas in OpenAPI 3.1. See https://github.com/softwaremill/tapir/issues/3447 for details.
    def fallbackTitle = tschema.name.map(fallbackSchemaTitle)
    oschema
      .copy(title = titleFromAttr orElse fallbackTitle)
      .copy(uniqueItems = tschema.attribute(UniqueItems.Attribute).map(_.uniqueItems))
  }

  private def addMetadata(oschema: ASchema, tschema: TSchema[_]): ASchema = {
    oschema.copy(
      description = tschema.description.orElse(oschema.description),
      default = tDefaultToADefault(tschema).orElse(oschema.default),
      const = tConstToAConst(tschema).orElse(oschema.const),
      examples = tExampleToAExample(tschema).map(List(_)).orElse(oschema.examples),
      format = tschema.format.orElse(oschema.format),
      deprecated = (if (tschema.deprecated) Some(true) else None).orElse(oschema.deprecated),
      extensions = DocsExtensions.fromIterable(tschema.docsExtensions)
    )
  }

  private def addConstraints(
      oschema: ASchema,
      vs: Seq[Validator.Primitive[_]],
      schemaIsWholeNumber: Boolean
  ): ASchema = vs.foldLeft(oschema)(addConstraints(_, _, schemaIsWholeNumber))

  private def addConstraints(aschema: ASchema, v: Validator.Primitive[_], wholeNumbers: Boolean): ASchema = {
    v match {
      case m @ Validator.Min(v, false) =>
        aschema.copy(minimum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)))
      case m @ Validator.Min(v, true) =>
        aschema.copy(exclusiveMinimum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)))
      case m @ Validator.Max(v, false) =>
        aschema.copy(maximum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)))
      case m @ Validator.Max(v, true) =>
        aschema.copy(exclusiveMaximum = Some(toBigDecimal(v, m.valueIsNumeric, wholeNumbers)))
      case Validator.Pattern(value)                  => aschema.copy(pattern = Some(Pattern(value)))
      case Validator.MinLength(value, _)             => aschema.copy(minLength = Some(value))
      case Validator.MaxLength(value, _)             => aschema.copy(maxLength = Some(value))
      case Validator.MinSize(value)                  => aschema.copy(minItems = Some(value))
      case Validator.MaxSize(value)                  => aschema.copy(maxItems = Some(value))
      case Validator.Custom(_, _)                    => aschema
      case Validator.Enumeration(_, None, _)         => aschema
      case Validator.Enumeration(v, Some(encode), _) => addEnumeration(aschema, v, encode)
    }
  }

  private def addEnumeration[T](aschema: ASchema, v: List[T], encode: EncodeToRaw[T]): ASchema = {
    val values = v.flatMap(x => encode(x).map(ExampleSingleValue.apply))
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
      discriminator.mapping
        .flatMap { case (k, TSchemaType.SRef(fullName)) =>
          toSchemaReference.mapDiscriminator(fullName).$ref.map(k -> _)
        }
        .toList
        .sortBy(_._1)
        .toListMap
    )
    Discriminator(discriminator.name.encodedName, schemas)
  }
}

object TSchemaToASchema {
  def tDefaultToADefault(schema: TSchema[_]): Option[ExampleValue] = schema.default.flatMap { case (_, raw) =>
    raw.flatMap(r => exampleValue(schema, r))
  }

  def tConstToAConst(schema: TSchema[_]): Option[ExampleValue] = schema.const.flatMap { case (_, raw) =>
    raw.flatMap(r => exampleValue(schema, r))
  }

  def tExampleToAExample(schema: TSchema[_]): Option[ExampleValue] = schema.encodedExample.flatMap(exampleValue(schema, _))
}
