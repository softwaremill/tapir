package sttp.tapir.codegen

import sttp.tapir.codegen.RootGenerator.{indent, mapSchemaSimpleTypeToType}
import sttp.tapir.codegen.JsonSerdeLib.{Circe, Jsoniter}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.{DefaultValueRenderer, OpenapiSchemaType, RenderConfig}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._
import sttp.tapir.codegen.util.DocUtils

case class GeneratedClassDefinitions(
    classRepr: String,
    jsonSerdeRepr: Option[String],
    schemaRepr: Seq[String],
    xmlSerdeRepr: Option[String]
)

case class InlineEnumDefn(enumName: String, impl: String)

class ClassDefinitionGenerator {

  def classDefs(
      doc: OpenapiDocument,
      targetScala3: Boolean = false,
      queryOrPathParamRefs: Set[String] = Set.empty,
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib = Circe,
      xmlSerdeLib: XmlSerdeLib.XmlSerdeLib = XmlSerdeLib.CatsXml,
      jsonParamRefs: Set[String] = Set.empty,
      fullModelPath: String = "",
      validateNonDiscriminatedOneOfs: Boolean = true,
      maxSchemasPerFile: Int = 400,
      enumsDefinedOnEndpointParams: Boolean = false,
      xmlParamRefs: Set[String] = Set.empty,
      useCustomJsoniterSerdes: Boolean = true
  ): Option[GeneratedClassDefinitions] = {
    val allSchemas: Map[String, OpenapiSchemaType] = doc.components.toSeq.flatMap(_.schemas).toMap
    val allOneOfSchemas = allSchemas.collect { case (name, oneOf: OpenapiSchemaOneOf) => name -> oneOf }.toSeq
    val adtInheritanceMap: Map[String, Seq[(String, OpenapiSchemaOneOf)]] = mkMapParentsByChild(allOneOfSchemas)
    val generatesQueryOrPathParamEnums = enumsDefinedOnEndpointParams ||
      allSchemas
        .collect { case (name, _: OpenapiSchemaEnum) => name }
        .exists(queryOrPathParamRefs.contains)

    def fetchTransitiveParamRefs(initialSet: Set[String], toCheck: Seq[OpenapiSchemaType]): Set[String] = toCheck match {
      case Nil          => initialSet
      case head +: tail => DocUtils.recursiveFindAllReferencedSchemaTypes(allSchemas)(head, initialSet, tail)
    }

    val allTransitiveJsonParamRefs = fetchTransitiveParamRefs(
      jsonParamRefs,
      jsonParamRefs.toSeq.flatMap(ref => allSchemas.get(ref.stripPrefix("#/components/schemas/")))
    )

    val adtTypes = adtInheritanceMap.flatMap(_._2).toSeq.map(_._1).distinct.map(name => s"sealed trait $name").mkString("", "\n", "\n")
    val enumSerdeHelper = if (!generatesQueryOrPathParamEnums) "" else enumSerdeHelperDefn(targetScala3)
    val schemasWithAny = allSchemas.filter { case (_, schema) => schemaContainsAny(schema) }
    val schemasContainAny = schemasWithAny.nonEmpty || allTransitiveJsonParamRefs.contains("io.circe.Json")
    if (schemasContainAny && !Set(Circe, Jsoniter).contains(jsonSerdeLib))
      throw new NotImplementedError(
        s"any not implemented for json libs other than circe and jsoniter (problematic models: ${schemasWithAny.keys})"
      )
    val schemas = SchemaGenerator
      .generateSchemas(doc, allSchemas, fullModelPath, jsonSerdeLib, maxSchemasPerFile, schemasContainAny, targetScala3)
    val jsonSerdes = JsonSerdeGenerator.serdeDefs(
      doc,
      jsonSerdeLib,
      jsonParamRefs,
      allTransitiveJsonParamRefs,
      validateNonDiscriminatedOneOfs,
      adtInheritanceMap.mapValues(_.map(_._1)),
      targetScala3,
      schemasContainAny,
      useCustomJsoniterSerdes
    )
    val allTransitiveXmlParamRefs = fetchTransitiveParamRefs(
      xmlParamRefs,
      xmlParamRefs.toSeq.flatMap(ref => allSchemas.get(ref.stripPrefix("#/components/schemas/")))
    )
    val xmlSerdes = XmlSerdeGenerator.generateSerdes(xmlSerdeLib, doc, allTransitiveXmlParamRefs, targetScala3)
    val defns = doc.components
      .map(_.schemas.flatMap {
        case (name, obj: OpenapiSchemaObject) =>
          generateClass(allSchemas, name, obj, allTransitiveJsonParamRefs, adtInheritanceMap, jsonSerdeLib, targetScala3)
        case (name, obj: OpenapiSchemaEnum) =>
          EnumGenerator.generateEnum(name, obj, targetScala3, queryOrPathParamRefs, jsonSerdeLib, allTransitiveJsonParamRefs)
        case (name, OpenapiSchemaMap(valueSchema, _, _))       => generateMap(name, valueSchema)
        case (name, OpenapiSchemaArray(valueSchema, _, _, rs)) => generateArray(name, valueSchema, rs)
        case (_, _: OpenapiSchemaOneOf)                        => Nil
        case (n, x) => throw new NotImplementedError(s"Only objects, enums and maps supported! (for $n found ${x})")
      })
      .map(_.mkString("\n"))
    val helpers = (enumSerdeHelper + adtTypes).linesIterator
      .filterNot(_.forall(_.isWhitespace))
      .mkString("\n")
    // Json serdes & schemas live in separate files from the class defns
    defns.map(helpers + "\n" + _).map(defStr => GeneratedClassDefinitions(defStr, jsonSerdes, schemas, xmlSerdes))
  }

