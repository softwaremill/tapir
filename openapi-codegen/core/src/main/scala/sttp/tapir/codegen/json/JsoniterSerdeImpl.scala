package sttp.tapir.codegen.json

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaAllOf,
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
import sttp.tapir.codegen.util.NameHelpers.{addName, indent, uncapitalise}

object JsoniterSerdeImpl {

  private val jsoniterPkgRoot = "com.github.plokhotnyuk.jsoniter_scala"
  private val jsoniterPkgCore = s"$jsoniterPkgRoot.core"
  private val jsoniterPkgMacros = s"$jsoniterPkgRoot.macros"
  // By default:
  // - permit recursive schema definitions
  // - force serialization of empty collections if 'required' (non-required T will be typed as 'Option[T]' to which this will not apply)
  // - force serialization of default values
  // - require presence of collections when decoding if 'required'
  private val jsoniterBaseConfig =
    s"$jsoniterPkgMacros.CodecMakerConfig.withAllowRecursiveTypes(true).withTransientEmpty(false).withTransientDefault(false).withRequireCollectionFields(true)"
  private val jsoniterEnumConfig = s"$jsoniterBaseConfig.withDiscriminatorFieldName(scala.None)"
  private[json] def genJsoniterSerdes(
      doc: OpenapiDocument,
      allSchemas: Map[String, OpenapiSchemaType],
      jsonParamRefs: Set[String],
      allTransitiveJsonParamRefs: Set[String],
      adtInheritanceMap: Map[String, Seq[String]],
      validateNonDiscriminatedOneOfs: Boolean,
      schemasContainAny: Boolean,
      useCustomJsoniterSerdes: Boolean,
      packageReuse: PackageReuseContext,
      resolvableNonClassyOneOfSchemas: Seq[(String, OpenapiSchemaOneOf)]
  ): SerdeGenResponse = {
    // if schemas contain an 'any' (i.e. any json), we assume jsoniter-scala-circe is a dependency
    val maybeAnySerde =
      if (schemasContainAny)
        Some(
          """implicit lazy val anyJsonSupport: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[io.circe.Json] = com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.jsonCodec()
            |implicit lazy val anyObjJsonSupport: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[io.circe.JsonObject] = new com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[io.circe.JsonObject] {
            |  import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
            |  override def decodeValue(in: JsonReader, default: io.circe.JsonObject): io.circe.JsonObject = anyJsonSupport.decodeValue(in, io.circe.Json.fromJsonObject(default)).asObject.getOrElse(throw new RuntimeException(s"${default.getClass.getSimpleName} is not an object"))
            |  override def encodeValue(x: io.circe.JsonObject, out: JsonWriter): Unit = anyJsonSupport.encodeValue(io.circe.Json.fromJsonObject(x), out)
            |  override def nullValue: io.circe.JsonObject = null
            |}""".stripMargin
        )
      else None

    val explicitNonObjTypes = jsonParamRefs.toSeq
      .filter(x => !allSchemas.contains(x) && x != "io.circe.Json")

    lazy val inheritedImpl = s"${packageReuse.dependencyModelPath}JsonSerdes"
    val meta = packageReuse.dependencyMeta
    def hasExistingDefn(name: String, enumLike: Boolean = false, skipCheck: Boolean = false) =
      PackageReuseContext.isReusedSchema(name, packageReuse) &&
        (skipCheck || (enumLike && meta.allTransitiveJsonParamRefs.contains(name)) || meta.jsonParamRefs.contains(name))
    // For jsoniter-scala, we define explicit serdes for any 'primitive' params (e.g. List[java.util.UUID]) that we reference.
    // This should be the set of all json param refs not included in our schema definitions
    val additionalExplicitSerdes = (explicitNonObjTypes.map { s =>
      val name = s.replace(" ", "").replace(",", "_").replace("[", "_").replace("]", "_").replace(".", "_") + "JsonCodec"
      if (meta.explicitNonObjTypes.contains(s) && meta.jsonParamRefs.contains(s))
        s"implicit lazy val $name: $jsoniterPkgCore.JsonValueCodec[$s] = $inheritedImpl.$name"
      else
        s"""implicit lazy val $name: $jsoniterPkgCore.JsonValueCodec[$s] =
           |  $jsoniterPkgMacros.JsonCodecMaker.make[$s]""".stripMargin
    }.sorted ++ maybeAnySerde).mkString("", "\n", "\n")

    // Permits usage of Option/Seq wrapped classes at top level without having to be explicit
    val jsonSerdeHelpers =
      if (packageReuse.reusedSchemas.nonEmpty && meta.jsonParamRefs.nonEmpty)
        s"""
           |implicit def seqCodec[T: $jsoniterPkgCore.JsonValueCodec]: $jsoniterPkgCore.JsonValueCodec[List[T]] = $inheritedImpl.seqCodec[T]
           |implicit def optionCodec[T: $jsoniterPkgCore.JsonValueCodec]: $jsoniterPkgCore.JsonValueCodec[Option[T]] = $inheritedImpl.optionCodec[T]
           |""".stripMargin
      else
        s"""
           |implicit def seqCodec[T: $jsoniterPkgCore.JsonValueCodec]: $jsoniterPkgCore.JsonValueCodec[List[T]] =
           |  $jsoniterPkgMacros.JsonCodecMaker.make[List[T]]
           |implicit def optionCodec[T: $jsoniterPkgCore.JsonValueCodec]: $jsoniterPkgCore.JsonValueCodec[Option[T]] =
           |  $jsoniterPkgMacros.JsonCodecMaker.make[Option[T]]
           |""".stripMargin
    val docSchemas = doc.components.toSeq.flatMap(_.schemas)
    val pathSchemas = JsonHelpers.inlineEndpointSchemas(doc)
    def jsoniterParentImpl(name: String, enumLike: Boolean = false, skipCheck: Boolean = false): Option[Seq[String]] =
      if (hasExistingDefn(name, enumLike, skipCheck)) {
        val codecName = getJsoniterName(name)
        Some(Seq(s"implicit lazy val $codecName: $jsoniterPkgCore.JsonValueCodec[$name] = $inheritedImpl.$codecName"))
      } else None

    val wrappedOneOfNames = resolvableNonClassyOneOfSchemas
      .filter { case (name, _) => allTransitiveJsonParamRefs.contains(name) }
      .flatMap(_._2.types.collect { case o: OpenapiSchemaRef => o.stripped })
      .toSet

    def getSerdeString(name: String, t: OpenapiSchemaType, isJson: Boolean): Seq[String] = (name, t, isJson) match {
      // For standard objects, generate the schema if it's a 'top level' json schema or if it's referenced as a subtype of an ADT without a discriminator
      case (name, o: OpenapiSchemaObject, isJson) =>
        // Inline enums (defined on a property rather than via $ref) need an explicitly emitted codec: jsoniter would
        // otherwise derive them as discriminated ADTs and reject their bare-string values. We descend through nested
        // inline objects/arrays/maps to reach enums at any depth, naming each to match the class ClassDefinitionGenerator
        // generates for it.
        val inlinedEnumDefns =
          if (allTransitiveJsonParamRefs.contains(name) && !hasExistingDefn(name, enumLike = true))
            collectInlineEnumNames(addName("", name), o).distinct.map(genJsoniterEnumSerde(useCustomJsoniterSerdes, _))
          else Nil
        val supertypes =
          adtInheritanceMap.getOrElse(name, Nil).map(allSchemas.apply).collect { case oneOf: OpenapiSchemaOneOf => oneOf }
        val topLevelDefn = {
          val forceDefn = isJson || supertypes.exists(_.discriminator.isEmpty) || wrappedOneOfNames.contains(name)
          if (jsonParamRefs.contains(name) || forceDefn) {
            jsoniterParentImpl(name, skipCheck = forceDefn)
              .getOrElse(Seq(genJsoniterClassSerde(useCustomJsoniterSerdes, supertypes)(name)))
          } else Nil
        }
        topLevelDefn ++ inlinedEnumDefns
      // For named maps and arrays, only generate the schema if it's a 'top level' json schema
      case (name, _: OpenapiSchemaMap, isJson) if jsonParamRefs.contains(name) || isJson =>
        jsoniterParentImpl(name, skipCheck = isJson) getOrElse Seq(genJsoniterNamedSerde(useCustomJsoniterSerdes, name))
      case (name, _: OpenapiSchemaArray, isJson) if jsonParamRefs.contains(name) || isJson =>
        jsoniterParentImpl(name, skipCheck = isJson) getOrElse Seq(genJsoniterNamedSerde(useCustomJsoniterSerdes, name))
      // For enums, generate the serde if it's referenced in any json model
      case (name, _: OpenapiSchemaEnum, _) if allTransitiveJsonParamRefs.contains(name) =>
        jsoniterParentImpl(name, enumLike = true) getOrElse Seq(genJsoniterEnumSerde(useCustomJsoniterSerdes, name))
      // For ADTs, generate the serde if it's referenced in any json model
      case (name, schema: OpenapiSchemaOneOf, _) if allTransitiveJsonParamRefs.contains(name) =>
        jsoniterParentImpl(name, enumLike = true) getOrElse Seq(
          if (schema.types.exists(!_.isInstanceOf[OpenapiSchemaRef]))
            generateJsoniterWrappedOneOfSerde(name, schema)
          else
            generateJsoniterAdtSerde(allSchemas, name, schema, validateNonDiscriminatedOneOfs, useCustomJsoniterSerdes)
        )
      case (
            _,
            _: OpenapiSchemaObject | _: OpenapiSchemaMap | _: OpenapiSchemaArray | _: OpenapiSchemaEnum | _: OpenapiSchemaOneOf |
            _: OpenapiSchemaAny,
            _
          ) =>
        Nil
      case (_, t: OpenapiSchemaSimpleType, _) if !t.isInstanceOf[OpenapiSchemaRef] => Nil
      case (n, x, _) => throw new NotImplementedError(s"Only objects, enums, maps, arrays and oneOf supported! (for $n found ${x})")
    }

    val maybeCustomByteStringSerde =
      if (packageReuse.reusedSchemas.nonEmpty && meta.jsonParamRefs.nonEmpty)
        s"implicit val byteStringJsonCodec: $jsoniterPkgCore.JsonValueCodec[ByteString] = $inheritedImpl.byteStringJsonCodec\n"
      else
        s"""implicit val byteStringJsonCodec: $jsoniterPkgCore.JsonValueCodec[ByteString] = new $jsoniterPkgCore.JsonValueCodec[ByteString] {
           |  def nullValue: ByteString = Array.empty[Byte]
           |  def decodeValue(in: $jsoniterPkgCore.JsonReader, default: ByteString): ByteString =
           |    toByteString(java.util.Base64.getDecoder.decode(in.readString("")))
           |  def encodeValue(x: ByteString, out: $jsoniterPkgCore.JsonWriter): _root_.scala.Unit =
           |    out.writeVal(java.util.Base64.getEncoder.encodeToString(x))
           |}
           |""".stripMargin

    val serdesDefn = (docSchemas.map { case (n, t) => (n, t, false) } ++ pathSchemas)
      .flatMap { (getSerdeString _).tupled }
      // an inline enum can be reached by multiple paths (or also be emitted at top level); duplicate decls won't compile
      .distinct
      .sorted
      .foldLeft(Option.empty[String]) {
        case (Some(a), b) => Some(a + "\n" + b)
        case (None, a)    => Some(a)
      }
      .map(jsonSerdeHelpers + additionalExplicitSerdes + _)
      .map(s => s"$maybeCustomByteStringSerde$s")
    SerdeGenResponse(serdesDefn, explicitNonObjTypes)
  }

