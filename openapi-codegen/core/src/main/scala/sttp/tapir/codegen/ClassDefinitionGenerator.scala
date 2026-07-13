package sttp.tapir.codegen

import sttp.tapir.codegen.endpoints.SimpleTypes.mapSchemaSimpleTypeToType
import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.endpoints.EnumGenerator
import sttp.tapir.codegen.json.{JsonHelpers, JsonSerdeGenerator, JsonSerdeLib, SerdeGenResponse}
import sttp.tapir.codegen.json.JsonSerdeLib.{Circe, Jsoniter}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.{DefaultValueRenderer, OpenapiSchemaType, RenderConfig}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._
import sttp.tapir.codegen.util.NameHelpers.{indent, safeVariableName}
import sttp.tapir.codegen.util.{DocUtils, JavaEscape, NameValidation, VersionedHelpers}
import sttp.tapir.codegen.xml.{XmlSerdeGenerator, XmlSerdeLib}

case class GeneratedClassDefinitions(
    classReprs: Map[String, String],
    jsonSerdeRepr: Option[String],
    schemaRepr: Seq[(Option[String], String)],
    xmlSerdeRepr: Option[String],
    schemasContainAny: Boolean,
    explicitNonObjTypes: Seq[String],
    allTransitiveJsonParamRefs: Set[String]
) {
  def classRepr: String = classReprs.toSeq.sortBy(_._1).map(_._2).filter(_.nonEmpty).mkString("\n")
}

case class InlineEnumDefn(enumName: String, impl: String)

class ClassDefinitionGenerator {
  private def canBeDisambiguated(doc: OpenapiDocument, schemaName: String, s: Seq[OpenapiSchemaSimpleType]): Boolean = {
    def bail(msg: String) = throw new RuntimeException(s"Unable to constructing internal representation for oneOf '$schemaName': $msg'")
    val classify: OpenapiSchemaSimpleType => Int = {
      case _: OpenapiSchemaBinary | _: OpenapiSchemaByte => bail("Binary/byte variants not supported on oneOf")
      case _: OpenapiSchemaDate                          => 1
      case _: OpenapiSchemaDateTime                      => 2
      case _: OpenapiSchemaDuration                      => 3
      case _: OpenapiSchemaUUID                          => 4
      case _: OpenapiSchemaBoolean                       => 5
      case _: OpenapiSchemaNumericType                   => 6
      case _: OpenapiSchemaStringType                    => 7
      case _: OpenapiSchemaAny                           => 8
      case _: OpenapiSchemaRef                           => 9
    }
    val grouped = s.zipWithIndex.groupBy(p => classify(p._1))
    val tps =
      Map(
        0 -> "binary",
        1 -> "date",
        2 -> "datetime",
        3 -> "duration",
        4 -> "uuid",
        5 -> "bool",
        6 -> "number",
        7 -> "string",
        8 -> "any",
        9 -> "obj"
      )
    (0 to 8).foreach(i => if (grouped.getOrElse(i, Nil).size > 1) bail(s"more than one ${tps(i)} variant found"))
    grouped.getOrElse(9, Seq.empty[(OpenapiSchemaSimpleType, Int)]) match {
      case Nil      =>
      case h +: Nil =>
      case seq      =>
        JsonHelpers.checkForSoundness(schemaName, doc.components.map(_.schemas).getOrElse(Map.empty))(seq.map(_._1))
    }
    val maxes = grouped.map { case (k, vs) => k -> vs.map(_._2).max }
    (0 to 4).foreach(i =>
      if (maxes.get(i).exists(m => maxes.get(7).exists(m2 => m > m2))) bail(s"${tps(i)} variant hidden by string variant")
    )
    (0 to 9).foreach(i => if (maxes.get(i).exists(m => maxes.get(8).exists(m2 => m > m2))) bail(s"${tps(i)} variant hidden by any variant"))
    true
  }

