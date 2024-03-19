package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.indent
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaEnum,
  OpenapiSchemaMap,
  OpenapiSchemaObject,
  OpenapiSchemaOneOf,
  OpenapiSchemaRef
}

object JsonSerdeGenerator {
  val jsoniterBaseConfig = "com.github.plokhotnyuk.jsoniter_scala.macros.CodecMakerConfig.withAllowRecursiveTypes(true)"
  val jsoniteEnumConfig = s"$jsoniterBaseConfig.withDiscriminatorFieldName(scala.None)"
  def serdeDefs(
      doc: OpenapiDocument,
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib = JsonSerdeLib.Circe,
      jsonParamRefs: Set[String] = Set.empty,
      allTransitiveJsonParamRefs: Set[String] = Set.empty,
      fullModelPath: String = ""
  ): Option[String] = {
    val allSchemas: Map[String, OpenapiSchemaType] = doc.components.toSeq.flatMap(_.schemas).toMap
    val allOneOfSchemas = allSchemas.collect { case (name, oneOf: OpenapiSchemaOneOf) => name -> oneOf }.toSeq

    val adtInheritanceMap: Map[String, Seq[String]] = mkMapParentsByChild(allOneOfSchemas)

    jsonSerdeLib match {
      case JsonSerdeLib.Circe => genCirceSerdes(doc, allTransitiveJsonParamRefs)
      case JsonSerdeLib.Jsoniter =>
        genJsoniterSerdes(
          doc,
          allSchemas,
          jsonParamRefs,
          allTransitiveJsonParamRefs,
          adtInheritanceMap,
          if (fullModelPath.isEmpty) None else Some(fullModelPath)
        )
    }
  }

  ///
  /// Helpers
  ///

  private def mkMapParentsByChild(allOneOfSchemas: Seq[(String, OpenapiSchemaOneOf)]): Map[String, Seq[String]] =
    allOneOfSchemas
      .flatMap { case (name, schema) =>
        val children = schema.types.map {
          case ref: OpenapiSchemaRef if ref.isSchema => Right(ref.stripped)
          case other                                 => Left(other.getClass.getName)
        }
        children.collect { case Left(unsupportedChild) =>
          throw new NotImplementedError(
            s"oneOf declarations are only supported when all variants are declared schemas. Found type '$unsupportedChild' as variant of $name"
          )
        }
        val validatedChildren = children.collect { case Right(kv) => kv }
        schema.discriminator match {
          case None =>
          case Some(d) =>
            val targetClassNames = d.mapping.values.map(_.split('/').last).toSet
            if (targetClassNames != validatedChildren.toSet)
              throw new IllegalArgumentException(
                s"Discriminator values $targetClassNames did not match schema variants $validatedChildren for oneOf defn $name"
              )
        }
        validatedChildren.map(_ -> name)
      }
      .groupBy(_._1)
      .mapValues(_.map(_._2))

  ///
  /// Circe
  ///
  private def genCirceSerdes(doc: OpenapiDocument, allTransitiveJsonParamRefs: Set[String]): Option[String] = {
    doc.components
      .map(_.schemas.flatMap {
        // Enum serdes are generated at the declaration site
        case (_, _: OpenapiSchemaEnum) => None
        // We generate the serde if it's referenced in any json model
        case (name, _: OpenapiSchemaObject | _: OpenapiSchemaMap) if allTransitiveJsonParamRefs.contains(name) =>
          Some(genCirceNamedSerde(name))
        case (name, schema: OpenapiSchemaOneOf) if allTransitiveJsonParamRefs.contains(name) =>
          Some(genCirceAdtSerde(schema, name))
        case (_, _: OpenapiSchemaObject | _: OpenapiSchemaMap | _: OpenapiSchemaEnum | _: OpenapiSchemaOneOf) => None
        case (n, x) => throw new NotImplementedError(s"Only objects, enums, maps and oneOf supported! (for $n found ${x})")
      })
      .map(_.mkString("\n"))
  }

  private def genCirceNamedSerde(name: String): String = {
    val uncapitalisedName = name.head.toLower +: name.tail
    s"""implicit lazy val ${uncapitalisedName}JsonDecoder: io.circe.Decoder[$name] = io.circe.generic.semiauto.deriveDecoder[$name]
       |implicit lazy val ${uncapitalisedName}JsonEncoder: io.circe.Encoder[$name] = io.circe.generic.semiauto.deriveEncoder[$name]""".stripMargin
  }