  private def getJsoniterName(name: String): String = {
    val uncapitalisedName = uncapitalise(name)
    s"${uncapitalisedName}JsonCodec"
  }

  private def genJsoniterClassSerde(useCustomJsoniterSerdes: Boolean, supertypes: Seq[OpenapiSchemaOneOf])(
      name: String
  ): String = {
    val codecName = getJsoniterName(name)
    if (supertypes.exists(_.discriminator.isDefined))
      throw new NotImplementedError(
        s"A class cannot be used both in a oneOf with discriminator and at the top level when using jsoniter serdes at $name"
      )
    else {
      if (useCustomJsoniterSerdes)
        s"""implicit lazy val $codecName: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[${name}] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.makeOpenapiLike"""
      else
        s"""implicit lazy val $codecName: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[${name}] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make($jsoniterBaseConfig)"""
    }
  }

  // Collects the class names of all inline enums reachable from the given object, descending through nested inline
  // objects and the element schemas of arrays/maps. Nested inline objects need no codec of their own (jsoniter derives
  // them as part of the parent); only inline enums require an explicitly emitted codec, so we gather just their names.
  // Naming mirrors ClassDefinitionGenerator (via the shared addName) so codec types match the classes.
  private def collectInlineEnumNames(objName: String, obj: OpenapiSchemaObject): Seq[String] =
    obj.properties.toSeq.flatMap { case (key, OpenapiSchemaField(tpe, _, _)) => inlineEnumNamesForType(objName, key, tpe) }

