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

  case class OpenapiSchemaBoolean(
      nullable: Boolean
  ) extends OpenapiSchemaSimpleType

  case class OpenapiSchemaRef(
      name: String
  ) extends OpenapiSchemaSimpleType {
    val nullable = false
  }

  // no minItems/maxItems, uniqueItems support
  case class OpenapiSchemaArray(
      items: OpenapiSchemaType,
      nullable: Boolean
  ) extends OpenapiSchemaType

  // no readOnly/writeOnly, minProperties/maxProperties support
  case class OpenapiSchemaObject(
      properties: Map[String, OpenapiSchemaType],
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

  implicit val OpenapiSchemaObjectDecoder: Decoder[OpenapiSchemaObject] = { (c: HCursor) =>
    for {
      _ <- c
        .downField("type")
        .as[Option[String]]
        .ensure(DecodingFailure("Given type is not object!", c.history))(v => v.forall(_ == "object"))
      f <- c.downField("properties").as[Map[String, OpenapiSchemaType]]
      r <- c.downField("required").as[Option[Seq[String]]]
      nb <- c.downField("nullable").as[Option[Boolean]]
    } yield {
      OpenapiSchemaObject(f, r.getOrElse(Seq.empty), nb.getOrElse(false))
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
      _ <- c.downField("type").as[String].ensure(DecodingFailure("Given type is not array!", c.history))(v => v == "array")
      f <- c.downField("items").as[OpenapiSchemaType]
      nb <- c.downField("nullable").as[Option[Boolean]]
    } yield {
      OpenapiSchemaArray(f, nb.getOrElse(false))
    }
  }

  implicit lazy val OpenapiSchemaTypeDecoder: Decoder[OpenapiSchemaType] =
    List[Decoder[OpenapiSchemaType]](
      Decoder[OpenapiSchemaSimpleType].widen,
      Decoder[OpenapiSchemaMixedType].widen,
      Decoder[OpenapiSchemaNot].widen,
      Decoder[OpenapiSchemaObject].widen,
      Decoder[OpenapiSchemaMap].widen,
      Decoder[OpenapiSchemaArray].widen
    ).reduceLeft(_ or _)
}
