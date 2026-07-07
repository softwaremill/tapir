package sttp.tapir.codegen.json

import sttp.tapir.codegen.json.JsonHelpers.{checkForSoundness, inlineEndpointSchemas}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaAny,
  OpenapiSchemaArray,
  OpenapiSchemaBoolean,
  OpenapiSchemaDate,
  OpenapiSchemaDateTime,
  OpenapiSchemaDuration,
  OpenapiSchemaEnum,
  OpenapiSchemaField,
  OpenapiSchemaMap,
  OpenapiSchemaNumericType,
  OpenapiSchemaObject,
  OpenapiSchemaOneOf,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType,
  OpenapiSchemaString,
  OpenapiSchemaStringType,
  OpenapiSchemaUUID
}
import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.util.NameHelpers.{indent, uncapitalise}

object CirceSerdeImpl {

  private[json] def genCirceSerdes(
      doc: OpenapiDocument,
      allSchemas: Map[String, OpenapiSchemaType],
      allTransitiveJsonParamRefs: Set[String],
      validateNonDiscriminatedOneOfs: Boolean,
      packageReuse: PackageReuseContext,
      seperateFilesForModels: Boolean,
  ): SerdeGenResponse = {
    val docSchemas = doc.components.toSeq.flatMap(_.schemas).map { case (n, t) => (n, t, allTransitiveJsonParamRefs.contains(n)) }
    val pathSchemas = inlineEndpointSchemas(doc)
    def circeParentImpl(name: String): Option[String] = if (
      PackageReuseContext.isReusedSchema(name, packageReuse) &&
      packageReuse.dependencyMeta.allTransitiveJsonParamRefs.contains(name)
    ) {
      val inheritedImpl = s"${packageReuse.dependencyModelPath}JsonSerdes"
      val uncapitalisedName = uncapitalise(name)
      val decoderName = s"${uncapitalisedName}JsonDecoder"
      val encoderName = s"${uncapitalisedName}JsonEncoder"
      Some(s"""implicit lazy val $decoderName: io.circe.Decoder[$name] = $inheritedImpl.$decoderName
              |implicit lazy val $encoderName: io.circe.Encoder[$name] = $inheritedImpl.$encoderName""")
    } else None
    def genCirceReusedEnumSerde(name: String): String = {
      val uncapitalisedName = uncapitalise(name)
      val decoderName = s"${uncapitalisedName}JsonDecoder"
      val encoderName = s"${uncapitalisedName}JsonEncoder"
      val companion = s"${packageReuse.modelRoot(seperateFilesForModels)}.$name"
      s"""implicit lazy val $decoderName: io.circe.Decoder[$name] = enumeratum.Circe.decoder($companion)
         |implicit lazy val $encoderName: io.circe.Encoder[$name] = enumeratum.Circe.encoder($companion)"""
    }
    val serdesDefn = (docSchemas ++ pathSchemas)
      .flatMap {
        // Reused enums are aliased; generate codecs here when referenced in json but wasn't 'before'
        case (name, _: OpenapiSchemaEnum, true) if PackageReuseContext.isReusedSchema(name, packageReuse) =>
          circeParentImpl(name) orElse Some(genCirceReusedEnumSerde(name))
        // Enum serdes are generated at the declaration site
        case (_, _: OpenapiSchemaEnum, _) => None
        // We generate the serde if it's referenced in any json model
        case (name, schema: OpenapiSchemaObject, true) =>
          circeParentImpl(name) orElse Some(genCirceObjectSerde(name, schema))
        case (name, schema: OpenapiSchemaMap, true) =>
          genCirceMapOrArraySerde(name, schema.items).map { case (n, impl) => circeParentImpl(n).getOrElse(impl) }
        case (name, schema: OpenapiSchemaArray, true) =>
          genCirceMapOrArraySerde(name, schema.items).map { case (n, impl) => circeParentImpl(n).getOrElse(impl) }
        case (name, schema: OpenapiSchemaOneOf, true) =>
          circeParentImpl(name) orElse Some(
            if (schema.types.exists(!_.isInstanceOf[OpenapiSchemaRef]))
              genCirceWrappedOneOfSerde(name, schema)
            else
              genCirceAdtSerde(allSchemas, schema, name, validateNonDiscriminatedOneOfs)
          )
        case (
              _,
              _: OpenapiSchemaObject | _: OpenapiSchemaArray | _: OpenapiSchemaMap | _: OpenapiSchemaEnum | _: OpenapiSchemaOneOf |
              _: OpenapiSchemaAny,
              _
            ) =>
          None
        case (_, t: OpenapiSchemaSimpleType, _) if !t.isInstanceOf[OpenapiSchemaRef] => None
        case (n, x, _) => throw new NotImplementedError(s"Only objects, enums, maps, arrays and oneOf supported! (for $n found ${x})")
      }
      .toSeq
      .sorted
      .foldLeft(Option.empty[String]) {
        case (Some(a), b) => Some(a + "\n" + b)
        case (None, a)    => Some(a)
      }
      .map(s => s"""implicit val byteStringJsonDecoder: io.circe.Decoder[ByteString] =
                   |  io.circe.Decoder.decodeString
                   |    .map(java.util.Base64.getDecoder.decode)
                   |    .map(toByteString)
                   |implicit val byteStringJsonEncoder: io.circe.Encoder[ByteString] =
                   |  io.circe.Encoder.encodeString
                   |    .contramap(java.util.Base64.getEncoder.encodeToString)
                   |$s""".stripMargin)
    SerdeGenResponse(serdesDefn, Nil)
  }

