package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.{indent, mapSchemaSimpleTypeToType}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.{
  OpenapiSchemaType,
  ReifiableValueDouble,
  ReifiableValueList,
  ReifiableValueLong,
  ReifiableValueString,
  RenderableValue
}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._

import scala.annotation.tailrec

class ClassDefinitionGenerator {
  val jsoniterDefaultConfig =
    "com.github.plokhotnyuk.jsoniter_scala.macros.CodecMakerConfig.withAllowRecursiveTypes(true).withDiscriminatorFieldName(scala.None)"

  def classDefs(
      doc: OpenapiDocument,
      targetScala3: Boolean = false,
      queryParamRefs: Set[String] = Set.empty,
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib = JsonSerdeLib.Circe,
      jsonParamRefs: Set[String] = Set.empty
  ): Option[String] = {
    val allSchemas: Map[String, OpenapiSchemaType] = doc.components.toSeq.flatMap(_.schemas).toMap
    val generatesQueryParamEnums =
      allSchemas
        .collect { case (name, _: OpenapiSchemaEnum) => name }
        .exists(queryParamRefs.contains)

    def fetchJsonParamRefs(initialSet: Set[String], toCheck: Seq[OpenapiSchemaType]): Set[String] = toCheck match {
      case Nil          => initialSet
      case head +: tail => recursiveFindAllReferencedSchemaTypes(allSchemas)(head, initialSet, tail)
    }

    val allTransitiveJsonParamRefs = fetchJsonParamRefs(
      jsonParamRefs,
      jsonParamRefs.toSeq.flatMap(ref => allSchemas.get(ref.stripPrefix("#/components/schemas/")))
    )

    val maybeJsonSerdeHelpers =
      if (jsonParamRefs.nonEmpty && jsonSerdeLib == JsonSerdeLib.Jsoniter)
        s"""implicit def seqCodec[T: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec]: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[List[T]] =
        |  com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make[List[T]]($jsoniterDefaultConfig)
        |implicit def optionCodec[T: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec]: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[Option[T]] =
        |  com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make[Option[T]]($jsoniterDefaultConfig)
        |""".stripMargin
      else ""
    val enumQuerySerdeHelper = if (!generatesQueryParamEnums) "" else enumQuerySerdeHelperDefn(targetScala3)
    // For jsoniter-scala, we define explicit serdes for any 'primitive' params (e.g. List[java.util.UUID]) that we reference.
    // This should be the set of all json param refs not included in our schema definitions
    val additionalExplicitSerdes = jsonParamRefs.toSeq
      .filter(x => !allSchemas.contains(x))
      .map(s =>
        jsonSerdeLib match {
          case JsonSerdeLib.Jsoniter =>
            val name = s.replace("[", "_").replace("]", "_").replace(".", "_") + "JsonCodec"
            s"""implicit lazy val $name: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[$s] =
            |  com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make[$s]($jsoniterDefaultConfig)""".stripMargin
          case _ => ""
        }
      )
      .mkString("", "\n", "\n")
    val defns = doc.components
      .map(_.schemas.flatMap {
        case (name, obj: OpenapiSchemaObject) =>
          generateClass(allSchemas, name, obj, jsonSerdeLib, allTransitiveJsonParamRefs)
        case (name, obj: OpenapiSchemaEnum) =>
          generateEnum(name, obj, targetScala3, queryParamRefs, jsonSerdeLib, allTransitiveJsonParamRefs)
        case (name, OpenapiSchemaMap(valueSchema, _)) => generateMap(name, valueSchema, jsonSerdeLib, allTransitiveJsonParamRefs)
        case (n, x) => throw new NotImplementedError(s"Only objects, enums and maps supported! (for $n found ${x})")
      })
      .map(_.mkString("\n"))
    defns.map(additionalExplicitSerdes + maybeJsonSerdeHelpers + enumQuerySerdeHelper + _)
  }