  private def mkMapParentsByChild(allOneOfSchemas: Seq[(String, OpenapiSchemaOneOf)]): Map[String, Seq[(String, OpenapiSchemaOneOf)]] =
    allOneOfSchemas
      .flatMap { case (name, schema) =>
        val validatedChildren = schema.types.map {
          case ref: OpenapiSchemaRef if ref.isSchema => ref.stripped
          case other =>
            val unsupportedChild = other.getClass.getName
            throw new NotImplementedError(
              s"oneOf declarations are only supported when all variants are declared schemas. Found type '$unsupportedChild' as variant of $name"
            )
        }
        // If defined, check that the discriminator mappings match the oneOf refs
        schema.discriminator match {
          case None | Some(Discriminator(_, None)) => // if there's no discriminator or no mapping, nothing to validate
          case Some(Discriminator(_, Some(mapping))) =>
            val targetClassNames = mapping.values.map(_.split('/').last).toSet
            if (targetClassNames != validatedChildren.toSet)
              throw new IllegalArgumentException(
                s"Discriminator values $targetClassNames did not match schema variants $validatedChildren for oneOf defn $name"
              )
        }
        validatedChildren.map(_ -> ((name, schema)))
      }
      .groupBy(_._1)
      .mapValues(_.map(_._2))

  private def enumSerdeHelperDefn(targetScala3: Boolean): String = {
    if (targetScala3)
      """
        |def enumMap[E: enumextensions.EnumMirror]: Map[String, E] =
        |  Map.from(
        |    for e <- enumextensions.EnumMirror[E].values yield e.name.toUpperCase -> e
        |  )
        |case class EnumExtraParamSupport[T: enumextensions.EnumMirror](eMap: Map[String, T]) extends ExtraParamSupport[T] {
        |  // Case-insensitive mapping
        |  def decode(s: String): sttp.tapir.DecodeResult[T] =
        |    scala.util
        |      .Try(eMap(s.toUpperCase))
        |      .fold(
        |        _ =>
        |          sttp.tapir.DecodeResult.Error(
        |            s,
        |            new NoSuchElementException(
        |              s"Could not find value $s for enum ${enumextensions.EnumMirror[T].mirroredName}, available values: ${enumextensions.EnumMirror[T].values.mkString(", ")}"
        |            )
        |          ),
        |        sttp.tapir.DecodeResult.Value(_)
        |      )
        |  def encode(t: T): String = t.name
        |}
        |def extraCodecSupport[T: enumextensions.EnumMirror]: ExtraParamSupport[T] =
        |  EnumExtraParamSupport(enumMap[T](using enumextensions.EnumMirror[T]))
        |""".stripMargin
    else
      """
        |case class EnumExtraParamSupport[T <: enumeratum.EnumEntry](enumName: String, T: enumeratum.Enum[T]) extends ExtraParamSupport[T] {
        |  // Case-insensitive mapping
        |  def decode(s: String): sttp.tapir.DecodeResult[T] =
        |    scala.util.Try(T.upperCaseNameValuesToMap(s.toUpperCase))
        |      .fold(
        |        _ =>
        |          sttp.tapir.DecodeResult.Error(
        |            s,
        |            new NoSuchElementException(
        |              s"Could not find value $s for enum ${enumName}, available values: ${T.values.mkString(", ")}"
        |            )
        |          ),
        |        sttp.tapir.DecodeResult.Value(_)
        |      )
        |  def encode(t: T): String = t.entryName
        |}
        |def extraCodecSupport[T <: enumeratum.EnumEntry](enumName: String, T: enumeratum.Enum[T]): ExtraParamSupport[T] =
        |  EnumExtraParamSupport(enumName, T)
        |""".stripMargin
  }

