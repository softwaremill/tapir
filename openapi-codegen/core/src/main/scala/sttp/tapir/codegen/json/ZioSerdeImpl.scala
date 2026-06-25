package sttp.tapir.codegen.json

import sttp.tapir.codegen.PackageReuseContext
import sttp.tapir.codegen.json.JsonHelpers.{checkForSoundness, inlineEndpointSchemas}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
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

object ZioSerdeImpl {

  ///
  /// Zio
  ///
  private[json] def genZioSerdes(
      doc: OpenapiDocument,
      allSchemas: Map[String, OpenapiSchemaType],
      allTransitiveJsonParamRefs: Set[String],
      validateNonDiscriminatedOneOfs: Boolean,
      targetScala3: Boolean,
      packageReuse: PackageReuseContext
  ): SerdeGenResponse = {
    val docSchemas = doc.components.toSeq.flatMap(_.schemas).map { case (n, t) => (n, t, allTransitiveJsonParamRefs.contains(n)) }
    val pathSchemas = inlineEndpointSchemas(doc)
    lazy val inheritedImpl = s"${packageReuse.dependencyModelPath}JsonSerdes"
    def zioParentImpl(name: String): Option[String] = if (
      PackageReuseContext.isReusedSchema(name, packageReuse) &&
      packageReuse.dependencyMeta.allTransitiveJsonParamRefs.contains(name)
    ) {
      val uncapitalisedName = uncapitalise(name)
      val codecName = s"${uncapitalisedName}JsonCodec"
      Some(s"implicit lazy val $codecName: zio.json.JsonCodec[$name] = $inheritedImpl.$codecName")
    } else None
    val serdesDefn = (docSchemas ++ pathSchemas)
      .flatMap {
        // Only enumeratum (scala 2) enum types currently supported for zio-json
        case (name, _: OpenapiSchemaEnum, true) if !targetScala3 =>
          zioParentImpl(name) orElse Some(genZioEnumSerde(name))
        // We generate the serde if it's referenced in any json model
        case (name, schema: OpenapiSchemaObject, true) =>
          zioParentImpl(name) orElse Some(genZioObjectSerde(name, schema))
        case (name, schema: OpenapiSchemaMap, true) =>
          zioParentImpl(name) orElse Some(genZioMapOrArraySerde(name, schema.items))
        case (name, schema: OpenapiSchemaArray, true) =>
          zioParentImpl(name) orElse Some(genZioMapOrArraySerde(name, schema.items))
        case (name, schema: OpenapiSchemaOneOf, true) =>
          zioParentImpl(name) orElse Some(genZioAdtSerde(allSchemas, schema, name, validateNonDiscriminatedOneOfs))
        case (_, _: OpenapiSchemaObject | _: OpenapiSchemaMap | _: OpenapiSchemaArray | _: OpenapiSchemaEnum | _: OpenapiSchemaOneOf, _) =>
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
      .map(s =>
        if (packageReuse.reusedSchemas.nonEmpty)
          s"implicit lazy val byteStringJsonCodec: zio.json.JsonCodec[ByteString] = $inheritedImpl.byteStringJsonCodec\n$s"
        else
          s"""implicit lazy val byteStringJsonCodec: zio.json.JsonCodec[ByteString] = zio.json.JsonCodec[ByteString](
             |  zio.json.JsonEncoder[String].contramap[ByteString](java.util.Base64.getEncoder.encodeToString),
             |  zio.json.JsonDecoder[String].mapOrFail(s => scala.util.Try(java.util.Base64.getDecoder.decode(s)).toEither.map(toByteString).left.map(error => error.getMessage)),
             |)
             |$s""".stripMargin
      )
    SerdeGenResponse(serdesDefn, Nil)
  }

  private def genZioObjectSerde(name: String, schema: OpenapiSchemaObject): String = {
    val subs = schema.properties.collect {
      case (k, OpenapiSchemaField(`type`: OpenapiSchemaObject, _, _)) => genZioObjectSerde(s"$name${k.capitalize}", `type`)
      case (k, OpenapiSchemaField(OpenapiSchemaArray(`type`: OpenapiSchemaObject, _, _, _), _, _)) =>
        genZioObjectSerde(s"$name${k.capitalize}Item", `type`)
      case (k, OpenapiSchemaField(OpenapiSchemaMap(`type`: OpenapiSchemaObject, _, _), _, _)) =>
        genZioObjectSerde(s"$name${k.capitalize}Item", `type`)
    } match {
      case s if s.isEmpty => ""
      case s              => s.mkString("", "\n", "\n")
    }
    val uncapitalisedName = uncapitalise(name)
    s"""${subs}implicit lazy val ${uncapitalisedName}JsonDecoder: zio.json.JsonDecoder[$name] = zio.json.DeriveJsonDecoder.gen[$name]
       |implicit lazy val ${uncapitalisedName}JsonEncoder: zio.json.JsonEncoder[$name] = zio.json.DeriveJsonEncoder.gen[$name]""".stripMargin
  }

  private def genZioMapOrArraySerde(name: String, schema: OpenapiSchemaType): String = {
    val subs = schema match {
      case `type`: OpenapiSchemaObject => Some(genZioObjectSerde(s"${name}ObjectsItem", `type`))
      case _                           => None
    }
    subs.fold("")("\n" + _)
  }

  private def genZioEnumSerde(name: String): String = {
    val uncapitalisedName = uncapitalise(name)
    s"""
       |implicit lazy val ${uncapitalisedName}JsonCodec: zio.json.JsonCodec[$name] = zio.json.JsonCodec[$name](
       |  zio.json.JsonEncoder[String].contramap[$name](_.entryName),
       |  zio.json.JsonDecoder[String].mapOrFail(name => $name.withNameEither(name).left.map(error => error.getMessage)),
       |)""".stripMargin
  }

  private def genZioAdtSerde(
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
            s"""case x: $t => zio.json.ast.Json.decoder.decodeJson(zio.json.JsonEncoder[$t].encodeJson(x)).getOrElse(throw new RuntimeException("Unable to encode tagged ADT type ${name} to json")).mapObject(_.add("${discriminator.propertyName}", zio.json.ast.Json.Str("$jsonTypeName")))"""
          }
          .mkString("\n")
        val decoders = subtypeNames
          .map { t => s"""case zio.json.ast.Json.Str("${schemaToJsonMapping(t)}") => zio.json.JsonDecoder[$t].fromJsonAST(json)""" }
          .mkString("\n")
        s"""implicit lazy val ${uncapitalisedName}JsonEncoder: zio.json.JsonEncoder[$name] = zio.json.JsonEncoder[zio.json.ast.Json].contramap {
           |${indent(2)(encoders)}
           |}
           |implicit lazy val ${uncapitalisedName}JsonDecoder: zio.json.JsonDecoder[$name] = zio.json.JsonDecoder[zio.json.ast.Json].mapOrFail {
           |  case json@zio.json.ast.Json.Obj(fields) =>
           |    (fields.find(_._1 == "type") match {
           |      case None => Left("Unable to decode json to tagged ADT type ${name}")
           |      case Some(r) => Right(r._2)
           |    }).flatMap {
           |${indent(6)(decoders)}
           |      case _ => Left("Unable to decode json to tagged ADT type ${name}")
           |    }
           |  case _ => Left("Unable to decode json to tagged ADT type ${name}")
           |}""".stripMargin
      case OpenapiSchemaOneOf(_, None) =>
        val subtypeNames = schema.types.map {
          case ref: OpenapiSchemaRef => ref.stripped
          case other => throw new IllegalArgumentException(s"oneOf subtypes must be refs to explicit schema models, found $other for $name")
        }
        if (validateNonDiscriminatedOneOfs) checkForSoundness(name, allSchemas)(schema.types.map(_.asInstanceOf[OpenapiSchemaRef]))
        val encoders = subtypeNames.map(t => s"case x: $t => zio.json.JsonEncoder[$t].unsafeEncode(x, indent, out)").mkString("\n")
        val decoders = subtypeNames.map(t => s"zio.json.JsonDecoder[$t].asInstanceOf[zio.json.JsonDecoder[$name]]").mkString(",\n")
        s"""implicit lazy val ${uncapitalisedName}JsonEncoder: zio.json.JsonEncoder[$name] = new zio.json.JsonEncoder[$name] {
           |  override def unsafeEncode(v: $name, indent: Option[Int], out: zio.json.internal.Write): Unit = {
           |    v match {
           |${indent(6)(encoders)}
           |    }
           |  }
           |}
           |implicit lazy val ${uncapitalisedName}JsonDecoder: zio.json.JsonDecoder[$name] =
           |  List[zio.json.JsonDecoder[$name]](
           |${indent(4)(decoders)}
           |  ).reduceLeft(_ orElse _)""".stripMargin
    }
  }
}
