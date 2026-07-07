package sttp.tapir.codegen

import sttp.tapir.codegen.dedup.{GenerationMeta, PackageReuseContext}
import sttp.tapir.codegen.endpoints.{Akka, EndpointDefs, EndpointDetails, EndpointGenerator, FS2, Pekko, StreamingImplementation, Zio}
import sttp.tapir.codegen.json.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._
import sttp.tapir.codegen.openapi.models.SpecificationExtensionRenderer
import sttp.tapir.codegen.security.SecurityGenerator
import sttp.tapir.codegen.util.NameHelpers
import sttp.tapir.codegen.validation.{ValidationDefns, ValidationGenerator}
import sttp.tapir.codegen.xml.{XmlSerdeGenerator, XmlSerdeLib}

case class GenerationInfo(allFiles: Map[String, String], meta: GenerationMeta)

object RootGenerator {

  val classGenerator = new ClassDefinitionGenerator()
  val endpointGenerator = new EndpointGenerator()

  def generateObjects(
      unNormalisedDoc: OpenapiDocument,
      packagePath: String,
      objName: String,
      targetScala3: Boolean,
      useHeadTagForObjectNames: Boolean,
      jsonSerdeLib: String,
      xmlSerdeLib: String,
      streamingImplementation: String,
      validateNonDiscriminatedOneOfs: Boolean,
      maxSchemasPerFile: Int,
      generateEndpointTypes: Boolean,
      generateValidators: Boolean,
      useCustomJsoniterSerdes: Boolean,
      packageReuse: PackageReuseContext,
      seperateFilesForModels: Boolean,
      alwaysGenerateParamSupport: Boolean
  ): GenerationInfo = {
    val doc = unNormalisedDoc.resolveAllOfSchemas
    val normalisedJsonLib = jsonSerdeLib.toLowerCase match {
      case "circe"    => JsonSerdeLib.Circe
      case "jsoniter" => JsonSerdeLib.Jsoniter
      case "zio"      => JsonSerdeLib.Zio
      case _          =>
        System.err.println(
          s"!!! Unrecognised value $jsonSerdeLib for json serde lib -- should be one of circe, jsoniter, zio. Defaulting to circe !!!"
        )
        JsonSerdeLib.Circe
    }
    val normalisedXmlLib = xmlSerdeLib.toLowerCase match {
      case "cats-xml" => XmlSerdeLib.CatsXml
      case "none"     => XmlSerdeLib.NoSupport
      case _          =>
        System.err.println(
          s"!!! Unrecognised value $xmlSerdeLib for xml serde lib -- should be one of cats-xml, none. Defaulting to none !!!"
        )
        XmlSerdeLib.NoSupport
    }
    val fs2WithEffect = """fs2-(.+)""".r
    val normalisedStreamingImplementation: StreamingImplementation = streamingImplementation.toLowerCase match {
      case "akka"  => Akka
      case "fs2"   => FS2()
      case "pekko" => Pekko
      case "zio"   => Zio
      case _       =>
        streamingImplementation match {
          case fs2WithEffect(effectType) =>
            FS2(effectType)
          case _ =>
            System.err.println(
              s"!!! Unrecognised value $streamingImplementation for streaming impl -- should be one of akka, fs2, pekko or zio. Defaulting to fs2 !!!"
            )
            FS2()
        }
    }

    val validators = if (generateValidators) ValidationGenerator.mkValidators(doc) else ValidationDefns.empty

    val EndpointDefs(
      endpointsByTag,
      queryOrPathParamRefs,
      enumsDefinedOnEndpointParams,
      EndpointDetails(jsonParamRefs, inlineDefns, xmlParamRefs, securityWrappers)
    ) =
      endpointGenerator.endpointDefs(
        doc,
        useHeadTagForObjectNames,
        targetScala3,
        normalisedJsonLib,
        normalisedXmlLib,
        normalisedStreamingImplementation,
        generateEndpointTypes,
        validators,
        generateValidators,
        packageReuse,
        seperateFilesForModels
      )
    val modelPackagePath = s"$packagePath.models"
    val GeneratedClassDefinitions(
      classDefns,
      jsonSerdes,
      shimsAndSchemas,
      xmlSerdes,
      schemasContainAny,
      explicitNonObjTypes,
      allTransitiveJsonParamRefs
    ) =
      classGenerator
        .classDefs(
          doc = doc,
          targetScala3 = targetScala3,
          queryOrPathParamRefs = queryOrPathParamRefs,
          jsonSerdeLib = normalisedJsonLib,
          xmlSerdeLib = normalisedXmlLib,
          jsonParamRefs = jsonParamRefs,
          fullModelPath = if (seperateFilesForModels) modelPackagePath else s"$packagePath.$objName",
          validateNonDiscriminatedOneOfs = validateNonDiscriminatedOneOfs,
          maxSchemasPerFile = maxSchemasPerFile,
          enumsDefinedOnEndpointParams = enumsDefinedOnEndpointParams,
          xmlParamRefs = xmlParamRefs,
          useCustomJsoniterSerdes = useCustomJsoniterSerdes,
          packageReuse = packageReuse,
          seperateFilesForModels = seperateFilesForModels,
          alwaysGenerateParamSupport = alwaysGenerateParamSupport
        )
        .getOrElse(GeneratedClassDefinitions(Map.empty, None, Nil, None, false, Nil, Set.empty))

    val schemas = shimsAndSchemas.map(_._2)
    val hasJsonSerdes = jsonSerdes.nonEmpty
    val hasXmlSerdes = xmlSerdes.nonEmpty

    val maybeValidatorImport = if (validators.defns.nonEmpty) s"\nimport $packagePath.${objName}Validators._" else ""
    val maybeJsonImport = if (hasJsonSerdes) s"\nimport $packagePath.${objName}JsonSerdes._" else ""
    val maybeXmlImport = if (hasXmlSerdes) s"\nimport $packagePath.${objName}XmlSerdes._" else ""
    val maybeModelImport = if (seperateFilesForModels) s"\nimport $modelPackagePath._" else ""
    val maybeSchemaImport =
      if (schemas.size > 1) (1 to schemas.size).map(i => s"import ${objName}Schemas$i._").mkString("\n", "\n", "")
      else if (schemas.size == 1) s"\nimport ${objName}Schemas._"
      else ""
    val internalImports =
      s"import $packagePath.$objName._$maybeModelImport$maybeValidatorImport$maybeJsonImport$maybeXmlImport$maybeSchemaImport"

    val taggedObjs = endpointsByTag.collect {
      case (Some(headTag), body) if body.nonEmpty =>
        val taggedObj =
          s"""package $packagePath
           |
           |$internalImports
           |
           |object $headTag {
           |
           |${indent(2)(imports(normalisedJsonLib))}
           |
           |${indent(2)(body)}
           |
           |}""".stripMargin
        headTag -> taggedObj
    }
    val validationObj =
      if (validators.defns.isEmpty) None
      else {
        val body =
          s"""package $packagePath
             |
             |object ${objName}Validators {
             |${indent(2)(s"import $packagePath.$objName._$maybeModelImport")}
             |  import sttp.tapir.{ValidationResult, Validator}
             |
             |${indent(2)(validators.render(packageReuse))}
             |}""".stripMargin
        Some(s"${objName}Validators" -> body)
      }

    val jsonSerdeObj = jsonSerdes.map { body =>
      s"""package $packagePath
         |
         |object ${objName}JsonSerdes {
         |${indent(2)(s"import $packagePath.$objName._$maybeModelImport")}
         |  import sttp.tapir.generic.auto._
         |${indent(2)(body)}
         |}""".stripMargin
    }

    val xmlSerdeObj =
      xmlSerdes.map(XmlSerdeGenerator.wrapBody(normalisedXmlLib, packagePath, objName, targetScala3, _, seperateFilesForModels))

    val schemaObjs = if (schemas.size > 1) shimsAndSchemas.zipWithIndex.map { case ((shims, body), idx) =>
      val priorImports = (0 until idx).map { i => s"import $packagePath.${objName}Schemas${i + 1}._" }.mkString("\n")
      val suffix = idx + 1
      val name = s"${objName}Schemas$suffix"
      name -> s"""package $packagePath
         |
         |${shims.map(_.replace("object Refs ", s"object Refs$suffix ")).getOrElse("")}
         |object $name {
         |${indent(2)(s"import $packagePath.$objName._$maybeModelImport")}
         |  import sttp.tapir.generic.auto._
         |${indent(2)(priorImports)}
         |${indent(2)(body.replace(" Refs.", s" Refs$suffix."))}
         |}""".stripMargin
    }
    else if (schemas.size == 1)
      Seq(s"${objName}Schemas" -> s"""package $packagePath
         |
         |${shimsAndSchemas.head._1.getOrElse("")}
         |object ${objName}Schemas {
         |${indent(2)(s"import $packagePath.$objName._$maybeModelImport")}
         |  import sttp.tapir.generic.auto._
         |${indent(2)(schemas.head)}
         |}""".stripMargin)
    else Nil

    val endpointsInMain = endpointsByTag.getOrElse(None, "")

    val extensions: Seq[(String, String, String)] = doc.paths
      .flatMap { p =>
        p.specificationExtensions.toSeq ++ p.methods.flatMap(_.specificationExtensions.toSeq)
      }
      .groupBy(_._1)
      .map { case (keyName, pairs) =>
        val values = pairs.map(_._2)
        val `type` = SpecificationExtensionRenderer.renderCombinedType(values)
        val name = NameHelpers.strippedToCamelCase(keyName)
        val uncapitalisedName = uncapitalise(name)
        val capitalisedName = uncapitalisedName.capitalize
        (s"${capitalisedName}Extension", s"${uncapitalisedName}ExtensionKey", `type`)
      }
      .toSeq
      .sortBy(_._1)
    val maybeSpecificationExtensionKeys = extensions
      .map { case tup @ (typeName, keyName, tpe) =>
        if (packageReuse.dependencyMeta.extensions.contains(tup))
          s"""type $typeName = ${packageReuse.dependencyModelPath}.$typeName
             |val $keyName = ${packageReuse.dependencyModelPath}.$keyName
             |""".stripMargin
        else s"""type $typeName = $tpe
           |val $keyName = new sttp.tapir.AttributeKey[$typeName]("$packagePath.$objName.$typeName")
           |""".stripMargin
      }
      .mkString("\n")

    val expectedTypes =
      Set("text/plain", "text/html", "application/json", "application/xml", "multipart/form-data", "application/octet-stream")
    val mediaType = "([^/]+)/(.+)".r
    val customTypes = doc.paths
      .flatMap(
        _.methods.flatMap(m =>
          m.requestBody.toSeq.flatMap(_.resolve(doc).content.map(_.contentType)) ++
            m.responses.flatMap(_.resolve(doc).content.map(_.contentType))
        )
      )
      .distinct
      .sorted
      .filterNot(expectedTypes.contains)
      .map {
        case ct @ mediaType(mainType, subType) =>
          s"""case class `${ct}CodecFormat`() extends CodecFormat {
           |  override val mediaType: sttp.model.MediaType = sttp.model.MediaType.unsafeApply(mainType = "$mainType", subType = "$subType")
           |}""".stripMargin
        case ct => throw new NotImplementedError(s"Cannot handle content type '$ct'")
      }
      .mkString("\n")
    val extraImports =
      if (endpointsInMain.nonEmpty) s"$maybeModelImport$maybeValidatorImport$maybeJsonImport$maybeXmlImport$maybeSchemaImport"
      else ""

    val paramListHelpers =
      if (packageReuse.reusedSchemas.nonEmpty)
        s"""
           |type CommaSeparatedValues[T] = ${packageReuse.modelRoot(seperateFilesForModels)}.CommaSeparatedValues[T]
           |def CommaSeparatedValues[T](values: List[T]) = ${packageReuse.modelRoot(seperateFilesForModels)}.CommaSeparatedValues[T](values)
           |type ExplodedValues[T] = ${packageReuse.modelRoot(seperateFilesForModels)}.ExplodedValues[T]
           |def ExplodedValues[T](values: List[T]) = ${packageReuse.modelRoot(seperateFilesForModels)}.ExplodedValues[T](values)
           |type ExtraParamSupport[T] = ${packageReuse.modelRoot(seperateFilesForModels)}.ExtraParamSupport[T]""".stripMargin
      else
        s"""
         |case class CommaSeparatedValues[T](values: List[T])
         |case class ExplodedValues[T](values: List[T])
         |trait ExtraParamSupport[T] {
         |  def decode(s: String): sttp.tapir.DecodeResult[T]
         |  def encode(t: T): String
         |}""".stripMargin
    val queryParamSupport =
      s"""
      |${if (!seperateFilesForModels) paramListHelpers else ""}
      |implicit def makePathCodecFromSupport[T](implicit support: ExtraParamSupport[T]): sttp.tapir.Codec[String, T, sttp.tapir.CodecFormat.TextPlain] = {
      |  sttp.tapir.Codec.string.mapDecode(support.decode)(support.encode)
      |}
      |implicit def makeQueryCodecFromSupport[T](implicit support: ExtraParamSupport[T]): sttp.tapir.Codec[List[String], T, sttp.tapir.CodecFormat.TextPlain] = {
      |  sttp.tapir.Codec.listHead[String, String, sttp.tapir.CodecFormat.TextPlain]
      |    .mapDecode(support.decode)(support.encode)
      |}
      |implicit def makeQueryOptCodecFromSupport[T](implicit support: ExtraParamSupport[T]): sttp.tapir.Codec[List[String], Option[T], sttp.tapir.CodecFormat.TextPlain] = {
      |  sttp.tapir.Codec.listHeadOption[String, String, sttp.tapir.CodecFormat.TextPlain]
      |    .mapDecode(maybeV => DecodeResult.sequence(maybeV.toSeq.map(support.decode)).map(_.headOption))(_.map(support.encode))
      |}
      |implicit def makeUnexplodedQuerySeqCodecFromListHead[T](implicit support: sttp.tapir.Codec[List[String], T, sttp.tapir.CodecFormat.TextPlain]): sttp.tapir.Codec[List[String], CommaSeparatedValues[T], sttp.tapir.CodecFormat.TextPlain] = {
      |  sttp.tapir.Codec.listHead[String, String, sttp.tapir.CodecFormat.TextPlain]
      |    .mapDecode(values => DecodeResult.sequence(values.split(',').toSeq.map(e => support.rawDecode(List(e)))).map(s => CommaSeparatedValues(s.toList)))(_.values.flatMap(support.encode).mkString(","))
      |}
      |implicit def makeUnexplodedQueryOptSeqCodecFromListHead[T](implicit support: sttp.tapir.Codec[List[String], T, sttp.tapir.CodecFormat.TextPlain]): sttp.tapir.Codec[List[String], Option[CommaSeparatedValues[T]], sttp.tapir.CodecFormat.TextPlain] = {
      |  sttp.tapir.Codec.listHeadOption[String, String, sttp.tapir.CodecFormat.TextPlain]
      |    .mapDecode{
      |      case None => DecodeResult.Value(None)
      |      case Some(values) => DecodeResult.sequence(values.split(',').toSeq.map(e => support.rawDecode(List(e)))).map(r => Some(CommaSeparatedValues(r.toList)))
      |    }(_.map(_.values.flatMap(support.encode).mkString(",")))
      |}
      |implicit def makeExplodedQuerySeqCodecFromListSeq[T](implicit support: sttp.tapir.Codec[List[String], List[T], sttp.tapir.CodecFormat.TextPlain]): sttp.tapir.Codec[List[String], ExplodedValues[T], sttp.tapir.CodecFormat.TextPlain] = {
      |  support.mapDecode(l => DecodeResult.Value(ExplodedValues(l)))(_.values)
      |}
      |""".stripMargin

    val securityTypes = SecurityGenerator.genSecurityTypes(securityWrappers, packageReuse)
    val byteStringDecl =
      if (packageReuse.reusedSchemas.nonEmpty)
        s"type ByteString = ${packageReuse.depPkg}.$objName.ByteString"
      else "type ByteString <: Array[Byte]"
    val mainClassDefns =
      if (seperateFilesForModels) classDefns.getOrElse("", "")
      else classDefns.toSeq.sortBy(_._1).map(_._2).mkString("\n")

    val maybeModelPackage =
      if (seperateFilesForModels) Some(s"""
         |package $packagePath
         |
         |package object models {
         |${indent(2)(paramListHelpers)}
         |${indent(2)(mainClassDefns)}
         |${indent(2)(inlineDefns.mkString("\n"))}
         |}""".stripMargin)
      else None

    val maybeModels =
      if (seperateFilesForModels) ""
      else s"""${indent(2)(mainClassDefns)}
              |${indent(2)(inlineDefns.mkString("\n"))}
              |""".stripMargin

    val mainObj = s"""
        |package $packagePath
        |
        |object $objName {
        |
        |${indent(2)(imports(normalisedJsonLib) + extraImports + maybeModelImport)}
        |
        |${indent(2)(customTypes)}
        |${indent(2)(securityTypes)}
        |${indent(2)(queryParamSupport)}
        |  implicit class RichBody[A, T](bod: EndpointIO.Body[A, T]) {
        |    def widenBody[TT >: T]: EndpointIO.Body[A, TT] = bod.map(_.asInstanceOf[TT])(_.asInstanceOf[T])
        |  }
        |  implicit class RichStreamBody[A, T, R](bod: sttp.tapir.StreamBodyIO[A, T, R]) {
        |    def widenBody[TT >: T]: sttp.tapir.StreamBodyIO[A, TT, R] = bod.map(_.asInstanceOf[TT])(_.asInstanceOf[T])
        |  }
        |  $byteStringDecl
        |  implicit def toByteString(ba: Array[Byte]): ByteString = ba.asInstanceOf[ByteString]
        |
        |$maybeModels
        |${indent(2)(maybeSpecificationExtensionKeys)}
        |
        |${indent(2)(endpointsInMain)}
        |${indent(2)(ServersGenerator.genServerDefinitions(doc.servers, targetScala3).getOrElse(""))}
        |}
        |""".stripMargin
    val modelFiles =
      if (seperateFilesForModels)
        classDefns.filter(_._1.nonEmpty).map { case (fn, body) =>
          s"models.$fn" ->
            s"""package $modelPackagePath
               |
               |$body
               |""".stripMargin
        }
      else Map.empty
    val allFiles = taggedObjs ++ jsonSerdeObj.map(s"${objName}JsonSerdes" -> _) ++ xmlSerdeObj.map(s"${objName}XmlSerdes" -> _) ++
      validationObj ++ schemaObjs ++ modelFiles ++ maybeModelPackage.map("models.package" -> _) + (objName -> mainObj)
    GenerationInfo(
      allFiles,
      GenerationMeta(
        schemaObjs.map(_._1),
        validationObj.isDefined,
        schemasContainAny,
        explicitNonObjTypes,
        securityWrappers,
        extensions,
        schemaObjs.size,
        allTransitiveJsonParamRefs,
        jsonParamRefs,
        packageReuse.reusedSchemas
      )
    )
  }