  def classDefs(
      doc: OpenapiDocument,
      targetScala3: Boolean,
      queryOrPathParamRefs: Set[String] = Set.empty,
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib = Circe,
      xmlSerdeLib: XmlSerdeLib.XmlSerdeLib = XmlSerdeLib.CatsXml,
      jsonParamRefs: Set[String] = Set.empty,
      fullModelPath: String = "",
      validateNonDiscriminatedOneOfs: Boolean = true,
      maxSchemasPerFile: Int = 400,
      enumsDefinedOnEndpointParams: Boolean = false,
      xmlParamRefs: Set[String] = Set.empty,
      useCustomJsoniterSerdes: Boolean = true,
      packageReuse: PackageReuseContext = PackageReuseContext.none,
      seperateFilesForModels: Boolean = false,
      alwaysGenerateParamSupport: Boolean = false
  ): Option[GeneratedClassDefinitions] = {
    NameValidation.validateDocumentNames(doc, useHeadTagForObjectNames = false)
    val allSchemas: Map[String, OpenapiSchemaType] = doc.components.toSeq.flatMap(_.schemas).toMap
    val allOneOfSchemas = allSchemas.collect { case (name, oneOf: OpenapiSchemaOneOf) => name -> oneOf }.toSeq
    val (allClassyOneOfSchemas, allOtherOneOfSchemas) = allOneOfSchemas.partition(_._2.types.forall {
      case r: OpenapiSchemaRef => r.maybeResolved(doc).forall(_.isInstanceOf[OpenapiSchemaObject])
      case _                   => false
    })
    val (resolvableNonClassyOneOfSchemas, unresolvableNCOOS) = allOtherOneOfSchemas.partition { p =>
      p._2.discriminator.isEmpty && canBeDisambiguated(doc, p._1, p._2.types)
    }
    if (unresolvableNCOOS.nonEmpty) // we throw on disambiguation errors, so this is the correct error message
      throw new RuntimeException(
        s"Unable to constructing internal representation for oneOf(s) '${unresolvableNCOOS.map(_._1)}': discriminator provided, but not all variants are objects!"
      )

    def nonClassyOneOfRepr(name: String, st: OpenapiSchemaOneOf): String = {
      val tt = name.capitalize
      val variants = st.types
        .map {
          case _: OpenapiSchemaBinary | _: OpenapiSchemaByte => s"case class ${tt}Bin(v: Array[Byte]) extends $tt"
          case _: OpenapiSchemaDate                          => s"case class ${tt}Date(v: java.time.LocalDate) extends $tt"
          case _: OpenapiSchemaDateTime                      => s"case class ${tt}DateTime(v: java.time.Instant) extends $tt"
          case _: OpenapiSchemaDuration                      => s"case class ${tt}Duration(v: java.time.Duration) extends $tt"
          case _: OpenapiSchemaUUID                          => s"case class ${tt}UUID(v: java.util.UUID) extends $tt"
          case _: OpenapiSchemaBoolean                       => s"case class ${tt}Boolean(v: Boolean) extends $tt"
          case t: OpenapiSchemaNumericType                   => s"case class $tt${t.scalaType}(v: ${t.scalaType}) extends $tt"
          case _: OpenapiSchemaStringType                    => s"case class ${tt}String(v: String) extends $tt"
          case _: OpenapiSchemaAny                           => s"case class ${tt}Json(v: io.circe.Json) extends $tt"
          case r: OpenapiSchemaRef                           => s"case class $tt${r.stripped}(v: ${r.stripped}) extends $tt"
        }
        .mkString("\n")
      s"""
         |sealed trait $tt
         |$variants""".stripMargin
    }
    val nonClassyOneOfReprs = resolvableNonClassyOneOfSchemas.map { case (name, st) => nonClassyOneOfRepr(name, st) }.mkString("\n")
    val adtInheritanceMap: Map[String, Seq[(String, OpenapiSchemaOneOf)]] = mkMapParentsByChild(allClassyOneOfSchemas)
    val generatesQueryOrPathParamEnums = enumsDefinedOnEndpointParams ||
      allSchemas
        .collect { case (name, _: OpenapiSchemaEnum) => name }
        .exists(alwaysGenerateParamSupport || queryOrPathParamRefs.contains(_))
    val enumSerdeHelper = if (!generatesQueryOrPathParamEnums) "" else EnumGenerator.enumSerdeHelperDefn(targetScala3)

    def fetchTransitiveParamRefs(initialSet: Set[String], toCheck: Seq[OpenapiSchemaType]): Set[String] = toCheck match {
      case Nil          => initialSet
      case head +: tail => DocUtils.recursiveFindAllReferencedSchemaTypes(allSchemas)(head, initialSet, tail)
    }

    val allTransitiveJsonParamRefs = fetchTransitiveParamRefs(
      jsonParamRefs,
      jsonParamRefs.toSeq.flatMap(ref => allSchemas.get(ref.stripPrefix("#/components/schemas/")))
    )

    def allChildrenDefineDiscriminator(d: String, s: OpenapiSchemaOneOf): Boolean =
      s.types.forall {
        case t: OpenapiSchemaRef =>
          t.maybeResolved(doc).exists {
            case o: OpenapiSchemaObject => o.properties.contains(d)
            case _                      => false
          }
        case _ => false
      }

    val adtTypes =
      adtInheritanceMap
        .flatMap(_._2)
        .toSeq
        .map { s => s._1 -> s._2.discriminator.map(_.propertyName).filter(d => allChildrenDefineDiscriminator(d, s._2)) }
        .distinct
        .filterNot(s => PackageReuseContext.isReusedSchema(s._1, packageReuse))
        .map { case (name, maybeDiscriminator) =>
          val maybeBody = maybeDiscriminator.map(d => s" { def ${safeVariableName(d)}: String }").getOrElse("")
          s"sealed trait $name$maybeBody"
        }
        .sorted
        .mkString("", "\n", "\n")
    val schemasWithAny = allSchemas.filter { case (_, schema) => schemaContainsAny(schema) }
    val schemasContainAny = schemasWithAny.nonEmpty || allTransitiveJsonParamRefs.contains("io.circe.Json")
    if (schemasContainAny && !Set(Circe, Jsoniter).contains(jsonSerdeLib))
      throw new NotImplementedError(
        s"any not implemented for json libs other than circe and jsoniter (problematic models: ${schemasWithAny.keys})"
      )
    val shimsAndSchemas = SchemaGenerator
      .generateSchemas(
        doc,
        allSchemas,
        fullModelPath,
        jsonSerdeLib,
        maxSchemasPerFile,
        schemasContainAny,
        targetScala3,
        packageReuse
      )
    val SerdeGenResponse(jsonSerdes, explicitNonObjTypes) = JsonSerdeGenerator.serdeDefs(
      doc,
      jsonSerdeLib,
      jsonParamRefs,
      allTransitiveJsonParamRefs,
      validateNonDiscriminatedOneOfs,
      adtInheritanceMap.mapValues(_.map(_._1)).toMap,
      targetScala3,
      schemasContainAny,
      useCustomJsoniterSerdes,
      packageReuse,
      resolvableNonClassyOneOfSchemas,
      seperateFilesForModels
    )
    val allTransitiveXmlParamRefs = fetchTransitiveParamRefs(
      xmlParamRefs,
      xmlParamRefs.toSeq.flatMap(ref => allSchemas.get(ref.stripPrefix("#/components/schemas/")))
    )
    val xmlSerdes = XmlSerdeGenerator.generateSerdes(xmlSerdeLib, doc, allTransitiveXmlParamRefs, targetScala3, packageReuse)

    def variantRefNames(oneOf: OpenapiSchemaOneOf): Set[String] =
      oneOf.types.collect { case r: OpenapiSchemaRef => r.stripped }.toSet

    val adtGroups: Map[String, Seq[String]] = allClassyOneOfSchemas
      .groupBy { case (_, st) => variantRefNames(st) }
      .map { case (_, group) =>
        val parents = group.map(_._1).sorted.distinct
        parents.head -> parents
      }

    val childToAdtFile: Map[String, String] = adtInheritanceMap.map { case (child, parents) =>
      val parentNames = parents.map(_._1)
      val fileName = adtGroups
        .find { case (_, ps) => parentNames.forall(ps.contains) }
        .map(_._1)
        .getOrElse(parentNames.sorted.head)
      child -> fileName
    }

    val schemaDefns: Map[String, (Int, Seq[String])] = doc.components
      .map(
        _.schemas.map {
          case (name, _: OpenapiSchemaEnum) if PackageReuseContext.isReusedSchema(name, packageReuse) =>
            (name, (0, Seq(PackageReuseContext.enumAliasType(name, packageReuse, seperateFilesForModels))))
          case (name, obj: OpenapiSchemaObject) =>
            val isReusedSchema = PackageReuseContext.isReusedSchema(name, packageReuse)
            (
              name,
              (
                if (isReusedSchema) 0 else 2,
                generateClass(
                  allSchemas,
                  name,
                  obj,
                  allTransitiveJsonParamRefs,
                  adtInheritanceMap,
                  jsonSerdeLib,
                  targetScala3,
                  isReusedSchema,
                  packageReuse,
                  seperateFilesForModels,
                  alwaysGenerateParamSupport
                )
              )
            )
          case (name, s) if PackageReuseContext.isReusedSchema(name, packageReuse) =>
            (name, (0, Seq(PackageReuseContext.aliasType(name, packageReuse, seperateFilesForModels))))
          case (name, obj: OpenapiSchemaEnum) =>
            (
              name,
              (
                1,
                EnumGenerator.generateEnum(
                  name,
                  obj,
                  targetScala3,
                  queryOrPathParamRefs,
                  jsonSerdeLib,
                  allTransitiveJsonParamRefs,
                  alwaysGenerateParamSupport
                )
              )
            )
          case (name, OpenapiSchemaMap(valueSchema, _, _)) =>
            (name, (0, generateMap(name, valueSchema)))
          case (name, OpenapiSchemaArray(valueSchema, _, _, rs)) =>
            (name, (0, generateArray(name, valueSchema, rs)))
          case (name, _: OpenapiSchemaOneOf) =>
            (name, (-1, Nil))
          case (name, r: OpenapiSchemaSimpleType) =>
            (name, (0, generateAlias(name, r)))
          case (n, x) => throw new NotImplementedError(s"Only objects, enums and maps supported! (for $n found ${x})")
        }
      )
      .getOrElse(Map.empty)

    def isTypeAlias(name: String): Boolean = allSchemas.get(name) match {
      case Some(_: OpenapiSchemaMap) | Some(_: OpenapiSchemaArray) | Some(_: OpenapiSchemaSimpleType) => true
      case _ if PackageReuseContext.isReusedSchema(name, packageReuse)                                => true
      case _                                                                                          => false
    }

    val classReprs: Map[String, String] = if (!seperateFilesForModels) {
      val defStr = adtTypes + "\n" +
        schemaDefns.toSeq.sortBy(d => (d._2._1, d._1)).flatMap(_._2._2).mkString("\n") + "\n" +
        nonClassyOneOfReprs
      val helpers = enumSerdeHelper.linesIterator.filterNot(_.forall(_.isWhitespace)).mkString("\n")
      Map("" -> (helpers + "\n" + defStr))
    } else {
      val (typeAliases, explicitDefns) = schemaDefns.toSeq.partition(_._2._1 == 0)
      val mainContent =
        enumSerdeHelper + "\n" + typeAliases.sortBy(_._1).flatMap(_._2._2).filterNot(_.forall(_.isWhitespace)).mkString("\n")

      val modelFiles = scala.collection.mutable.Map.empty[String, Vector[String]]

      def addModel(file: String, content: String): Unit =
        if (content.nonEmpty) modelFiles.update(file, modelFiles.getOrElse(file, Vector.empty) :+ content)

      explicitDefns.foreach { case (name, (_, defns)) =>
        if (childToAdtFile.contains(name)) ()
        else if (allSchemas.get(name).exists(_.isInstanceOf[OpenapiSchemaEnum]))
          addModel(name, defns.mkString("\n"))
        else if (allSchemas.get(name).exists(_.isInstanceOf[OpenapiSchemaObject]))
          addModel(name, defns.mkString("\n"))
      }

      adtGroups.toSeq.foreach { case (fileName, parentTraits) =>
        val isReused = isTypeAlias(fileName)
        val traits =
          if (isReused) parentTraits.map(p => s"type $p = ${packageReuse.modelRoot(seperateFilesForModels)}.$p").mkString("\n")
          else parentTraits.map(p => s"sealed trait $p").mkString("\n")
        val children = childToAdtFile.filter(_._2 == fileName).keys.toSeq.sorted
        val childContent = children
          .flatMap { child =>
            allSchemas.get(child).collect { case obj: OpenapiSchemaObject =>
              generateClass(
                allSchemas,
                child,
                obj,
                allTransitiveJsonParamRefs,
                adtInheritanceMap,
                jsonSerdeLib,
                targetScala3,
                isReused,
                packageReuse,
                seperateFilesForModels,
                alwaysGenerateParamSupport
              ).mkString("\n")
            }
          }
          .mkString("\n")
        val defns = Seq(traits, childContent).filter(_.nonEmpty)
        if (!isReused) addModel(fileName, defns.mkString("\n"))
      }

      resolvableNonClassyOneOfSchemas.filterNot(p => isTypeAlias(p._1)).foreach { case (name, st) =>
        addModel(name.capitalize, nonClassyOneOfRepr(name, st))
      }

      Map("" -> mainContent) ++ modelFiles.map { case (k, v) => k -> v.mkString("\n") }.filter(_._2.nonEmpty)
    }

    // Json serdes & schemas live in separate files from the class defns
    Some(
      GeneratedClassDefinitions(
        classReprs,
        jsonSerdes,
        shimsAndSchemas,
        xmlSerdes,
        schemasContainAny,
        explicitNonObjTypes,
        allTransitiveJsonParamRefs
      )
    )
  }