  private def genCirceAdtSerde(schema: OpenapiSchemaOneOf, name: String): String = {
    val uncapitalisedName = name.head.toLower +: name.tail

    schema match {
      case OpenapiSchemaOneOf(_, Some(discriminator)) =>
        val schemaToJsonMapping = discriminator.mapping
          .map { case (jsonValue, fullRef) => fullRef.stripPrefix("#/components/schemas/") -> jsonValue }

        val subtypeNames = schema.types.map {
          case ref: OpenapiSchemaRef => ref.stripped
          case other => throw new IllegalArgumentException(s"oneOf subtypes must be refs to explicit schema models, found $other for $name")
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

  ///
  /// Jsoniter
  ///
  private def genJsoniterSerdes(
      doc: OpenapiDocument,
      allSchemas: Map[String, OpenapiSchemaType],
      jsonParamRefs: Set[String],
      allTransitiveJsonParamRefs: Set[String],
      adtInheritanceMap: Map[String, Seq[String]],
      fullModelPath: Option[String]
  ): Option[String] = {
    // For jsoniter-scala, we define explicit serdes for any 'primitive' params (e.g. List[java.util.UUID]) that we reference.
    // This should be the set of all json param refs not included in our schema definitions
    val additionalExplicitSerdes = jsonParamRefs.toSeq
      .filter(x => !allSchemas.contains(x))
      .map { s =>
        val name = s.replace("[", "_").replace("]", "_").replace(".", "_") + "JsonCodec"
        s"""implicit lazy val $name: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[$s] =
           |  com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make[$s]""".stripMargin
      }
      .mkString("", "\n", "\n")

    // Permits usage of Option/Seq wrapped classes at top level without having to be explicit
    val jsonSerdeHelpers =
      s"""
           |implicit def seqCodec[T: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec]: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[List[T]] =
           |  com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make[List[T]]
           |implicit def optionCodec[T: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec]: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[Option[T]] =
           |  com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make[Option[T]]
           |""".stripMargin
    doc.components
      .map(_.schemas.flatMap {
        // For standard objects, only generate the schema if it's a 'top level' json schema
        case (name, _: OpenapiSchemaObject) if jsonParamRefs.contains(name) =>
          Some(genJsoniterClassSerde(adtInheritanceMap)(name))
        // For named maps, only generate the schema if it's a 'top level' json schema
        case (name, _: OpenapiSchemaMap) if jsonParamRefs.contains(name) =>
          Some(genJsoniterNamedSerde(name))
        // For enums, generate the serde if it's referenced in any json model
        case (name, _: OpenapiSchemaEnum) if allTransitiveJsonParamRefs.contains(name) =>
          Some(genJsoniterEnumSerde(name))
        // For ADTs, generate the serde if it's referenced in any json model
        case (name, schema: OpenapiSchemaOneOf) if allTransitiveJsonParamRefs.contains(name) =>
          Some(generateJsoniterAdtSerde(name, schema, fullModelPath))
        case (_, _: OpenapiSchemaObject | _: OpenapiSchemaMap | _: OpenapiSchemaEnum | _: OpenapiSchemaOneOf) => None
        case (n, x) => throw new NotImplementedError(s"Only objects, enums, maps and oneOf supported! (for $n found ${x})")
      })
      .map(jsonSerdeHelpers + additionalExplicitSerdes + _.mkString("\n"))
  }

  private def genJsoniterClassSerde(adtInheritanceMap: Map[String, Seq[String]])(name: String): String = {
    val uncapitalisedName = name.head.toLower +: name.tail
    if (adtInheritanceMap.getOrElse(name, Nil).isEmpty)
      s"""implicit lazy val ${uncapitalisedName}JsonCodec: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[${name}] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make($jsoniterBaseConfig)"""
    else throw new NotImplementedError(s"A class cannot be used both in a oneOf at the top level when using jsoniter serdes at $name")
  }

  private def genJsoniterEnumSerde(name: String): String = {
    val uncapitalisedName = name.head.toLower +: name.tail
    s"""
       |implicit lazy val ${uncapitalisedName}JsonCodec: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[${name}] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make($jsoniterBaseConfig.withDiscriminatorFieldName(scala.None))""".stripMargin
  }

  private def genJsoniterNamedSerde(name: String): String = {
    val uncapitalisedName = name.head.toLower +: name.tail
    s"""
       |implicit lazy val ${uncapitalisedName}JsonCodec: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[$name] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make($jsoniterBaseConfig)""".stripMargin
  }

  private def generateJsoniterAdtSerde(
      name: String,
      schema: OpenapiSchemaOneOf,
      maybeFullModelPath: Option[String]
  ): String = {
    val fullPathPrefix = maybeFullModelPath.map(_ + ".").getOrElse("")
    val uncapitalisedName = name.head.toLower +: name.tail
    schema match {
      case OpenapiSchemaOneOf(_, Some(discriminator)) =>
        val schemaToJsonMapping = discriminator.mapping
          .map { case (jsonValue, fullRef) => fullRef.stripPrefix("#/components/schemas/") -> jsonValue }
        val body = if (schemaToJsonMapping.exists { case (className, jsonValue) => className != jsonValue }) {
          val discriminatorMap = indent(2)(
            schemaToJsonMapping
              .map { case (k, v) => s"""case "$fullPathPrefix$k" => "$v"""" }
              .mkString("\n", "\n", "\n")
          )
          val config =
            s"""$jsoniterBaseConfig.withRequireDiscriminatorFirst(false).withDiscriminatorFieldName(Some("${discriminator.propertyName}")).withAdtLeafClassNameMapper{$discriminatorMap}"""
          val serde =
            s"implicit lazy val ${uncapitalisedName}Codec: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[$name] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make($config)"

          s"""$serde
             |""".stripMargin
        } else {
          val config =
            s"""$jsoniterBaseConfig.withRequireDiscriminatorFirst(false).withDiscriminatorFieldName(Some("${discriminator.propertyName}"))"""
          s"implicit lazy val ${uncapitalisedName}Codec: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[$name] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make($config)"
        }
        body

      case OpenapiSchemaOneOf(_, None) =>
        val config = s"""$jsoniterBaseConfig.withRequireDiscriminatorFirst(false).withDiscriminatorFieldName(None)"""
        val serde =
          s"implicit lazy val ${uncapitalisedName}Codec: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[$name] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make($config)"
        serde
    }
  }
}
