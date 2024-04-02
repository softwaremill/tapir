package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.indent
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaBoolean,
  OpenapiSchemaEnum,
  OpenapiSchemaMap,
  OpenapiSchemaNumericType,
  OpenapiSchemaObject,
  OpenapiSchemaOneOf,
  OpenapiSchemaRef,
  OpenapiSchemaString,
  OpenapiSchemaStringType
}

import scala.annotation.tailrec

object JsonSerdeGenerator {
  def serdeDefs(
      doc: OpenapiDocument,
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      jsonParamRefs: Set[String],
      allTransitiveJsonParamRefs: Set[String],
      fullModelPath: String,
      validateNonDiscriminatedOneOfs: Boolean,
      adtInheritanceMap: Map[String, Seq[String]]
  ): Option[String] = {
    val allSchemas: Map[String, OpenapiSchemaType] = doc.components.toSeq.flatMap(_.schemas).toMap

    jsonSerdeLib match {
      case JsonSerdeLib.Circe => genCirceSerdes(doc, allSchemas, allTransitiveJsonParamRefs, validateNonDiscriminatedOneOfs)
      case JsonSerdeLib.Jsoniter =>
        genJsoniterSerdes(
          doc,
          allSchemas,
          jsonParamRefs,
          allTransitiveJsonParamRefs,
          adtInheritanceMap,
          if (fullModelPath.isEmpty) None else Some(fullModelPath),
          validateNonDiscriminatedOneOfs
        )
    }
  }

