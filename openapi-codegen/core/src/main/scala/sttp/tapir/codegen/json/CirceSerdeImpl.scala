package sttp.tapir.codegen.json

import sttp.tapir.codegen.PackageReuseContext
import sttp.tapir.codegen.json.JsonHelpers.{checkForSoundness, inlineEndpointSchemas}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaAny,
  OpenapiSchemaArray,
  OpenapiSchemaEnum,
  OpenapiSchemaField,
  OpenapiSchemaMap,
  OpenapiSchemaObject,
  OpenapiSchemaOneOf,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType
}
import sttp.tapir.codegen.util.NameHelpers.{indent, uncapitalise}

object CirceSerdeImpl {

  ///
  /// Circe
  ///
  private[json] def genCirceSerdes(
      doc: OpenapiDocument,
      allSchemas: Map[String, OpenapiSchemaType],
      allTransitiveJsonParamRefs: Set[String],
      validateNonDiscriminatedOneOfs: Boolean,
      packageReuse: PackageReuseContext
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
      val companion = s"${packageReuse.dependencyModelPath}.$name"
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
          circeParentImpl(name) orElse Some(genCirceMapOrArraySerde(name, schema.items))
        case (name, schema: OpenapiSchemaArray, true) =>
          circeParentImpl(name) orElse Some(genCirceMapOrArraySerde(name, schema.items))
        case (name, schema: OpenapiSchemaOneOf, true) =>
          circeParentImpl(name) orElse Some(genCirceAdtSerde(allSchemas, schema, name, validateNonDiscriminatedOneOfs))
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
  private def genCirceMapOrArraySerde(name: String, schema: OpenapiSchemaType): String = {
    val subs = schema match {
      case `type`: OpenapiSchemaObject => Some(genCirceObjectSerde(s"${name}ObjectsItem", `type`))
      case _                           => None
    }
    subs.fold("")("\n" + _)
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
}