  private[codegen] def generateMap(
      name: String,
      valueSchema: OpenapiSchemaType
  ): Seq[String] = {
    val valueSchemaName = valueSchema match {
      case simpleType: OpenapiSchemaSimpleType => RootGenerator.mapSchemaSimpleTypeToType(simpleType)._1
      case otherType => throw new NotImplementedError(s"Only simple value types and refs are implemented for named maps (found $otherType)")
    }
    Seq(s"""type $name = Map[String, $valueSchemaName]""")
  }

  private[codegen] def generateArray(
      name: String,
      valueSchema: OpenapiSchemaType,
      rs: ArrayRestrictions
  ): Seq[String] = {
    val valueSchemaName = valueSchema match {
      case simpleType: OpenapiSchemaSimpleType => RootGenerator.mapSchemaSimpleTypeToType(simpleType)._1
      case otherType =>
        throw new NotImplementedError(s"Only simple value types and refs are implemented for named arrays (found $otherType)")
    }
    if (rs.uniqueItems.contains(true)) Seq(s"""type $name = Set[$valueSchemaName]""")
    else Seq(s"""type $name = List[$valueSchemaName]""")
  }

  private[codegen] def generateClass(
      allSchemas: Map[String, OpenapiSchemaType],
      name: String,
      obj: OpenapiSchemaObject,
      jsonParamRefs: Set[String],
      adtInheritanceMap: Map[String, Seq[(String, OpenapiSchemaOneOf)]],
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      targetScala3: Boolean
  ): Seq[String] = try {
    val isJson = jsonParamRefs contains name
    def rec(name: String, obj: OpenapiSchemaObject, acc: List[String]): Seq[String] = {
      val innerClasses = obj.properties
        .collect {
          case (propName, OpenapiSchemaField(st: OpenapiSchemaObject, _, _)) =>
            val newName = addName(name, propName)
            rec(newName, st, Nil)

          case (propName, OpenapiSchemaField(OpenapiSchemaMap(st: OpenapiSchemaObject, _, _), _, _)) =>
            val newName = addName(addName(name, propName), "item")
            rec(newName, st, Nil)

          case (propName, OpenapiSchemaField(OpenapiSchemaArray(st: OpenapiSchemaObject, _, _, _), _, _)) =>
            val newName = addName(addName(name, propName), "item")
            rec(newName, st, Nil)
        }
        .flatten
        .toList

      val parents = adtInheritanceMap.getOrElse(name, Nil) match {
        case Nil => ""
        case ps  => ps.map(_._1).mkString(" extends ", " with ", "")
      }
      val discriminatorDefFields = adtInheritanceMap
        .getOrElse(name, Nil)
        .flatMap { case (_, parent) =>
          parent.discriminator.map { d =>
            d.propertyName -> d.mapping.flatMap(_.find(_._2.stripPrefix("#/components/schemas/") == name).map(_._1)).getOrElse(name)
          }
        }
        .distinct
      val discriminatorDefBody = discriminatorDefFields.filter { case (n, _) => obj.properties.map(_._1).toSet.contains(n) } match {
        case Nil => ""
        case fields =>
          val fs = fields.map { case (k, v) => s"""def `$k`: String = "$v"""" }.mkString("\n")
          s""" {
             |${indent(2)(fs)}
             |}""".stripMargin
      }

      val (properties, maybeEnums) = obj.properties
        .filterNot(discriminatorDefFields.map(_._1) contains _._1)
        .map { case (key, OpenapiSchemaField(schemaType, maybeDefault, _)) =>
          val (tpe, maybeEnum) = mapSchemaTypeToType(name, key, obj.required.contains(key), schemaType, isJson, jsonSerdeLib, targetScala3)
          val fixedKey = fixKey(key)
          val optional = schemaType.nullable || !obj.required.contains(key)
          val maybeExplicitDefault =
            maybeDefault.map(
              " = " +
                DefaultValueRenderer
                  .render(allModels = allSchemas, thisType = schemaType, optional, RenderConfig(maybeEnum.map(_.enumName)))(_)
            )
          val default = maybeExplicitDefault getOrElse (if (optional) " = None" else "")
          s"$fixedKey: $tpe$default" -> maybeEnum.map(_.impl)
        }
        .unzip

      val enumDefn = maybeEnums.flatten.toList
      s"""|case class $name (
          |${indent(2)(properties.mkString(",\n"))}
          |)$parents$discriminatorDefBody""".stripMargin :: innerClasses ::: enumDefn ::: acc
    }

    rec(addName("", name), obj, Nil)
  } catch {
    case t: Throwable => throw new NotImplementedError(s"Generating class for $name: ${t.getMessage}")
  }

  private def mapSchemaTypeToType(
      parentName: String,
      key: String,
      required: Boolean,
      schemaType: OpenapiSchemaType,
      isJson: Boolean,
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      targetScala3: Boolean
  ): (String, Option[InlineEnumDefn]) = schemaType match {
    case OpenapiSchemaAllOf(Seq(singleElem)) =>
      mapSchemaTypeToType(parentName, key, required, singleElem, isJson, jsonSerdeLib, targetScala3)
    case _ =>
      val ((tpe, optional), maybeEnum) = schemaType match {

        case simpleType: OpenapiSchemaSimpleType =>
          mapSchemaSimpleTypeToType(simpleType, multipartForm = !isJson) -> None

        case objectType: OpenapiSchemaObject =>
          (addName(parentName, key) -> objectType.nullable, None)

        case mapType: OpenapiSchemaMap =>
          val (innerType, maybeEnum) =
            mapSchemaTypeToType(
              addName(parentName, key),
              "item",
              required = true,
              mapType.items,
              isJson = isJson,
              jsonSerdeLib,
              targetScala3
            )
          (s"Map[String, $innerType]" -> mapType.nullable, maybeEnum)

        case arrayType: OpenapiSchemaArray =>
          val (innerType, maybeEnum) =
            mapSchemaTypeToType(
              addName(parentName, key),
              "item",
              required = true,
              arrayType.items,
              isJson = isJson,
              jsonSerdeLib,
              targetScala3
            )
          val container = if (arrayType.restrictions.uniqueItems.contains(true)) "Set" else "Seq"
          (s"$container[$innerType]" -> arrayType.nullable, maybeEnum)

        case e: OpenapiSchemaEnum =>
          val enumName = addName(parentName.capitalize, key)
          val enumDefn = EnumGenerator.generateEnum(
            enumName,
            e,
            targetScala3,
            Set.empty,
            jsonSerdeLib,
            if (isJson) Set(enumName) else Set.empty
          )
          (enumName -> e.nullable, Some(InlineEnumDefn(enumName, enumDefn.mkString("\n"))))

        case _ =>
          throw new NotImplementedError(s"We can't serialize some of the properties yet! $parentName $key $schemaType")
      }

      (if (optional || !required) s"Option[$tpe]" else tpe, maybeEnum)
  }

  private def addName(parentName: String, key: String) = parentName + key.replace('_', ' ').replace('-', ' ').capitalize.replace(" ", "")

  private val reservedKeys = scala.reflect.runtime.universe.asInstanceOf[scala.reflect.internal.SymbolTable].nme.keywords.map(_.toString)

  private def fixKey(key: String) = {
    if (reservedKeys.contains(key))
      s"`$key`"
    else
      key
  }

  private def schemaContainsAny(schema: OpenapiSchemaType): Boolean = schema match {
    case _: OpenapiSchemaAny                => true
    case OpenapiSchemaArray(items, _, _, _) => schemaContainsAny(items)
    case OpenapiSchemaMap(items, _, _)      => schemaContainsAny(items)
    case OpenapiSchemaObject(fs, _, _, _)   => fs.values.map(_.`type`).exists(schemaContainsAny)
    case OpenapiSchemaOneOf(types, _)       => types.exists(schemaContainsAny)
    case OpenapiSchemaAllOf(types)          => types.exists(schemaContainsAny)
    case OpenapiSchemaAnyOf(types)          => types.exists(schemaContainsAny)
    case OpenapiSchemaNot(item)             => schemaContainsAny(item)
    case _: OpenapiSchemaSimpleType | _: OpenapiSchemaEnum | _: OpenapiSchemaConstantString | _: OpenapiSchemaRef => false
  }
}