  private def enumQuerySerdeHelperDefn(targetScala3: Boolean): String = if (targetScala3)
    """
      |def enumMap[E: enumextensions.EnumMirror]: Map[String, E] =
      |  Map.from(
      |    for e <- enumextensions.EnumMirror[E].values yield e.name.toUpperCase -> e
      |  )
      |
      |def makeQueryCodecForEnum[T: enumextensions.EnumMirror]: sttp.tapir.Codec[List[String], T, sttp.tapir.CodecFormat.TextPlain] =
      |  sttp.tapir.Codec
      |    .listHead[String, String, sttp.tapir.CodecFormat.TextPlain]
      |    .mapDecode(s =>
      |      // Case-insensitive mapping
      |      scala.util
      |        .Try(enumMap[T](using enumextensions.EnumMirror[T])(s.toUpperCase))
      |        .fold(
      |          _ =>
      |            sttp.tapir.DecodeResult.Error(
      |              s,
      |              new NoSuchElementException(
      |                s"Could not find value $s for enum ${enumextensions.EnumMirror[T].mirroredName}, available values: ${enumextensions.EnumMirror[T].values.mkString(", ")}"
      |              )
      |            ),
      |          sttp.tapir.DecodeResult.Value(_)
      |        )
      |    )(_.name)
      |""".stripMargin
  else
    """def makeQueryCodecForEnum[T <: enumeratum.EnumEntry](enumName: String, T: enumeratum.Enum[T]): sttp.tapir.Codec[List[String], T, sttp.tapir.CodecFormat.TextPlain] =
      |  sttp.tapir.Codec.listHead[String, String, sttp.tapir.CodecFormat.TextPlain]
      |    .mapDecode(s =>
      |      // Case-insensitive mapping
      |      scala.util.Try(T.upperCaseNameValuesToMap(s.toUpperCase))
      |        .fold(
      |          _ =>
      |            sttp.tapir.DecodeResult.Error(
      |              s,
      |              new NoSuchElementException(
      |                s"Could not find value $s for enum ${enumName}, available values: ${T.values.mkString(", ")}"
      |              )
      |            ),
      |          sttp.tapir.DecodeResult.Value(_)
      |        )
      |    )(_.entryName)
      |""".stripMargin

  @tailrec
  final def recursiveFindAllReferencedSchemaTypes(
      allSchemas: Map[String, OpenapiSchemaType]
  )(toCheck: OpenapiSchemaType, checked: Set[String], tail: Seq[OpenapiSchemaType]): Set[String] = {
    def nextParamsFromTypeSeq(types: Seq[OpenapiSchemaType]) = types match {
      case Nil          => None
      case next +: rest => Some((next, checked, rest ++ tail))
    }
    val maybeNextParams = toCheck match {
      case OpenapiSchemaRef(ref) if ref.startsWith("#/components/schemas/") =>
        val name = ref.stripPrefix("#/components/schemas/")
        val maybeAppended = if (checked contains name) None else allSchemas.get(name)
        (tail ++ maybeAppended) match {
          case Nil          => None
          case next +: rest => Some((next, checked + name, rest))
        }
      case OpenapiSchemaArray(items, _)                                => Some((items, checked, tail))
      case OpenapiSchemaNot(items)                                     => Some((items, checked, tail))
      case OpenapiSchemaMap(items, _)                                  => Some((items, checked, tail))
      case OpenapiSchemaOneOf(types)                                   => nextParamsFromTypeSeq(types)
      case OpenapiSchemaAnyOf(types)                                   => nextParamsFromTypeSeq(types)
      case OpenapiSchemaAllOf(types)                                   => nextParamsFromTypeSeq(types)
      case OpenapiSchemaObject(properties, _, _) if properties.isEmpty => None
      case OpenapiSchemaObject(properties, required, nullable) =>
        val propToCheck = properties.head
        val (propToCheckName, OpenapiSchemaField(propToCheckType, _)) = propToCheck
        val objectWithoutHeadField = OpenapiSchemaObject(properties - propToCheckName, required, nullable)
        Some((propToCheckType, checked, objectWithoutHeadField +: tail))
      case _ => None
    }
    maybeNextParams match {
      case None =>
        tail match {
          case Nil          => checked
          case next +: rest => recursiveFindAllReferencedSchemaTypes(allSchemas)(next, checked, rest)
        }
      case Some((next, checked, rest)) => recursiveFindAllReferencedSchemaTypes(allSchemas)(next, checked, rest)
    }
  }

