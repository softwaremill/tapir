package sttp.tapir.codegen.openapi.models

sealed trait OpenapiSchemaType {
  def nullable: Boolean
}

object OpenapiSchemaType {
  sealed trait OpenapiSchemaMixedType extends OpenapiSchemaType
  sealed trait OpenapiSchemaSimpleType extends OpenapiSchemaType

  // https://swagger.io/docs/specification/data-models/oneof-anyof-allof-not/
  case class OpenapiSchemaOneOf(
      types: Seq[OpenapiSchemaSimpleType]
  ) extends OpenapiSchemaMixedType {
    val nullable: Boolean = false
  }

  case class OpenapiSchemaAnyOf(
      types: Seq[OpenapiSchemaSimpleType]
  ) extends OpenapiSchemaMixedType {
    val nullable: Boolean = false
  }

  case class OpenapiSchemaAllOf(
      types: Seq[OpenapiSchemaSimpleType]
  ) extends OpenapiSchemaMixedType {
    val nullable: Boolean = false
  }

  case class OpenapiSchemaNot(
      `type`: OpenapiSchemaType
  ) extends OpenapiSchemaType {
    val nullable: Boolean = false
  }

  // https://swagger.io/docs/specification/data-models/data-types/#numbers
  // no min/max, exclusiveMin/exclusiveMax, multipleOf support
  sealed trait OpenapiSchemaNumericType extends OpenapiSchemaSimpleType

  case class OpenapiSchemaDouble(
      nullable: Boolean
  ) extends OpenapiSchemaNumericType
  case class OpenapiSchemaFloat(
      nullable: Boolean
  ) extends OpenapiSchemaNumericType
  case class OpenapiSchemaLong(
      nullable: Boolean
  ) extends OpenapiSchemaNumericType
  case class OpenapiSchemaInt(
      nullable: Boolean
  ) extends OpenapiSchemaNumericType

  // https://swagger.io/docs/specification/data-models/data-types/#string
  // no minLength/maxLength, pattern support
  sealed trait OpenapiSchemaStringType extends OpenapiSchemaSimpleType

  case class OpenapiSchemaString(
      nullable: Boolean
  ) extends OpenapiSchemaStringType
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

  // no minItems/maxItems, uniqueItems support
  case class OpenapiSchemaArray(
      items: OpenapiSchemaType,
      nullable: Boolean
  ) extends OpenapiSchemaType

  case class OpenapiSchemaField(
      `type`: OpenapiSchemaType,
      default: Option[RenderableValue]
  )
  // no readOnly/writeOnly, minProperties/maxProperties support
  case class OpenapiSchemaObject(
      properties: Map[String, OpenapiSchemaField],
      required: Seq[String],
      nullable: Boolean
  ) extends OpenapiSchemaType

