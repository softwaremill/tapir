package sttp.tapir.codegen.openapi.models

import io.circe.{Json, JsonNumber}

sealed trait OpenapiSchemaType {
  def nullable: Boolean
}

object OpenapiSchemaType {
  sealed trait OpenapiSchemaMixedType extends OpenapiSchemaType
  sealed trait OpenapiSchemaSimpleType extends OpenapiSchemaType

  // https://swagger.io/specification/v3/#discriminator-object
  case class Discriminator(
      propertyName: String,
      mapping: Option[Map[String, String]] = None
  )
  // https://swagger.io/docs/specification/data-models/oneof-anyof-allof-not/
  case class OpenapiSchemaOneOf(
      types: Seq[OpenapiSchemaSimpleType],
      discriminator: Option[Discriminator] = None
  ) extends OpenapiSchemaMixedType {
    val nullable: Boolean = false
  }

  case class OpenapiSchemaAnyOf(
      types: Seq[OpenapiSchemaSimpleType]
  ) extends OpenapiSchemaMixedType {
    val nullable: Boolean = false
  }

  case class OpenapiSchemaAllOf(
      types: Seq[OpenapiSchemaType]
  ) extends OpenapiSchemaMixedType {
    val nullable: Boolean = false
  }

  case class OpenapiSchemaNot(
      `type`: OpenapiSchemaType
  ) extends OpenapiSchemaType {
    val nullable: Boolean = false
  }

  // https://swagger.io/docs/specification/data-models/data-types/#numbers
  case class NumericRestrictions(
      min: Option[JsonNumber] = None,
      max: Option[JsonNumber] = None,
      exclusiveMinimum: Option[Boolean] = None,
      exclusiveMaximum: Option[Boolean] = None,
      multipleOf: Option[JsonNumber] = None
  ) {
    def hasRestriction: Boolean = min.isDefined || max.isDefined || multipleOf.isDefined
  }
  sealed trait OpenapiSchemaNumericType extends OpenapiSchemaSimpleType {
    def restrictions: NumericRestrictions
    def scalaType: String
  }

  case class OpenapiSchemaDouble(
      nullable: Boolean,
      restrictions: NumericRestrictions
  ) extends OpenapiSchemaNumericType { def scalaType: String = "Double" }
  case class OpenapiSchemaFloat(
      nullable: Boolean,
      restrictions: NumericRestrictions
  ) extends OpenapiSchemaNumericType { def scalaType: String = "Float" }
  case class OpenapiSchemaLong(
      nullable: Boolean,
      restrictions: NumericRestrictions
  ) extends OpenapiSchemaNumericType { def scalaType: String = "Long" }
  case class OpenapiSchemaInt(
      nullable: Boolean,
      restrictions: NumericRestrictions
  ) extends OpenapiSchemaNumericType { def scalaType: String = "Int" }

  // https://swagger.io/docs/specification/data-models/data-types/#string
  // no minLength/maxLength, pattern support on 'formatted' subtypes.
  sealed trait OpenapiSchemaStringType extends OpenapiSchemaSimpleType

  case class OpenapiSchemaString(
      nullable: Boolean,
      pattern: Option[String] = None,
      minLength: Option[Int] = None,
      maxLength: Option[Int] = None
  ) extends OpenapiSchemaStringType {
    def hasRestriction: Boolean = pattern.isDefined || minLength.isDefined || maxLength.isDefined
  }
  case class OpenapiSchemaDate(
      nullable: Boolean
  ) extends OpenapiSchemaStringType
  case class OpenapiSchemaDateTime(
      nullable: Boolean
  ) extends OpenapiSchemaStringType
  case class OpenapiSchemaByte(
      nullable: Boolean
  ) extends OpenapiSchemaStringType
  case class OpenapiSchemaBinary(
      nullable: Boolean
  ) extends OpenapiSchemaStringType
  case class OpenapiSchemaUUID(
      nullable: Boolean
  ) extends OpenapiSchemaStringType

  case class OpenapiSchemaBoolean(
      nullable: Boolean
  ) extends OpenapiSchemaSimpleType

  case class OpenapiSchemaRef(
      name: String
  ) extends OpenapiSchemaSimpleType {
    val nullable = false
    def isSchema: Boolean = name.startsWith("#/components/schemas/")
    def stripped: String = name.stripPrefix("#/components/schemas/")
  }

  case class OpenapiSchemaAny(
      nullable: Boolean
  ) extends OpenapiSchemaSimpleType

  case class OpenapiSchemaConstantString(
      value: String
  ) extends OpenapiSchemaType {
    val nullable = false
  }