  private[codegen] def generateMap(
      name: String,
      valueSchema: OpenapiSchemaType,
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      jsonParamRefs: Set[String]
  ): Seq[String] = {
    val valueSchemaName = valueSchema match {
      case simpleType: OpenapiSchemaSimpleType => BasicGenerator.mapSchemaSimpleTypeToType(simpleType)._1
      case otherType => throw new NotImplementedError(s"Only simple value types and refs are implemented for named maps (found $otherType)")
    }
    val uncapitalisedName = name.head.toLower +: name.tail
    val maybeJsonCodecDefn = jsonSerdeLib match {
      case _ if !jsonParamRefs.contains(name) => ""
      case JsonSerdeLib.Circe =>
        s"""
           |implicit lazy val ${uncapitalisedName}JsonDecoder: io.circe.Decoder[$name] = io.circe.Decoder.decodeMap[String, $valueSchemaName]
           |implicit lazy val ${uncapitalisedName}JsonEncoder: io.circe.Encoder[$name] = io.circe.Encoder.encodeMap[String, $valueSchemaName]""".stripMargin
      case JsonSerdeLib.Jsoniter =>
        s"""
           |implicit lazy val ${uncapitalisedName}JsonCodec: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[$name] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make($jsoniterDefaultConfig)""".stripMargin
    }
    Seq(s"""type $name = Map[String, $valueSchemaName]$maybeJsonCodecDefn""")
  }

  // Uses enumeratum for scala 2, but generates scala 3 enums instead where it can
  private[codegen] def generateEnum(
      name: String,
      obj: OpenapiSchemaEnum,
      targetScala3: Boolean,
      queryParamRefs: Set[String],
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      jsonParamRefs: Set[String]
  ): Seq[String] = if (targetScala3) {
    val maybeCompanion =
      if (queryParamRefs contains name)
        s"""
        |object $name {
        |  given stringList${name}Codec: sttp.tapir.Codec[List[String], $name, sttp.tapir.CodecFormat.TextPlain] =
        |    makeQueryCodecForEnum[$name]
        |}""".stripMargin
      else ""
    val maybeCodecExtensions = jsonSerdeLib match {
      case _ if !jsonParamRefs.contains(name) && !queryParamRefs.contains(name) => ""
      case _ if !jsonParamRefs.contains(name)                                   => " derives enumextensions.EnumMirror"
      case JsonSerdeLib.Circe if !queryParamRefs.contains(name) => " derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec"
      case JsonSerdeLib.Circe => " derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec, enumextensions.EnumMirror"
      case JsonSerdeLib.Jsoniter if !queryParamRefs.contains(name) => s" extends java.lang.Enum[$name]"
      case JsonSerdeLib.Jsoniter                                   => s" extends java.lang.Enum[$name] derives enumextensions.EnumMirror"
    }
    s"""$maybeCompanion
       |enum $name$maybeCodecExtensions {
       |  case ${obj.items.map(_.value).mkString(", ")}
       |}""".stripMargin :: Nil
  } else {
    val uncapitalisedName = name.head.toLower +: name.tail
    val members = obj.items.map { i => s"case object ${i.value} extends $name" }
    val maybeJsonCodecDefn = jsonSerdeLib match {
      case _ if !jsonParamRefs.contains(name) => ""
      case JsonSerdeLib.Circe                 => ""
      case JsonSerdeLib.Jsoniter =>
        s"""
           |  implicit lazy val ${uncapitalisedName}JsonCodec: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[${name}] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make($jsoniterDefaultConfig)""".stripMargin
    }
    val maybeCodecExtension = jsonSerdeLib match {
      case _ if !jsonParamRefs.contains(name) && !queryParamRefs.contains(name) => ""
      case JsonSerdeLib.Circe                                                   => s" with enumeratum.CirceEnum[$name]"
      case JsonSerdeLib.Jsoniter                                                => ""
    }
    val maybeQueryCodecDefn =
      if (queryParamRefs contains name)
        s"""
       |  implicit val ${uncapitalisedName}QueryCodec: sttp.tapir.Codec[List[String], ${name}, sttp.tapir.CodecFormat.TextPlain] =
       |    makeQueryCodecForEnum("${name}", ${name})""".stripMargin
      else ""
    s"""|sealed trait $name extends enumeratum.EnumEntry
        |object $name extends enumeratum.Enum[$name]$maybeCodecExtension {
        |  val values = findValues$maybeJsonCodecDefn
        |${indent(2)(members.mkString("\n"))}$maybeQueryCodecDefn
        |}""".stripMargin :: Nil
  }