  private def inlineEnumNamesForType(parentName: String, key: String, schemaType: OpenapiSchemaType): Seq[String] =
    schemaType match {
      case OpenapiSchemaAllOf(Seq(single))    => inlineEnumNamesForType(parentName, key, single)
      case _: OpenapiSchemaEnum               => Seq(addName(parentName.capitalize, key))
      case o: OpenapiSchemaObject             => collectInlineEnumNames(addName(parentName, key), o)
      case OpenapiSchemaArray(items, _, _, _) => inlineEnumNamesForType(addName(parentName, key), "item", items)
      case OpenapiSchemaMap(items, _, _)      => inlineEnumNamesForType(addName(parentName, key), "item", items)
      case _                                  => Nil
    }

  private def genJsoniterEnumSerde(useCustomJsoniterSerdes: Boolean, name: String): String = {
    val codecName = getJsoniterName(name)
    if (useCustomJsoniterSerdes)
      s"""
         |implicit lazy val $codecName: $jsoniterPkgCore.JsonValueCodec[$name] = $jsoniterPkgMacros.JsonCodecMaker.makeOpenapiLikeWithoutDiscriminator""".stripMargin
    else s"""
            |implicit lazy val $codecName: $jsoniterPkgCore.JsonValueCodec[${name}] = $jsoniterPkgMacros.JsonCodecMaker.make($jsoniterEnumConfig)""".stripMargin
  }