  private def genCirceObjectSerde(name: String, schema: OpenapiSchemaObject): String = {
    val subs = schema.properties.collect {
      case (k, OpenapiSchemaField(`type`: OpenapiSchemaObject, _, _)) => genCirceObjectSerde(s"$name${k.capitalize}", `type`)
      case (k, OpenapiSchemaField(OpenapiSchemaArray(`type`: OpenapiSchemaObject, _, _, _), _, _)) =>
        genCirceObjectSerde(s"$name${k.capitalize}Item", `type`)
      case (k, OpenapiSchemaField(OpenapiSchemaMap(`type`: OpenapiSchemaObject, _, _), _, _)) =>
        genCirceObjectSerde(s"$name${k.capitalize}Item", `type`)
    } match {
      case s if s.isEmpty => ""
      case s              => s.mkString("", "\n", "\n")
    }
    val uncapitalisedName = uncapitalise(name)
    s"""${subs}implicit lazy val ${uncapitalisedName}JsonDecoder: io.circe.Decoder[$name] = io.circe.generic.semiauto.deriveDecoder[$name]
       |implicit lazy val ${uncapitalisedName}JsonEncoder: io.circe.Encoder[$name] = io.circe.generic.semiauto.deriveEncoder[$name]""".stripMargin
  }
  private def genCirceMapOrArraySerde(name: String, schema: OpenapiSchemaType): Option[(String, String)] = {
    schema match {
      case `type`: OpenapiSchemaObject =>
        val inlineItemName = s"${name}ObjectsItem"
        Some(inlineItemName -> ("\n" + genCirceObjectSerde(inlineItemName, `type`)))
      case _ => None
    }
  }

  private def genCirceAdtSerde(
      allSchemas: Map[String, OpenapiSchemaType],
      schema: OpenapiSchemaOneOf,
      name: String,
      validateNonDiscriminatedOneOfs: Boolean
  ): String = {
    val uncapitalisedName = uncapitalise(name)

    schema match {
      case OpenapiSchemaOneOf(_, Some(discriminator)) =>
        val subtypeNames = schema.types.map {
          case ref: OpenapiSchemaRef => ref.stripped
          case other => throw new IllegalArgumentException(s"oneOf subtypes must be refs to explicit schema models, found $other for $name")
        }
        val schemaToJsonMapping = discriminator.mapping match {
          case Some(mapping) =>
            mapping.map { case (jsonValue, fullRef) => fullRef.stripPrefix("#/components/schemas/") -> jsonValue }
          case None => subtypeNames.map(s => s -> s).toMap
        }
        val encoders = subtypeNames
          .map { t =>
            val jsonTypeName = schemaToJsonMapping(t)
            s"""case x: $t => io.circe.Encoder[$t].apply(x).mapObject(_.add("${discriminator.propertyName}", io.circe.Json.fromString("$jsonTypeName")))"""
          }
          .mkString("\n")
        val decoders = subtypeNames
          .map { t => s"""case "${schemaToJsonMapping(t)}" => c.as[$t]""" }
          .mkString("\n")
        s"""implicit lazy val ${uncapitalisedName}JsonEncoder: io.circe.Encoder[$name] = io.circe.Encoder.instance {
           |${indent(2)(encoders)}
           |}
           |implicit lazy val ${uncapitalisedName}JsonDecoder: io.circe.Decoder[$name] = io.circe.Decoder { (c: io.circe.HCursor) =>
           |  for {
           |    discriminator <- c.downField("${discriminator.propertyName}").as[String]
           |    res <- discriminator match {
           |${indent(6)(decoders)}
           |    }
           |  } yield res
           |}""".stripMargin
      case OpenapiSchemaOneOf(_, None) =>
        val subtypeNames = schema.types.map {
          case ref: OpenapiSchemaRef => ref.stripped
          case other => throw new IllegalArgumentException(s"oneOf subtypes must be refs to explicit schema models, found $other for $name")
        }
        if (validateNonDiscriminatedOneOfs) checkForSoundness(name, allSchemas)(schema.types.map(_.asInstanceOf[OpenapiSchemaRef]))
        val encoders = subtypeNames.map(t => s"case x: $t => io.circe.Encoder[$t].apply(x)").mkString("\n")
        val decoders = subtypeNames.map(t => s"io.circe.Decoder[$t].asInstanceOf[io.circe.Decoder[$name]]").mkString(",\n")
        s"""implicit lazy val ${uncapitalisedName}JsonEncoder: io.circe.Encoder[$name] = io.circe.Encoder.instance {
           |${indent(2)(encoders)}
           |}
           |implicit lazy val ${uncapitalisedName}JsonDecoder: io.circe.Decoder[$name] =
           |  List[io.circe.Decoder[$name]](
           |${indent(4)(decoders)}
           |  ).reduceLeft(_ or _)""".stripMargin
    }
  }