  private[codegen] def generateClass(
      allSchemas: Map[String, OpenapiSchemaType],
      name: String,
      obj: OpenapiSchemaObject,
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      jsonParamRefs: Set[String]
  ): Seq[String] = {
    val isJson = jsonParamRefs contains name
    def rec(name: String, obj: OpenapiSchemaObject, acc: List[String]): Seq[String] = {
      val innerClasses = obj.properties
        .collect {
          case (propName, OpenapiSchemaField(st: OpenapiSchemaObject, _)) =>
            val newName = addName(name, propName)
            rec(newName, st, Nil)

          case (propName, OpenapiSchemaField(OpenapiSchemaMap(st: OpenapiSchemaObject, _), _)) =>
            val newName = addName(addName(name, propName), "item")
            rec(newName, st, Nil)

          case (propName, OpenapiSchemaField(OpenapiSchemaArray(st: OpenapiSchemaObject, _), _)) =>
            val newName = addName(addName(name, propName), "item")
            rec(newName, st, Nil)
        }
        .flatten
        .toList

      val properties = obj.properties.map { case (key, OpenapiSchemaField(schemaType, maybeDefault)) =>
        val tpe = mapSchemaTypeToType(name, key, obj.required.contains(key), schemaType, isJson)
        val fixedKey = fixKey(key)
        val default = maybeDefault.map(" = " + renderDefault(allSchemas, obj.required.contains(key), _, schemaType)) getOrElse ""
        s"$fixedKey: $tpe$default"
      }

      val uncapitalisedName = name.head.toLower +: name.tail
      def jsonCodec = jsonSerdeLib match {
        case JsonSerdeLib.Circe =>
          s"""implicit lazy val ${uncapitalisedName}JsonDecoder: io.circe.Decoder[$name] = io.circe.generic.semiauto.deriveDecoder[$name]
             |implicit lazy val ${uncapitalisedName}JsonEncoder: io.circe.Encoder[$name] = io.circe.generic.semiauto.deriveEncoder[$name]""".stripMargin
        case JsonSerdeLib.Jsoniter =>
          s"""implicit lazy val ${uncapitalisedName}JsonCodec: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[${name}] = com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make($jsoniterDefaultConfig)"""
      }
      val maybeCompanion =
        if (isJson)
          s"""
          |object $name {
          |${indent(2)(jsonCodec)}
          |}"""
        else ""

      s"""|$maybeCompanion
          |case class $name (
          |${indent(2)(properties.mkString(",\n"))}
          |)""".stripMargin :: innerClasses ::: acc
    }

    rec(addName("", name), obj, Nil)
  }

  private def mapSchemaTypeToType(
      parentName: String,
      key: String,
      required: Boolean,
      schemaType: OpenapiSchemaType,
      isJson: Boolean
  ): String = {
    val (tpe, optional) = schemaType match {
      case simpleType: OpenapiSchemaSimpleType =>
        mapSchemaSimpleTypeToType(simpleType, multipartForm = !isJson)

      case objectType: OpenapiSchemaObject =>
        addName(parentName, key) -> objectType.nullable

      case mapType: OpenapiSchemaMap =>
        val innerType = mapSchemaTypeToType(addName(parentName, key), "item", required = true, mapType.items, isJson = isJson)
        s"Map[String, $innerType]" -> mapType.nullable

      case arrayType: OpenapiSchemaArray =>
        val innerType = mapSchemaTypeToType(addName(parentName, key), "item", required = true, arrayType.items, isJson = isJson)
        s"Seq[$innerType]" -> arrayType.nullable

      case _ =>
        throw new NotImplementedError(s"We can't serialize some of the properties yet! $parentName $key $schemaType")
    }

    if (optional || !required) s"Option[$tpe]" else tpe
  }

  private def renderDefault(
      allSchemas: Map[String, OpenapiSchemaType],
      required: Boolean,
      default: RenderableValue,
      schemaType: OpenapiSchemaType
  ): String = {
    default.render(allModels = allSchemas, thisType = schemaType, schemaType.nullable || !required)
  }

  private def addName(parentName: String, key: String) = parentName + key.replace('_', ' ').replace('-', ' ').capitalize.replace(" ", "")

  private val reservedKeys = scala.reflect.runtime.universe.asInstanceOf[scala.reflect.internal.SymbolTable].nme.keywords.map(_.toString)

  private def fixKey(key: String) = {
    if (reservedKeys.contains(key))
      s"`$key`"
    else
      key
  }
}