  private def mkMapParentsByChild(allOneOfSchemas: Seq[(String, OpenapiSchemaOneOf)]): Map[String, Seq[(String, OpenapiSchemaOneOf)]] =
    allOneOfSchemas
      .flatMap { case (name, schema) =>
        val validatedChildren = schema.types.map {
          case ref: OpenapiSchemaRef if ref.isSchema => ref.stripped
          case other                                 =>
            val unsupportedChild = other.getClass.getName
            throw new NotImplementedError(
              s"oneOf declarations are only supported when all variants are declared schemas. Found type '$unsupportedChild' as variant of $name"
            )
        }
        // If defined, check that the discriminator mappings match the oneOf refs
        schema.discriminator match {
          case None | Some(Discriminator(_, None))   => // if there's no discriminator or no mapping, nothing to validate
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
      .toMap

  private[codegen] def generateMap(
      name: String,
      valueSchema: OpenapiSchemaType
  ): Seq[String] = {
    val valueSchemaName = valueSchema match {
      case simpleType: OpenapiSchemaSimpleType => mapSchemaSimpleTypeToType(simpleType)._1
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
      case simpleType: OpenapiSchemaSimpleType => mapSchemaSimpleTypeToType(simpleType)._1
      case otherType                           =>
        throw new NotImplementedError(s"Only simple value types and refs are implemented for named arrays (found $otherType)")
    }
    if (rs.uniqueItems.contains(true)) Seq(s"""type $name = Set[$valueSchemaName]""")
    else Seq(s"""type $name = List[$valueSchemaName]""")
  }

  private[codegen] def generateAlias(name: String, valueSchema: OpenapiSchemaSimpleType): Seq[String] = valueSchema match {
    case r: OpenapiSchemaRef        => Seq(s"""type $name = ${r.stripped}""")
    case r: OpenapiSchemaSimpleType =>
      val simpleType = mapSchemaSimpleTypeToType(r)._1
      Seq(s"""type $name = $simpleType""")
  }

  private[codegen] def generateClass(
      allSchemas: Map[String, OpenapiSchemaType],
      name: String,
      obj: OpenapiSchemaObject,
      jsonParamRefs: Set[String],
      adtInheritanceMap: Map[String, Seq[(String, OpenapiSchemaOneOf)]],
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      targetScala3: Boolean,
      isReused: Boolean,
      packageReuse: PackageReuseContext,
      seperateFilesForModels: Boolean,
      alwaysGenerateParamSupport: Boolean
  ): Seq[String] = try {
    val isJson = jsonParamRefs contains name
    def rec(className: String, schemaKey: String, obj: OpenapiSchemaObject, acc: List[String]): Seq[String] = {
      val innerClasses = obj.properties
        .collect {
          case (propName, OpenapiSchemaField(st: OpenapiSchemaObject, _, _)) =>
            val newName = addName(className, propName)
            rec(newName, newName, st, Nil)

          case (propName, OpenapiSchemaField(OpenapiSchemaMap(st: OpenapiSchemaObject, _, _), _, _)) =>
            val newName = addName(addName(className, propName), "item")
            rec(newName, newName, st, Nil)

          case (propName, OpenapiSchemaField(OpenapiSchemaArray(st: OpenapiSchemaObject, _, _, _), _, _)) =>
            val newName = addName(addName(className, propName), "item")
            rec(newName, newName, st, Nil)
        }
        .flatten
        .toList

      val parents = adtInheritanceMap.getOrElse(schemaKey, Nil) match {
        case Nil => ""
        case ps  => ps.map(_._1).mkString(" extends ", " with ", "")
      }
      val discriminatorDefFields = adtInheritanceMap
        .getOrElse(schemaKey, Nil)
        .flatMap { case (_, parent) =>
          parent.discriminator.map { d =>
            d.propertyName -> d.mapping
              .flatMap(_.find(_._2.stripPrefix("#/components/schemas/") == schemaKey).map(_._1))
              .getOrElse(schemaKey)
          }
        }
        .distinct
      val discriminatorDefBody = discriminatorDefFields.filter { case (n, _) => obj.properties.map(_._1).toSet.contains(n) } match {
        case Nil    => ""
        case fields =>
          val fs = fields.map { case (k, v) => s"""def ${safeVariableName(k)}: String = "${JavaEscape.escapeString(v)}"""" }.mkString("\n")
          s""" {
             |${indent(2)(fs)}
             |}""".stripMargin
      }

      val (properties, maybeEnums) = obj.properties.toSeq
        .filterNot(discriminatorDefFields.map(_._1) contains _._1)
        .map { case (key, OpenapiSchemaField(schemaType, maybeDefault, _)) =>
          val (tpe, maybeEnum) =
            mapSchemaTypeToType(className, key, obj.required.contains(key), schemaType, isJson, jsonSerdeLib, targetScala3)
          val fixedKey = safeVariableName(key)
          val optional = schemaType.nullable || !obj.required.contains(key)
          val maybeExplicitDefault =
            maybeDefault.map(
              " = " +
                DefaultValueRenderer
                  .render(allModels = allSchemas, thisType = schemaType, optional, RenderConfig(maybeEnum.map(_.enumName)))(_)
            )
          val default = maybeExplicitDefault getOrElse (if (optional) " = None" else "")
          if (isReused) "" -> maybeEnum.map { e =>
            val alias = s"${e.enumName} = ${packageReuse.modelRoot(seperateFilesForModels)}.${e.enumName}"
            s"type $alias\nval $alias"
          }
          else s"$fixedKey: $tpe$default" -> maybeEnum.map(_.impl)
        }
        .unzip

      val enumDefn = maybeEnums.flatten.toList
      val modelDefn =
        if (isReused) {
          val alias = s"$className = ${packageReuse.modelRoot(seperateFilesForModels)}.$className"
          s"type $alias\nval $alias"
        } else
          s"""|case class $className (
              |${indent(2)(properties.mkString(",\n"))}
              |)$parents$discriminatorDefBody""".stripMargin
      modelDefn :: innerClasses ::: enumDefn ::: acc
    }

    rec(addName("", name), name, obj, Nil)
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
            if (isJson) Set(enumName) else Set.empty,
            false
          )
          (enumName -> e.nullable, Some(InlineEnumDefn(enumName, enumDefn.mkString("\n"))))

        case _ =>
          throw new NotImplementedError(s"We can't serialize some of the properties yet! $parentName $key $schemaType")
      }

      (if (optional || !required) s"Option[$tpe]" else tpe, maybeEnum)
  }

  private def addName(parentName: String, key: String) = RootGenerator.addName(parentName, key)

  private val reservedKeys = VersionedHelpers.reservedKeys

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