  private def genJsoniterNamedSerde(useCustomJsoniterSerdes: Boolean, name: String): String = {
    val codecName = getJsoniterName(name)
    if (useCustomJsoniterSerdes)
      s"""
         |implicit lazy val $codecName: $jsoniterPkgCore.JsonValueCodec[$name] = $jsoniterPkgMacros.JsonCodecMaker.makeOpenapiLike""".stripMargin
    else s"""
            |implicit lazy val $codecName: $jsoniterPkgCore.JsonValueCodec[$name] = $jsoniterPkgMacros.JsonCodecMaker.make($jsoniterBaseConfig)""".stripMargin
  }

  private def generateJsoniterAdtSerde(
      allSchemas: Map[String, OpenapiSchemaType],
      name: String,
      schema: OpenapiSchemaOneOf,
      validateNonDiscriminatedOneOfs: Boolean,
      useCustomJsoniterSerdes: Boolean
  ): String = {
    val codecName = getJsoniterName(name)
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
              .map { case (k, v) => s"""case "$k" => "$v"""" }
              .mkString("\n", "\n", "\n")
          )
          val codecName = getJsoniterName(name)
          val serde =
            if (useCustomJsoniterSerdes)
              s"""implicit lazy val $codecName: $jsoniterPkgCore.JsonValueCodec[$name] = $jsoniterPkgMacros.JsonCodecMaker.makeOpenapiLike("${discriminator.propertyName}", {$discriminatorMap})"""
            else {
              val config =
                s"""$jsoniterBaseConfig.withRequireDiscriminatorFirst(false).withDiscriminatorFieldName(Some("${discriminator.propertyName}")).withAdtLeafClassNameMapper(x => com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.simpleClassName(x) match {$discriminatorMap})"""
              s"implicit lazy val $codecName: $jsoniterPkgCore.JsonValueCodec[$name] = $jsoniterPkgMacros.JsonCodecMaker.make($config)"
            }

          s"""$serde
             |""".stripMargin
        } else {
          if (useCustomJsoniterSerdes)
            s"""implicit lazy val $codecName: $jsoniterPkgCore.JsonValueCodec[$name] = $jsoniterPkgMacros.JsonCodecMaker.makeOpenapiLike("${discriminator.propertyName}")"""
          else {
            val config =
              s"""$jsoniterBaseConfig.withRequireDiscriminatorFirst(false).withDiscriminatorFieldName(Some("${discriminator.propertyName}"))"""
            s"implicit lazy val $codecName: $jsoniterPkgCore.JsonValueCodec[$name] = $jsoniterPkgMacros.JsonCodecMaker.make($config)"
          }
        }
        body

