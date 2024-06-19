package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.{indent, mapSchemaSimpleTypeToType}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.{OpenapiSchemaType, DefaultValueRenderer}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._

import scala.annotation.tailrec

case class GeneratedClassDefinitions(classRepr: String, serdeRepr: Option[String], schemaRepr: Seq[String])

class ClassDefinitionGenerator {

  def classDefs(
      doc: OpenapiDocument,
      targetScala3: Boolean = false,
      queryParamRefs: Set[String] = Set.empty,
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib = JsonSerdeLib.Circe,
      jsonParamRefs: Set[String] = Set.empty,
      fullModelPath: String = "",
      validateNonDiscriminatedOneOfs: Boolean = true,
      maxSchemasPerFile: Int = 400,
      enumsDefinedOnEndpointParams: Boolean = false
  ): Option[GeneratedClassDefinitions] = {
    val allSchemas: Map[String, OpenapiSchemaType] = doc.components.toSeq.flatMap(_.schemas).toMap
    val allOneOfSchemas = allSchemas.collect { case (name, oneOf: OpenapiSchemaOneOf) => name -> oneOf }.toSeq
    val adtInheritanceMap: Map[String, Seq[String]] = mkMapParentsByChild(allOneOfSchemas)
    val generatesQueryParamEnums = enumsDefinedOnEndpointParams ||
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

    val adtTypes = adtInheritanceMap.flatMap(_._2).toSeq.distinct.map(name => s"sealed trait $name").mkString("", "\n", "\n")
    val enumQuerySerdeHelper = if (!generatesQueryParamEnums) "" else enumQuerySerdeHelperDefn(targetScala3)
    val schemas = SchemaGenerator.generateSchemas(doc, allSchemas, fullModelPath, jsonSerdeLib, maxSchemasPerFile)
    val jsonSerdes = JsonSerdeGenerator.serdeDefs(
      doc,
      jsonSerdeLib,
      jsonParamRefs,
      allTransitiveJsonParamRefs,
      fullModelPath,
      validateNonDiscriminatedOneOfs,
      adtInheritanceMap,
      targetScala3
    )
    val defns = doc.components
      .map(_.schemas.flatMap {
        case (name, obj: OpenapiSchemaObject) =>
          generateClass(allSchemas, name, obj, allTransitiveJsonParamRefs, adtInheritanceMap, jsonSerdeLib, targetScala3)
        case (name, obj: OpenapiSchemaEnum) =>
          EnumGenerator.generateEnum(name, obj, targetScala3, queryParamRefs, jsonSerdeLib, allTransitiveJsonParamRefs)
        case (name, OpenapiSchemaMap(valueSchema, _)) => generateMap(name, valueSchema)
        case (_, _: OpenapiSchemaOneOf)               => Nil
        case (n, x) => throw new NotImplementedError(s"Only objects, enums and maps supported! (for $n found ${x})")
      })
      .map(_.mkString("\n"))
    val helpers = (enumQuerySerdeHelper + adtTypes).linesIterator
      .filterNot(_.forall(_.isWhitespace))
      .mkString("\n")
    // Json serdes & schemas live in separate files from the class defns
    defns.map(helpers + "\n" + _).map(defStr => GeneratedClassDefinitions(defStr, jsonSerdes, schemas))
  }