  ///
  /// Helpers
  ///
  private def checkForSoundness(allSchemas: Map[String, OpenapiSchemaType])(variants: Seq[OpenapiSchemaRef]) = if (variants.size <= 1) false
  else {
    @tailrec def resolve(variant: OpenapiSchemaRef): OpenapiSchemaType = allSchemas(variant.stripped) match {
      case ref: OpenapiSchemaRef => resolve(ref)
      case resolved              => resolved
    }
    def maybeResolve(variant: OpenapiSchemaType): OpenapiSchemaType = variant match {
      case ref: OpenapiSchemaRef => resolve(ref)
      case other                 => other
    }
    def rCanLookLikeL(lhs: OpenapiSchemaType, rhs: OpenapiSchemaType): Boolean = (lhs, rhs) match {
      // check for equality first
      case (l, r) if l == r => true
      // then resolve any refs
      case (l: OpenapiSchemaRef, r) => rCanLookLikeL(resolve(l), r)
      case (l, r: OpenapiSchemaRef) => rCanLookLikeL(l, resolve(r))
      // nullable types can always look like each other since can be null
      case (l, r) if l.nullable && r.nullable => true
      // l enum can look like r enum if there's a value in common
      case (l: OpenapiSchemaEnum, r: OpenapiSchemaEnum) => l.items.map(_.value).toSet.intersect(r.items.map(_.value).toSet).nonEmpty
      // stringy subclasses of same type can look like each other
      case (l: OpenapiSchemaStringType, r: OpenapiSchemaStringType) if l.getClass == r.getClass => true
      // a string can look like any stringy type, and vice-versa
      case (_: OpenapiSchemaString, _: OpenapiSchemaStringType) | (_: OpenapiSchemaStringType, _: OpenapiSchemaString) => true
      // any numeric type can always look like any other (123 is valid for all subtypes, for example)
      case (_: OpenapiSchemaNumericType, _: OpenapiSchemaNumericType) => true
      // bools can always look like each other
      case (_: OpenapiSchemaBoolean, _: OpenapiSchemaBoolean) => true
      // arrays can always look like each other (if empty). We don't know about non-empty arrays yet.
      case (_: OpenapiSchemaArray, _: OpenapiSchemaArray) => true
      // objects need to recurse
      case (l: OpenapiSchemaObject, r: OpenapiSchemaObject) =>
        val requiredL =
          l.properties.filter(l.required contains _._1).filter { case (_, t) => t.default.isEmpty && !maybeResolve(t.`type`).nullable }
        val anyR = r.properties
        // if lhs has some required non-nullable fields with no default that rhs will never contain, then right cannot be mistaken for left
        if ((requiredL.keySet -- anyR.keySet).nonEmpty) false
        else {
          // otherwise, if any required field on rhs can't look like the similarly-named field on lhs, then r can't look like l
          val rForRequiredL = anyR.filter(requiredL.keySet contains _._1)
          requiredL.forall { case (k, lhsV) => rCanLookLikeL(lhsV.`type`, rForRequiredL(k).`type`) }
        }
      // Let's not support nested oneOfs for now, it's complex and I'm not sure if it's legal
      case (_: OpenapiSchemaOneOf, _) | (_, _: OpenapiSchemaOneOf) => throw new NotImplementedError("Not supported")
      // I think at this point we're ok
      case _ => false
    }
    val withAllSubsequent = variants.scanRight(Seq.empty[OpenapiSchemaRef])(_ +: _).collect {
      case h +: t if t.nonEmpty => (h, t)
    }
    val problems = withAllSubsequent
      .flatMap { case (variant, fallbacks) => fallbacks.filter(rCanLookLikeL(variant, _)).map(variant -> _) }
      .map { case (l, r) => s"${l.name} appears before ${r.name}, but a ${r.name} can be a valid ${l.name}" }
    if (problems.nonEmpty)
      throw new IllegalArgumentException(problems.mkString("Problems in non-discriminated oneOf declaration (", "; ", ")"))
  }
  ///
  /// Circe
  ///
  private def genCirceSerdes(
      doc: OpenapiDocument,
      allSchemas: Map[String, OpenapiSchemaType],
      allTransitiveJsonParamRefs: Set[String],
      validateNonDiscriminatedOneOfs: Boolean
  ): Option[String] = {
    doc.components
      .map(_.schemas.flatMap {
        // Enum serdes are generated at the declaration site
        case (_, _: OpenapiSchemaEnum) => None
        // We generate the serde if it's referenced in any json model
        case (name, _: OpenapiSchemaObject | _: OpenapiSchemaMap) if allTransitiveJsonParamRefs.contains(name) =>
          Some(genCirceNamedSerde(name))
        case (name, schema: OpenapiSchemaOneOf) if allTransitiveJsonParamRefs.contains(name) =>
          Some(genCirceAdtSerde(allSchemas, schema, name, validateNonDiscriminatedOneOfs))
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

  private def genCirceAdtSerde(
      allSchemas: Map[String, OpenapiSchemaType],
      schema: OpenapiSchemaOneOf,
      name: String,
      validateNonDiscriminatedOneOfs: Boolean
  ): String = {
    val uncapitalisedName = name.head.toLower +: name.tail

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
        if (validateNonDiscriminatedOneOfs) checkForSoundness(allSchemas)(schema.types.map(_.asInstanceOf[OpenapiSchemaRef]))
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
  private val jsoniterPkgRoot = "com.github.plokhotnyuk.jsoniter_scala"
  private val jsoniterPkgCore = s"$jsoniterPkgRoot.core"
  private val jsoniterPkgMacros = s"$jsoniterPkgRoot.macros"
  private val jsoniterBaseConfig = s"$jsoniterPkgMacros.CodecMakerConfig.withAllowRecursiveTypes(true)"
  private val jsoniteEnumConfig = s"$jsoniterBaseConfig.withDiscriminatorFieldName(scala.None)"
  private def genJsoniterSerdes(
      doc: OpenapiDocument,
      allSchemas: Map[String, OpenapiSchemaType],
      jsonParamRefs: Set[String],
      allTransitiveJsonParamRefs: Set[String],
      adtInheritanceMap: Map[String, Seq[String]],
      fullModelPath: Option[String],
      validateNonDiscriminatedOneOfs: Boolean
  ): Option[String] = {
    // For jsoniter-scala, we define explicit serdes for any 'primitive' params (e.g. List[java.util.UUID]) that we reference.
    // This should be the set of all json param refs not included in our schema definitions
    val additionalExplicitSerdes = jsonParamRefs.toSeq
      .filter(x => !allSchemas.contains(x))
      .map { s =>
        val name = s.replace("[", "_").replace("]", "_").replace(".", "_") + "JsonCodec"
        s"""implicit lazy val $name: $jsoniterPkgCore.JsonValueCodec[$s] =
           |  $jsoniterPkgMacros.JsonCodecMaker.make[$s]""".stripMargin
      }
      .mkString("", "\n", "\n")

    // Permits usage of Option/Seq wrapped classes at top level without having to be explicit
    val jsonSerdeHelpers =
      s"""
           |implicit def seqCodec[T: $jsoniterPkgCore.JsonValueCodec]: $jsoniterPkgCore.JsonValueCodec[List[T]] =
           |  $jsoniterPkgMacros.JsonCodecMaker.make[List[T]]
           |implicit def optionCodec[T: $jsoniterPkgCore.JsonValueCodec]: $jsoniterPkgCore.JsonValueCodec[Option[T]] =
           |  $jsoniterPkgMacros.JsonCodecMaker.make[Option[T]]
           |""".stripMargin
    doc.components
      .map(_.schemas.flatMap {
        // For standard objects, generate the schema if it's a 'top level' json schema or if it's referenced as a subtype of an ADT without a discriminator
        case (name, _: OpenapiSchemaObject) =>
          val supertypes =
            adtInheritanceMap.get(name).getOrElse(Nil).map(allSchemas.apply).collect { case oneOf: OpenapiSchemaOneOf => oneOf }
          if (jsonParamRefs.contains(name) || supertypes.exists(_.discriminator.isEmpty)) Some(genJsoniterClassSerde(supertypes)(name))
          else None
        // For named maps, only generate the schema if it's a 'top level' json schema
        case (name, _: OpenapiSchemaMap) if jsonParamRefs.contains(name) =>
          Some(genJsoniterNamedSerde(name))
        // For enums, generate the serde if it's referenced in any json model
        case (name, _: OpenapiSchemaEnum) if allTransitiveJsonParamRefs.contains(name) =>
          Some(genJsoniterEnumSerde(name))
        // For ADTs, generate the serde if it's referenced in any json model
        case (name, schema: OpenapiSchemaOneOf) if allTransitiveJsonParamRefs.contains(name) =>
          Some(generateJsoniterAdtSerde(allSchemas, name, schema, fullModelPath, validateNonDiscriminatedOneOfs))
        case (_, _: OpenapiSchemaObject | _: OpenapiSchemaMap | _: OpenapiSchemaEnum | _: OpenapiSchemaOneOf) => None
        case (n, x) => throw new NotImplementedError(s"Only objects, enums, maps and oneOf supported! (for $n found ${x})")
      })
      .map(jsonSerdeHelpers + additionalExplicitSerdes + _.mkString("\n"))
  }

  private def genJsoniterClassSerde(supertypes: Seq[OpenapiSchemaOneOf])(name: String): String = {
    val uncapitalisedName = name.head.toLower +: name.tail
    if (supertypes.exists(_.discriminator.isDefined))
      throw new NotImplementedError(
        s"A class cannot be used both in a oneOf with discriminator and at the top level when using jsoniter serdes at $name"
      )
    else
      s"""implicit lazy val ${uncapitalisedName}JsonCodec: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[${name}] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make($jsoniterBaseConfig)"""
  }

  private def genJsoniterEnumSerde(name: String): String = {
    val uncapitalisedName = name.head.toLower +: name.tail
    s"""
       |implicit lazy val ${uncapitalisedName}JsonCodec: $jsoniterPkgCore.JsonValueCodec[${name}] = $jsoniterPkgMacros.JsonCodecMaker.make($jsoniteEnumConfig.withDiscriminatorFieldName(scala.None))""".stripMargin
  }

  private def genJsoniterNamedSerde(name: String): String = {
    val uncapitalisedName = name.head.toLower +: name.tail
    s"""
       |implicit lazy val ${uncapitalisedName}JsonCodec: $jsoniterPkgCore.JsonValueCodec[$name] = $jsoniterPkgMacros.JsonCodecMaker.make($jsoniterBaseConfig)""".stripMargin
  }

  private def generateJsoniterAdtSerde(
      allSchemas: Map[String, OpenapiSchemaType],
      name: String,
      schema: OpenapiSchemaOneOf,
      maybeFullModelPath: Option[String],
      validateNonDiscriminatedOneOfs: Boolean
  ): String = {
    val fullPathPrefix = maybeFullModelPath.map(_ + ".").getOrElse("")
    val uncapitalisedName = name.head.toLower +: name.tail
    schema match {
      case OpenapiSchemaOneOf(_, Some(discriminator)) =>
        def subtypeNames = schema.types.map {
          case ref: OpenapiSchemaRef => ref.stripped
          case other => throw new IllegalArgumentException(s"oneOf subtypes must be refs to explicit schema models, found $other for $name")
        }
        val schemaToJsonMapping = discriminator.mapping match {
          case Some(mapping) =>
            mapping.map { case (jsonValue, fullRef) => fullRef.stripPrefix("#/components/schemas/") -> jsonValue }
          case None => subtypeNames.map(s => s -> s).toMap
        }
        val body = if (schemaToJsonMapping.exists { case (className, jsonValue) => className != jsonValue }) {
          val discriminatorMap = indent(2)(
            schemaToJsonMapping
              .map { case (k, v) => s"""case "$fullPathPrefix$k" => "$v"""" }
              .mkString("\n", "\n", "\n")
          )
          val config =
            s"""$jsoniterBaseConfig.withRequireDiscriminatorFirst(false).withDiscriminatorFieldName(Some("${discriminator.propertyName}")).withAdtLeafClassNameMapper{$discriminatorMap}"""
          val serde =
            s"implicit lazy val ${uncapitalisedName}Codec: $jsoniterPkgCore.JsonValueCodec[$name] = $jsoniterPkgMacros.JsonCodecMaker.make($config)"

          s"""$serde
             |""".stripMargin
        } else {
          val config =
            s"""$jsoniterBaseConfig.withRequireDiscriminatorFirst(false).withDiscriminatorFieldName(Some("${discriminator.propertyName}"))"""
          s"implicit lazy val ${uncapitalisedName}Codec: $jsoniterPkgCore.JsonValueCodec[$name] = $jsoniterPkgMacros.JsonCodecMaker.make($config)"
        }
        body

      case OpenapiSchemaOneOf(schemas, None) =>
        if (validateNonDiscriminatedOneOfs) checkForSoundness(allSchemas)(schema.types.map(_.asInstanceOf[OpenapiSchemaRef]))
        val childNameAndSerde = schemas.collect { case ref: OpenapiSchemaRef =>
          val name = ref.stripped
          name -> s"${name.head.toLower +: name.tail}JsonCodec"
        }
        val childSerdes = childNameAndSerde.map(_._2)
        val doDecode = childSerdes.mkString("List(\n  ", ",\n  ", ")\n") +
          indent(2)(s""".foldLeft(Option.empty[$name]) {
          |  case (Some(v), _) => Some(v)
          |  case (None, next) =>
          |    in.setMark()
          |    scala.util.Try(next.asInstanceOf[$jsoniterPkgCore.JsonValueCodec[$name]].decodeValue(in, default))
          |      .fold(_ => { in.rollbackToMark(); None }, Some(_))
          |}.getOrElse(throw new RuntimeException("Unable to decode json to untagged ADT type ${name}"))""".stripMargin)
        val doEncode = childNameAndSerde.map { case (name, serdeName) => s"case x: $name => $serdeName.encodeValue(x, out)" }.mkString("\n")
        val serde =
          s"""implicit lazy val ${uncapitalisedName}Codec: $jsoniterPkgCore.JsonValueCodec[$name] = new $jsoniterPkgCore.JsonValueCodec[$name] {
             |  def decodeValue(in: $jsoniterPkgCore.JsonReader, default: $name): $name = {
             |${indent(4)(doDecode)}
             |  }
             |  def encodeValue(x: $name, out: $jsoniterPkgCore.JsonWriter): Unit = x match {
             |${indent(4)(doEncode)}
             |  }
             |
             |  def nullValue: $name = ${childSerdes.head}.nullValue
             |}""".stripMargin
        serde
    }
  }
}