  private def genCirceWrappedOneOfSerde(
      name: String,
      schema: OpenapiSchemaOneOf
  ): String = {
    val uncapitalisedName = uncapitalise(name)
    val (refSchemas, otherSchemas) = schema.types.partition(_.isInstanceOf[OpenapiSchemaRef])
    val maybeBoolSchema = otherSchemas.collectFirst { case b: OpenapiSchemaBoolean => b }
    val maybeNumericSchema = otherSchemas.collectFirst { case n: OpenapiSchemaNumericType => n }
    val maybeAnySchema = otherSchemas.collectFirst { case a: OpenapiSchemaAny => a }
    val stringSchemas = otherSchemas.collect { case a: OpenapiSchemaStringType => a }

    val childNameAndSerde = refSchemas.map { case ref: OpenapiSchemaRef =>
      val typeName = ref.stripped
      typeName -> s"${uncapitalise(typeName)}JsonDecoder"
    }
    val doBoolDecode = maybeBoolSchema.map { _ =>
      s"io.circe.Decoder[Boolean].map(${name}Boolean(_)).asInstanceOf[io.circe.Decoder[$name]]"
    }.toSeq
    val doNumericDecode = maybeNumericSchema.map { s =>
      s"io.circe.Decoder[${s.scalaType}].map($name${s.scalaType}(_)).asInstanceOf[io.circe.Decoder[$name]]"
    }.toSeq
    val doStringDecodes =
      if (stringSchemas.isEmpty) None
      else {
        val tries = stringSchemas.zipWithIndex
          .map { case (s, i) =>
            val (parseImpl, wrapper) = s match {
              case _: OpenapiSchemaDate     => "java.time.LocalDate.parse(succ)" -> s"${name}Date"
              case _: OpenapiSchemaDateTime => "java.time.Instant.parse(succ)" -> s"${name}DateTime"
              case _: OpenapiSchemaDuration => "java.time.Duration.parse(succ)" -> s"${name}Duration"
              case _: OpenapiSchemaUUID     => "java.util.UUID.fromString(succ)" -> s"${name}UUID"
              case _                        => "succ" -> s"${name}String"
            }
            if (i == 0) s"scala.util.Try($parseImpl).map($wrapper(_))" else s".orElse(scala.util.Try($parseImpl).map($wrapper(_)))"
          }
          .mkString("\n        ")
        val decode =
          s"""io.circe.Decoder[String].emap { succ =>
             |  $tries
             |    .toEither.left.map(_ => "unable to parse string to acceptable type")
             |}.asInstanceOf[io.circe.Decoder[$name]]""".stripMargin
        Some(decode)
      }
    val doRefDecodes = childNameAndSerde.map { case (typeName, decoderName) =>
      s"io.circe.Decoder[$typeName].map($name${typeName.capitalize}(_)).asInstanceOf[io.circe.Decoder[$name]]"
    }
    val doAnyDecode = maybeAnySchema.map { _ =>
      s"io.circe.Decoder[io.circe.Json].map(${name}Json(_)).asInstanceOf[io.circe.Decoder[$name]]"
    }.toSeq
    val decoders = (doBoolDecode ++ doNumericDecode ++ doStringDecodes ++ doRefDecodes ++ doAnyDecode).mkString(",\n    ")
    val encoders =
      (maybeBoolSchema.map { _ => s"case x: ${name}Boolean => io.circe.Encoder[Boolean].apply(x.v)" }.toSeq ++
        maybeNumericSchema.map { s => s"case x: $name${s.scalaType} => io.circe.Encoder[${s.scalaType}].apply(x.v)" }.toSeq ++
        maybeAnySchema.map { _ => s"case x: ${name}Json => io.circe.Encoder[io.circe.Json].apply(x.v)" }.toSeq ++
        stringSchemas.map {
          case s: OpenapiSchemaString => s"case x: ${name}String => io.circe.Encoder[String].apply(x.v)"
          case s                      =>
            s"case x: ${name}${s.disambiguationSuffix} => io.circe.Encoder[String].apply(x.v.toString)"
        } ++
        childNameAndSerde.map { case (subName, _) => s"case x: $name${subName} => io.circe.Encoder[$subName].apply(x.v)" })
        .mkString("\n    ")
    s"""implicit lazy val ${uncapitalisedName}JsonEncoder: io.circe.Encoder[$name] = io.circe.Encoder.instance {
       |    $encoders
       |  }
       |  implicit lazy val ${uncapitalisedName}JsonDecoder: io.circe.Decoder[$name] =
       |    List[io.circe.Decoder[$name]](
       |    $decoders
       |    ).reduceLeft(_ or _)""".stripMargin
  }
}