      case OpenapiSchemaOneOf(schemas, None) =>
        if (validateNonDiscriminatedOneOfs) JsonHelpers.checkForSoundness(name, allSchemas)(schema.types)
        val childNameAndSerde = schemas.collect { case ref: OpenapiSchemaRef =>
          val name = ref.stripped
          name -> s"${uncapitalise(name)}JsonCodec"
        }
        val childSerdes = childNameAndSerde.map(_._2)
        val doDecode = childSerdes.mkString("List(\n  ", ",\n  ", ")\n") +
          indent(2)(s""".foldLeft(Option.empty[$name]) {
                       |  case (Some(v), _) => Some(v)
                       |  case (None, next) =>
                       |    in.setMark()
                       |    scala.util.Try(next.asInstanceOf[$jsoniterPkgCore.JsonValueCodec[$name]].decodeValue(in, default))
                       |      .fold(_ => { in.rollbackToMark(); None }, succ => Some(succ))
                       |}.getOrElse(throw new RuntimeException("Unable to decode json to untagged ADT type ${name}"))""".stripMargin)
        val doEncode = childNameAndSerde.map { case (name, serdeName) => s"case x: $name => $serdeName.encodeValue(x, out)" }.mkString("\n")
        val serde =
          s"""implicit lazy val $codecName: $jsoniterPkgCore.JsonValueCodec[$name] = new $jsoniterPkgCore.JsonValueCodec[$name] {
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

  private def generateJsoniterWrappedOneOfSerde(
      name: String,
      schema: OpenapiSchemaOneOf
  ): String = {
    val codecName = getJsoniterName(name)
    val (refSchemas, otherSchemas) = schema.types.partition(_.isInstanceOf[OpenapiSchemaRef])
    val maybeBoolSchema = otherSchemas.collectFirst { case b: OpenapiSchemaBoolean => b }
    val maybeNumericSchema = otherSchemas.collectFirst { case n: OpenapiSchemaNumericType => n }
    val maybeAnySchema = otherSchemas.collectFirst { case a: OpenapiSchemaAny => a }
    val stringSchemas = otherSchemas.collect { case a: OpenapiSchemaStringType => a }

    val childNameAndSerde = refSchemas.map { case ref: OpenapiSchemaRef =>
      val name = ref.stripped
      name -> s"${uncapitalise(name)}JsonCodec"
    }
    val doBoolDecode = maybeBoolSchema.map { _ =>
      s"""() => {
         |  in.setMark()
         |  scala.util.Try(in.readBoolean()).fold(_ => { in.rollbackToMark(); None }, succ => Some(${name}Boolean(succ)))
         |}""".stripMargin
    }.toSeq
    val doNumericDecode = maybeNumericSchema.map { s =>
      s"""() => {
         |  in.setMark()
         |  scala.util.Try(in.read${s.scalaType}()).fold(_ => { in.rollbackToMark(); None }, succ => Some($name${s.scalaType}(succ)))
         |}""".stripMargin
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
            if (i == 0) s"Some(scala.util.Try($parseImpl).map($wrapper(_))" else s".orElse(scala.util.Try($parseImpl).map($wrapper(_)))"
          }
          .mkString("\n          ")
        val decode =
          s"""() => {
             |  in.setMark()
             |  scala.util.Try(in.readString(null))
             |    .fold(
             |      _ => { in.rollbackToMark(); None },
             |      succ => {
             |        $tries
             |          .getOrElse(throw new RuntimeException("unable to parse string to acceptable type")))
             |      })
             |}""".stripMargin
        Some(decode)
      }

    val doRefDecodes = childNameAndSerde
      .map { case (typeName, serdeName) =>
        s"""
           |() => {
           |  in.setMark()
           |  scala.util.Try($serdeName.decodeValue(in, null.asInstanceOf[$typeName]))
           |    .fold(_ => { in.rollbackToMark(); None }, succ => Some($name${typeName.capitalize}(succ)))
           |}""".stripMargin
      }
    val doAnyDecode = maybeAnySchema.map { _ => s"() => Some(${name}Json(anyJsonSupport.decodeValue(in, io.circe.Json.Null)))" }
    val doDecode = (doBoolDecode ++ doNumericDecode ++ doStringDecodes ++ doRefDecodes ++ doAnyDecode).mkString(",\n")
    val doEncode =
      (maybeBoolSchema.map { s => s"case x: ${name}Boolean => out.writeVal(x.v)" }.toSeq ++
        maybeNumericSchema.map { s => s"case x: $name${s.scalaType} => out.writeVal(x.v)" }.toSeq ++
        maybeAnySchema.map { s => s"case x: ${name}Json => anyJsonSupport.encodeValue(x.v, out)" }.toSeq ++
        stringSchemas.map {
          case s: OpenapiSchemaString => s"case x: ${name}String => out.writeVal(x.v)"
          case s                      =>
            s"case x: ${name}${s.disambiguationSuffix} => out.writeVal(x.v.toString)"
        } ++
        childNameAndSerde.map { case (subName, serdeName) => s"case x: $name${subName} => $serdeName.encodeValue(x.v, out)" }).mkString("\n")
    val serde =
      s"""implicit lazy val $codecName: $jsoniterPkgCore.JsonValueCodec[$name] = new $jsoniterPkgCore.JsonValueCodec[$name] {
         |    def decodeValue(in: $jsoniterPkgCore.JsonReader, default: $name): $name = {
         |      List[() => Option[$name]](
         |${indent(8)(doDecode)})
         |        .foldLeft(Option.empty[$name]) {
         |          case (Some(v), _) => Some(v)
         |          case (None, next) => next()
         |        }.getOrElse(throw new RuntimeException("Unable to decode json to wrapped oneOf type $name"))
         |    }
         |    def encodeValue(x: $name, out: $jsoniterPkgCore.JsonWriter): Unit = x match {
         |${indent(6)(doEncode)}
         |    }
         |
         |    def nullValue: $name = null.asInstanceOf[$name]
         |  }""".stripMargin
    serde
  }

}