  // Can't currently support non-string enum types although that should apparently be legal (see https://json-schema.org/draft/2020-12/json-schema-validation.html#enum)
  case class OpenapiSchemaEnum(
      `type`: String,
      items: Seq[OpenapiSchemaConstantString],
      nullable: Boolean
  ) extends OpenapiSchemaType

  // no uniqueItems support
  case class ArrayRestrictions(minItems: Option[Int] = None, maxItems: Option[Int] = None, uniqueItems: Option[Boolean] = None) {
    def hasRestriction: Boolean = minItems.isDefined || maxItems.isDefined || uniqueItems.isDefined
  }
  case class OpenapiSchemaArray(
      items: OpenapiSchemaType,
      nullable: Boolean,
      xml: Option[OpenapiXml.XmlArrayConfiguration] = None,
      restrictions: ArrayRestrictions = ArrayRestrictions()
  ) extends OpenapiSchemaType

  case class ObjectFieldRestrictions(
      readOnly: Option[Boolean] = None,
      writeOnly: Option[Boolean] = None
  )
  case class OpenapiSchemaField(
      `type`: OpenapiSchemaType,
      default: Option[Json],
      restrictions: ObjectFieldRestrictions = ObjectFieldRestrictions()
  )
  case class ObjectRestrictions(
      minProperties: Option[Int] = None,
      maxProperties: Option[Int] = None
  ) {
    def hasRestriction: Boolean = minProperties.isDefined || maxProperties.isDefined
  }
  // no readOnly/writeOnly, minProperties/maxProperties support
  case class OpenapiSchemaObject(
      properties: Map[String, OpenapiSchemaField],
      required: Seq[String],
      nullable: Boolean,
      xml: Option[OpenapiXml.XmlObjectConfiguration] = None
  ) extends OpenapiSchemaType

  // no readOnly/writeOnly, minProperties/maxProperties support
  case class OpenapiSchemaMap(
      items: OpenapiSchemaType,
      nullable: Boolean,
      restrictions: ObjectRestrictions = ObjectRestrictions()
  ) extends OpenapiSchemaType

  // ///////////////////////////////////////////////////////
  // decoders
  // //////////////////////////////////////////////////////

  import io.circe._
  import cats.implicits._

  implicit val OpenapiSchemaRefDecoder: Decoder[OpenapiSchemaRef] = { (c: HCursor) =>
    for {
      r <- c.downField("$ref").as[String]
    } yield {
      OpenapiSchemaRef(r)
    }
  }

  def typeAndNullable(c: HCursor): Decoder.Result[(String, Boolean)] = {
    val typeField = c.downField("type")
    for {
      tf <- typeField.as[String].map(Seq(_)).orElse(typeField.as[Seq[String]])
      (t: String, nullableByType: Boolean) = tf match {
        case Seq(t)                                       => t -> false
        case seq if seq.size == 2 && seq.contains("null") => seq.find(_ != "null").getOrElse("null") -> true
        case _ => DecodingFailure("Type lists are only supported for lists of length two where one type is 'null'", c.history)
      }
      nb <- c.downField("nullable").as[Option[Boolean]]
    } yield (t, nullableByType || nb.contains(true))
  }

  implicit val OpenapiSchemaBooleanDecoder: Decoder[OpenapiSchemaBoolean] = { (c: HCursor) =>
    for {
      p <- typeAndNullable(c).ensure(DecodingFailure("Given type is not boolean!", c.history))(_._1 == "boolean")
    } yield {
      OpenapiSchemaBoolean(p._2)
    }
  }

  implicit val OpenapiSchemaStringTypeDecoder: Decoder[OpenapiSchemaStringType] = { (c: HCursor) =>
    for {
      p <- typeAndNullable(c).ensure(DecodingFailure("Given type is not string!", c.history))(_._1 == "string")
      f <- c.downField("format").as[Option[String]]
      pattern <- c.downField("pattern").as[Option[String]]
      min <- c.downField("minLength").as[Option[Int]]
      max <- c.downField("maxLength").as[Option[Int]]
    } yield {
      f.fold[OpenapiSchemaStringType](
        OpenapiSchemaString(p._2, pattern, min, max)
      ) {
        case "date"      => OpenapiSchemaDate(p._2)
        case "date-time" => OpenapiSchemaDateTime(p._2)
        case "byte"      => OpenapiSchemaByte(p._2)
        case "binary"    => OpenapiSchemaBinary(p._2)
        case "uuid"      => OpenapiSchemaUUID(p._2)
        case _           => OpenapiSchemaString(p._2, pattern, min, max)
      }
    }
  }