  private def mkMapParentsByChild(allOneOfSchemas: Seq[(String, OpenapiSchemaOneOf)]): Map[String, Seq[String]] =
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
        validatedChildren.map(_ -> name)
      }
      .groupBy(_._1)
      .mapValues(_.map(_._2))

  private def enumQuerySerdeHelperDefn(targetScala3: Boolean): String = {
    if (targetScala3)
      """
        |def enumMap[E: enumextensions.EnumMirror]: Map[String, E] =
        |  Map.from(
        |    for e <- enumextensions.EnumMirror[E].values yield e.name.toUpperCase -> e
        |  )
        |case class EnumQueryParamSupport[T: enumextensions.EnumMirror](eMap: Map[String, T]) extends QueryParamSupport[T] {
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
        |def queryCodecSupport[T: enumextensions.EnumMirror]: QueryParamSupport[T] =
        |  EnumQueryParamSupport(enumMap[T](using enumextensions.EnumMirror[T]))
        |""".stripMargin
    else
      """
        |case class EnumQueryParamSupport[T <: enumeratum.EnumEntry](enumName: String, T: enumeratum.Enum[T]) extends QueryParamSupport[T] {
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
        |def queryCodecSupport[T <: enumeratum.EnumEntry](enumName: String, T: enumeratum.Enum[T]): QueryParamSupport[T] =
        |  EnumQueryParamSupport(enumName, T)
        |""".stripMargin
  }

  @tailrec
  final def recursiveFindAllReferencedSchemaTypes(
      allSchemas: Map[String, OpenapiSchemaType]
  )(toCheck: OpenapiSchemaType, checked: Set[String], tail: Seq[OpenapiSchemaType]): Set[String] = {
    def nextParamsFromTypeSeq(types: Seq[OpenapiSchemaType]) = types match {
      case Nil          => None
      case next +: rest => Some((next, checked, rest ++ tail))
    }
    val maybeNextParams = toCheck match {
      case ref: OpenapiSchemaRef if ref.isSchema =>
        val name = ref.stripped
        val maybeAppended = if (checked contains name) None else allSchemas.get(name)
        (tail ++ maybeAppended) match {
          case Nil          => None
          case next +: rest => Some((next, checked + name, rest))
        }
      case OpenapiSchemaArray(items, _)                                => Some((items, checked, tail))
      case OpenapiSchemaNot(items)                                     => Some((items, checked, tail))
      case OpenapiSchemaMap(items, _)                                  => Some((items, checked, tail))
      case OpenapiSchemaOneOf(types, _)                                => nextParamsFromTypeSeq(types)
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
      valueSchema: OpenapiSchemaType
  ): Seq[String] = {
    val valueSchemaName = valueSchema match {
      case simpleType: OpenapiSchemaSimpleType => BasicGenerator.mapSchemaSimpleTypeToType(simpleType)._1
      case otherType => throw new NotImplementedError(s"Only simple value types and refs are implemented for named maps (found $otherType)")
    }
    Seq(s"""type $name = Map[String, $valueSchemaName]""")
  }

  private[codegen] def generateClass(
      allSchemas: Map[String, OpenapiSchemaType],
      name: String,
      obj: OpenapiSchemaObject,
      jsonParamRefs: Set[String],
      adtInheritanceMap: Map[String, Seq[String]],
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      targetScala3: Boolean
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

      val (properties, maybeEnums) = obj.properties.map { case (key, OpenapiSchemaField(schemaType, maybeDefault)) =>
        val (tpe, maybeEnum) = mapSchemaTypeToType(name, key, obj.required.contains(key), schemaType, isJson, jsonSerdeLib, targetScala3)
        val fixedKey = fixKey(key)
        val optional = schemaType.nullable || !obj.required.contains(key)
        val maybeExplicitDefault =
          maybeDefault.map(" = " + DefaultValueRenderer.render(allModels = allSchemas, thisType = schemaType, optional)(_))
        val default = maybeExplicitDefault getOrElse (if (optional) " = None" else "")
        s"$fixedKey: $tpe$default" -> maybeEnum
      }.unzip

      val parents = adtInheritanceMap.getOrElse(name, Nil) match {
        case Nil => ""
        case ps  => ps.mkString(" extends ", " with ", "")
      }

      val enumDefn = maybeEnums.collect { case Some(defn) => defn }.toList
      s"""|case class $name (
          |${indent(2)(properties.mkString(",\n"))}
          |)$parents""".stripMargin :: innerClasses ::: enumDefn ::: acc
    }

    rec(addName("", name), obj, Nil)
  }

  private def mapSchemaTypeToType(
      parentName: String,
      key: String,
      required: Boolean,
      schemaType: OpenapiSchemaType,
      isJson: Boolean,
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      targetScala3: Boolean
  ): (String, Option[String]) = {
    val ((tpe, optional), maybeEnum) = schemaType match {
      case simpleType: OpenapiSchemaSimpleType =>
        mapSchemaSimpleTypeToType(simpleType, multipartForm = !isJson) -> None

      case objectType: OpenapiSchemaObject =>
        (addName(parentName, key) -> objectType.nullable, None)

      case mapType: OpenapiSchemaMap =>
        val (innerType, maybeEnum) =
          mapSchemaTypeToType(addName(parentName, key), "item", required = true, mapType.items, isJson = isJson, jsonSerdeLib, targetScala3)
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
        (s"Seq[$innerType]" -> arrayType.nullable, maybeEnum)

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
        (enumName -> e.nullable, Some(enumDefn.mkString("\n")))

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
}