  // no readOnly/writeOnly, minProperties/maxProperties support
  case class OpenapiSchemaMap(
      items: OpenapiSchemaType,
      nullable: Boolean
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

  implicit val OpenapiSchemaBooleanDecoder: Decoder[OpenapiSchemaBoolean] = { (c: HCursor) =>
    for {
      _ <- c.downField("type").as[String].ensure(DecodingFailure("Given type is not boolean!", c.history))(_ == "boolean")
      nb <- c.downField("nullable").as[Option[Boolean]]
    } yield {
      OpenapiSchemaBoolean(nb.getOrElse(false))
    }
  }

  implicit val OpenapiSchemaStringTypeDecoder: Decoder[OpenapiSchemaStringType] = { (c: HCursor) =>
    for {
      _ <- c.downField("type").as[String].ensure(DecodingFailure("Given type is not string!", c.history))(_ == "string")
      f <- c.downField("format").as[Option[String]]
      nb <- c.downField("nullable").as[Option[Boolean]]
    } yield {
      f.fold[OpenapiSchemaStringType](
        OpenapiSchemaString(nb.getOrElse(false))
      ) {
        case "date"      => OpenapiSchemaDate(nb.getOrElse(false))
        case "date-time" => OpenapiSchemaDateTime(nb.getOrElse(false))
        case "byte"      => OpenapiSchemaByte(nb.getOrElse(false))
        case "binary"    => OpenapiSchemaBinary(nb.getOrElse(false))
        case "uuid"      => OpenapiSchemaUUID(nb.getOrElse(false))
        case _           => OpenapiSchemaString(nb.getOrElse(false))
      }
    }
  }

  implicit val OpenapiSchemaNumericTypeDecoder: Decoder[OpenapiSchemaNumericType] = { (c: HCursor) =>
    for {
      t <- c
        .downField("type")
        .as[String]
        .ensure(DecodingFailure("Given type is not number/integer!", c.history))(v => v == "number" || v == "integer")
      f <- c.downField("format").as[Option[String]]
      nb <- c.downField("nullable").as[Option[Boolean]]
    } yield {
      if (t == "number") {
        f.fold[OpenapiSchemaNumericType](
          OpenapiSchemaDouble(nb.getOrElse(false))
        ) {
          case "int64"  => OpenapiSchemaLong(nb.getOrElse(false))
          case "int32"  => OpenapiSchemaInt(nb.getOrElse(false))
          case "float"  => OpenapiSchemaFloat(nb.getOrElse(false))
          case "double" => OpenapiSchemaDouble(nb.getOrElse(false))
          case _        => OpenapiSchemaDouble(nb.getOrElse(false))
        }
      } else {
        f.fold[OpenapiSchemaNumericType](
          OpenapiSchemaInt(nb.getOrElse(false))
        ) {
          case "int64" => OpenapiSchemaLong(nb.getOrElse(false))
          case "int32" => OpenapiSchemaInt(nb.getOrElse(false))
          case _       => OpenapiSchemaInt(nb.getOrElse(false))
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

  implicit val OpenapiSchemaOneOfDecoder: Decoder[OpenapiSchemaOneOf] = { (c: HCursor) =>
    for {
      d <- c.downField("oneOf").as[Seq[OpenapiSchemaSimpleType]]
    } yield {
      OpenapiSchemaOneOf(d)
    }
  }

  implicit val OpenapiSchemaAllOfDecoder: Decoder[OpenapiSchemaAllOf] = { (c: HCursor) =>
    for {
      d <- c.downField("allOf").as[Seq[OpenapiSchemaSimpleType]]
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
      tpe <- c.downField("type").as[String]
      _ <- Either.cond(tpe == "string", (), DecodingFailure("only string enums are supported", c.history))
      items <- c.downField("enum").as[Seq[OpenapiSchemaConstantString]]
      nb <- c.downField("nullable").as[Option[Boolean]]
    } yield OpenapiSchemaEnum(tpe, items, nb.getOrElse(false))
  }

  def decodeReifiableValue(json: Json): ReifiableRenderableValue =
    json.fold(
      ReifiableValueNull,
      ReifiableValueBoolean.apply,
      n => n.toLong.map(ReifiableValueLong.apply).getOrElse(ReifiableValueDouble(n.toDouble)),
      ReifiableValueString.apply,
      arr => ReifiableValueList(arr.map(decodeReifiableValue)),
      obj => ReifiableValueMap(obj.toMap.map { case (k, v) => k -> decodeReifiableValue(v) })
    )
  implicit val ReifiableRenderableValueDecoder: Decoder[ReifiableRenderableValue] = { (c: HCursor) =>
    Right(decodeReifiableValue(c.value))
  }
  def decodeRenderable(maybeName: Option[String], cursor: ACursor): Option[RenderableValue] = maybeName match {
    case None => cursor.as[Option[ReifiableRenderableValue]].toOption.flatten
    case Some(name) =>
      cursor.focus
        .flatMap(_.asObject match {
          case Some(o) => Some(RenderableClassModel(name, o.toMap.map { case (k, v) => k -> decodeReifiableValue(v) }))
          case None    => cursor.as[Option[ReifiableRenderableValue]].toOption.flatten
        })
  }
  implicit val SchemaTypeWithDefaultDecoder: Decoder[(OpenapiSchemaType, Option[RenderableValue])] = { (c: HCursor) =>
    for {
      schemaType <- c.as[OpenapiSchemaType]
      maybeName = schemaType match {
        case OpenapiSchemaRef(ref) if ref.startsWith("#/components/schemas/") => Some(ref.stripPrefix("#/components/schemas/"))
        case _                                                                => None
      }
      maybeDefault = decodeRenderable(maybeName, c.downField("default"))
    } yield (schemaType, maybeDefault)
  }
  implicit val OpenapiSchemaObjectDecoder: Decoder[OpenapiSchemaObject] = { (c: HCursor) =>
    for {
      _ <- c.downField("type").as[String].ensure(DecodingFailure("Given type is not object!", c.history))(v => v == "object")
      fieldsWithDefaults <- c.downField("properties").as[Option[Map[String, (OpenapiSchemaType, Option[RenderableValue])]]]
      r <- c.downField("required").as[Option[Seq[String]]]
      nb <- c.downField("nullable").as[Option[Boolean]]
      fields = fieldsWithDefaults.getOrElse(Map.empty).map { case (k, (f, d)) => k -> OpenapiSchemaField(f, d) }
    } yield {
      OpenapiSchemaObject(fields, r.getOrElse(Seq.empty), nb.getOrElse(false))
    }
  }

  implicit val OpenapiSchemaMapDecoder: Decoder[OpenapiSchemaMap] = { (c: HCursor) =>
    for {
      _ <- c.downField("type").as[String].ensure(DecodingFailure("Given type is not object!", c.history))(v => v == "object")
      t <- c.downField("additionalProperties").as[OpenapiSchemaType]
      nb <- c.downField("nullable").as[Option[Boolean]]
    } yield {
      OpenapiSchemaMap(t, nb.getOrElse(false))
    }
  }

  implicit val OpenapiSchemaArrayDecoder: Decoder[OpenapiSchemaArray] = { (c: HCursor) =>
    for {
      _ <- c.downField("type").as[String].ensure(DecodingFailure("Given type is not array!", c.history))(v => v == "array" || v == "object")
      f <- c.downField("items").as[OpenapiSchemaType]
      nb <- c.downField("nullable").as[Option[Boolean]]
    } yield {
      OpenapiSchemaArray(f, nb.getOrElse(false))
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