  implicit val OpenapiSchemaNumericTypeDecoder: Decoder[OpenapiSchemaNumericType] = { (c: HCursor) =>
    for {
      p <- typeAndNullable(c)
        .ensure(DecodingFailure("Given type is not number/integer!", c.history))(v => v._1 == "number" || v._1 == "integer")
      (t, nb) = p
      f <- c.downField("format").as[Option[String]]
      min <- c.downField("minimum").as[Option[JsonNumber]]
      max <- c.downField("maximum").as[Option[JsonNumber]]
      exclusiveMinimum <- c.downField("exclusiveMinimum").as[Option[Boolean]]
      exclusiveMaximum <- c.downField("exclusiveMaximum").as[Option[Boolean]]
      multipleOf <- c.downField("multipleOf").as[Option[JsonNumber]]
      restrictions = NumericRestrictions(min, max, exclusiveMinimum, exclusiveMaximum, multipleOf)
    } yield {
      if (t == "number") {
        f.fold[OpenapiSchemaNumericType](
          OpenapiSchemaDouble(nb, restrictions)
        ) {
          case "int64"  => OpenapiSchemaLong(nb, restrictions)
          case "int32"  => OpenapiSchemaInt(nb, restrictions)
          case "float"  => OpenapiSchemaFloat(nb, restrictions)
          case "double" => OpenapiSchemaDouble(nb, restrictions)
          case _        => OpenapiSchemaDouble(nb, restrictions)
        }
      } else {
        f.fold[OpenapiSchemaNumericType](
          OpenapiSchemaInt(nb, restrictions)
        ) {
          case "int64" => OpenapiSchemaLong(nb, restrictions)
          case "int32" => OpenapiSchemaInt(nb, restrictions)
          case _       => OpenapiSchemaInt(nb, restrictions)
        }
      }
    }
  }

  implicit val OpenapiSchemaSimpleTypeDecoder: Decoder[OpenapiSchemaSimpleType] =
    List[Decoder[OpenapiSchemaSimpleType]](
      Decoder[OpenapiSchemaRef].widen,
      Decoder[OpenapiSchemaBoolean].widen,
      Decoder[OpenapiSchemaStringType].widen,
      Decoder[OpenapiSchemaNumericType].widen
    ).reduceLeft(_ or _)

  implicit val DiscriminatorDecoder: Decoder[Discriminator] = { (c: HCursor) =>
    for {
      propertyName <- c.downField("propertyName").as[String]
      mapping <- c.downField("mapping").as[Option[Map[String, String]]]
    } yield Discriminator(propertyName, mapping)
  }

  implicit val OpenapiSchemaOneOfDecoder: Decoder[OpenapiSchemaOneOf] = { (c: HCursor) =>
    for {
      variants <- c.downField("oneOf").as[Seq[OpenapiSchemaSimpleType]]
      discriminator <- c.downField("discriminator").as[Option[Discriminator]]
    } yield {
      OpenapiSchemaOneOf(variants, discriminator)
    }
  }

  implicit val OpenapiSchemaAllOfDecoder: Decoder[OpenapiSchemaAllOf] = { (c: HCursor) =>
    for {
      d <- c.downField("allOf").as[Seq[OpenapiSchemaType]]
    } yield {
      OpenapiSchemaAllOf(d)
    }
  }

  implicit val OpenapiSchemaAnyOfDecoder: Decoder[OpenapiSchemaAnyOf] = { (c: HCursor) =>
    for {
      d <- c.downField("anyOf").as[Seq[OpenapiSchemaSimpleType]]
    } yield {
      OpenapiSchemaAnyOf(d)
    }
  }

  implicit val OpenapiSchemaMixedTypeDecoder: Decoder[OpenapiSchemaMixedType] = {
    List[Decoder[OpenapiSchemaMixedType]](
      Decoder[OpenapiSchemaOneOf].widen,
      Decoder[OpenapiSchemaAnyOf].widen,
      Decoder[OpenapiSchemaAllOf].widen
    ).reduceLeft(_ or _)
  }

  implicit val OpenapiSchemaNotDecoder: Decoder[OpenapiSchemaNot] = { (c: HCursor) =>
    for {
      d <- c.downField("not").as[OpenapiSchemaType]
    } yield {
      OpenapiSchemaNot(d)
    }
  }

  implicit val OpenapiSchemaConstantDecoder: Decoder[OpenapiSchemaConstantString] =
    Decoder.decodeString.map(OpenapiSchemaConstantString.apply)

  implicit val OpenapiSchemaEnumDecoder: Decoder[OpenapiSchemaEnum] = { (c: HCursor) =>
    for {
      p <- typeAndNullable(c)
      (tpe, nb) = p
      _ <- Either.cond(tpe == "string", (), DecodingFailure("only string enums are supported", c.history))
      items <- c.downField("enum").as[Seq[OpenapiSchemaConstantString]]
    } yield OpenapiSchemaEnum(tpe, items, nb)
  }