  private[codegen] def imports(jsonSerdeLib: JsonSerdeLib.JsonSerdeLib): String = {
    val jsonImports = jsonSerdeLib match {
      case JsonSerdeLib.Circe =>
        """import sttp.tapir.json.circe._
          |import io.circe.generic.semiauto._""".stripMargin
      case JsonSerdeLib.Jsoniter =>
        """import sttp.tapir.json.jsoniter._
          |import com.github.plokhotnyuk.jsoniter_scala.macros._
          |import com.github.plokhotnyuk.jsoniter_scala.core._""".stripMargin
      case JsonSerdeLib.Zio =>
        """import sttp.tapir.json.zio._
          |import zio.json._""".stripMargin
    }
    s"""import sttp.tapir._
       |import sttp.tapir.model._
       |import sttp.tapir.generic.auto._
       |$jsonImports
       |""".stripMargin
  }

  def indent(i: Int)(str: String): String = NameHelpers.indent(i)(str)

  def uncapitalise(name: String): String = NameHelpers.uncapitalise(name)

  def addName(parentName: String, key: String): String = NameHelpers.addName(parentName, key)

  private def schemaDependencyImportsFor(packageReuse: PackageReuseContext): String =
    if (packageReuse.reusedSchemas.isEmpty) ""
    else {
      val depPkg = packageReuse.depPkg
      val depObj = packageReuse.dependencyObjectName
      packageReuse.dependencyMeta.schemaFiles.map(s => s"\nimport $depPkg.$s._").mkString("\n")
    }
}