  implicit val SchemaTypeWithDefaultDecoder: Decoder[(OpenapiSchemaType, Option[Json], ObjectFieldRestrictions)] = { (c: HCursor) =>
    for {
      schemaType <- c.as[OpenapiSchemaType]
      maybeDefault <- c.downField("default").as[Option[Json]]
      readOnly <- c.downField("readOnly").as[Option[Boolean]]
      writeOnly <- c.downField("writeOnly").as[Option[Boolean]]
    } yield (schemaType, maybeDefault, ObjectFieldRestrictions(readOnly, writeOnly))
  }
  implicit val OpenapiSchemaObjectDecoder: Decoder[OpenapiSchemaObject] = { (c: HCursor) =>
    for {
      p <- typeAndNullable(c).ensure(DecodingFailure("Given type is not object!", c.history))(_._1 == "object")
      fieldsWithDefaults <- c.downField("properties").as[Option[Map[String, (OpenapiSchemaType, Option[Json], ObjectFieldRestrictions)]]]
      r <- c.downField("required").as[Option[Seq[String]]]
      (_, nb) = p
      fields = fieldsWithDefaults.getOrElse(Map.empty).map { case (k, (f, d, r)) => k -> OpenapiSchemaField(f, d, r) }
    } yield {
      OpenapiSchemaObject(fields, r.getOrElse(Seq.empty), nb)
    }
  }

  implicit val OpenapiSchemaMapDecoder: Decoder[OpenapiSchemaMap] = { (c: HCursor) =>
    for {
      p <- typeAndNullable(c).ensure(DecodingFailure("Given type is not object!", c.history))(_._1 == "object")
      t <- c.downField("additionalProperties").as[OpenapiSchemaType]
      mi <- c.downField("minProperties").as[Option[Int]]
      ma <- c.downField("maxProperties").as[Option[Int]]
      (_, nb) = p
    } yield {
      OpenapiSchemaMap(t, nb, ObjectRestrictions(mi, ma))
    }
  }

  implicit val xmlArrayConfigurationDecoder: Decoder[OpenapiXml.XmlArrayConfiguration] = { (c: HCursor) =>
    for {
      name <- c.downField("name").as[Option[String]]
      wrapped <- c.downField("wrapped").as[Option[Boolean]]
    } yield OpenapiXml.XmlArrayConfiguration(name, wrapped, None)
  }

  implicit val OpenapiSchemaArrayDecoder: Decoder[OpenapiSchemaArray] = { (c: HCursor) =>
    for {
      p <- typeAndNullable(c).ensure(DecodingFailure("Given type is not array!", c.history))(v => v._1 == "array" || v._1 == "object")
      f <- c.downField("items").as[OpenapiSchemaType]
      xmlItemName <- c.downField("items").downField("xml").downField("name").as[Option[String]]
      (_, nb) = p
      xmlArray <- c.downField("xml").as[Option[OpenapiXml.XmlArrayConfiguration]]
      xml = xmlArray match {
        case Some(some) => Some(some.copy(itemName = xmlItemName))
        case None       => xmlItemName.map(n => OpenapiXml.XmlArrayConfiguration(itemName = Some(n)))
      }
      minItems <- c.downField("minItems").as[Option[Int]]
      maxItems <- c.downField("maxItems").as[Option[Int]]
      restrictions = ArrayRestrictions(minItems, maxItems)
    } yield {
      OpenapiSchemaArray(f, nb, xml, restrictions)
    }
  }

  implicit val OpenapiSchemaAnyDecoder: Decoder[OpenapiSchemaAny] = { (c: HCursor) =>
    for {
      _ <- c.downField("type").as[Option[String]].ensure(DecodingFailure("Type must not be defined!", c.history))(_.isEmpty)
      nb <- c.downField("nullable").as[Option[Boolean]]
    } yield {
      OpenapiSchemaAny(nb.getOrElse(false))
    }
  }

  implicit lazy val OpenapiSchemaTypeDecoder: Decoder[OpenapiSchemaType] =
    List[Decoder[OpenapiSchemaType]](
      Decoder[OpenapiSchemaEnum].widen,
      Decoder[OpenapiSchemaSimpleType].widen,
      Decoder[OpenapiSchemaMixedType].widen,
      Decoder[OpenapiSchemaNot].widen,
      Decoder[OpenapiSchemaMap].widen,
      Decoder[OpenapiSchemaObject].widen,
      Decoder[OpenapiSchemaArray].widen,
      Decoder[OpenapiSchemaAny].widen
    ).reduceLeft(_ or _)
}
