package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaAny,
  OpenapiSchemaBinary,
  OpenapiSchemaBoolean,
  OpenapiSchemaDateTime,
  OpenapiSchemaDouble,
  OpenapiSchemaFloat,
  OpenapiSchemaInt,
  OpenapiSchemaLong,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType,
  OpenapiSchemaString,
  OpenapiSchemaUUID
}
import sttp.tapir.codegen.openapi.models.SpecificationExtensionRenderer

object JsonSerdeLib extends Enumeration {
  val Circe, Jsoniter, Zio = Value
  type JsonSerdeLib = Value
}
object XmlSerdeLib extends Enumeration {
  val CatsXml, NoSupport = Value
  type XmlSerdeLib = Value
}
object StreamingImplementation extends Enumeration {
  val Akka, FS2, Pekko, Zio = Value
  type StreamingImplementation = Value
}

object BasicGenerator {

  val classGenerator = new ClassDefinitionGenerator()
  val endpointGenerator = new EndpointGenerator()

  def generateObjects(
      doc: OpenapiDocument,
      packagePath: String,
      objName: String,
      targetScala3: Boolean,
      useHeadTagForObjectNames: Boolean,
      jsonSerdeLib: String,
      xmlSerdeLib: String,
      streamingImplementation: String,
      validateNonDiscriminatedOneOfs: Boolean,
      maxSchemasPerFile: Int,
      generateEndpointTypes: Boolean
  ): Map[String, String] = {
    val normalisedJsonLib = jsonSerdeLib.toLowerCase match {
      case "circe"    => JsonSerdeLib.Circe
      case "jsoniter" => JsonSerdeLib.Jsoniter
      case "zio"      => JsonSerdeLib.Zio
      case _ =>
        System.err.println(
          s"!!! Unrecognised value $jsonSerdeLib for json serde lib -- should be one of circe, jsoniter, zio. Defaulting to circe !!!"
        )
        JsonSerdeLib.Circe
    }
    val normalisedXmlLib = xmlSerdeLib.toLowerCase match {
      case "cats-xml" => XmlSerdeLib.CatsXml
      case "none"     => XmlSerdeLib.NoSupport
      case _ =>
        System.err.println(
          s"!!! Unrecognised value $xmlSerdeLib for xml serde lib -- should be one of cats-xml, none. Defaulting to none !!!"
        )
        XmlSerdeLib.NoSupport
    }
    val normalisedStreamingImplementation = streamingImplementation.toLowerCase match {
      case "akka"  => StreamingImplementation.Akka
      case "fs2"   => StreamingImplementation.FS2
      case "pekko" => StreamingImplementation.Pekko
      case "zio"   => StreamingImplementation.Zio
      case _ =>
        System.err.println(
          s"!!! Unrecognised value $streamingImplementation for streaming impl -- should be one of akka, fs2, pekko or zio. Defaulting to fs2 !!!"
        )
        StreamingImplementation.FS2
    }

    val EndpointDefs(
      endpointsByTag,
      queryOrPathParamRefs,
      jsonParamRefs,
      enumsDefinedOnEndpointParams,
      inlineDefns,
      xmlParamRefs,
      securityWrappers
    ) =
      endpointGenerator.endpointDefs(
        doc,
        useHeadTagForObjectNames,
        targetScala3,
        normalisedJsonLib,
        normalisedXmlLib,
        normalisedStreamingImplementation,
        generateEndpointTypes
      )
    val GeneratedClassDefinitions(classDefns, jsonSerdes, schemas, xmlSerdes) =
      classGenerator
        .classDefs(
          doc = doc,
          targetScala3 = targetScala3,
          queryOrPathParamRefs = queryOrPathParamRefs,
          jsonSerdeLib = normalisedJsonLib,
          xmlSerdeLib = normalisedXmlLib,
          jsonParamRefs = jsonParamRefs,
          fullModelPath = s"$packagePath.$objName",
          validateNonDiscriminatedOneOfs = validateNonDiscriminatedOneOfs,
          maxSchemasPerFile = maxSchemasPerFile,
          enumsDefinedOnEndpointParams = enumsDefinedOnEndpointParams,
          xmlParamRefs = xmlParamRefs
        )
        .getOrElse(GeneratedClassDefinitions("", None, Nil, None))
    val hasJsonSerdes = jsonSerdes.nonEmpty
    val hasXmlSerdes = xmlSerdes.nonEmpty

    val maybeJsonImport = if (hasJsonSerdes) s"\nimport $packagePath.${objName}JsonSerdes._" else ""
    val maybeXmlImport = if (hasXmlSerdes) s"\nimport $packagePath.${objName}XmlSerdes._" else ""
    val maybeSchemaImport =
      if (schemas.size > 1) (1 to schemas.size).map(i => s"import ${objName}Schemas$i._").mkString("\n", "\n", "")
      else if (schemas.size == 1) s"\nimport ${objName}Schemas._"
      else ""
    val internalImports = s"import $packagePath.$objName._$maybeJsonImport$maybeXmlImport$maybeSchemaImport"

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

    val jsonSerdeObj = jsonSerdes.map { body =>
      s"""package $packagePath
         |
         |object ${objName}JsonSerdes {
         |  import $packagePath.$objName._
         |  import sttp.tapir.generic.auto._
         |${indent(2)(body)}
         |}""".stripMargin
    }

    val xmlSerdeObj = xmlSerdes.map(XmlSerdeGenerator.wrapBody(normalisedXmlLib, packagePath, objName, targetScala3, _))

    val schemaObjs = if (schemas.size > 1) schemas.zipWithIndex.map { case (body, idx) =>
      val priorImports = (0 until idx).map { i => s"import $packagePath.${objName}Schemas${i + 1}._" }.mkString("\n")
      val name = s"${objName}Schemas${idx + 1}"
      name -> s"""package $packagePath
         |
         |object $name {
         |  import $packagePath.$objName._
         |  import sttp.tapir.generic.auto._
         |${indent(2)(priorImports)}
         |${indent(2)(body)}
         |}""".stripMargin
    }
    else if (schemas.size == 1)
      Seq(s"${objName}Schemas" -> s"""package $packagePath
         |
         |object ${objName}Schemas {
         |  import $packagePath.$objName._
         |  import sttp.tapir.generic.auto._
         |${indent(2)(schemas.head)}
         |}""".stripMargin)
    else Nil

    val endpointsInMain = endpointsByTag.getOrElse(None, "")

    val maybeSpecificationExtensionKeys = doc.paths
      .flatMap { p =>
        p.specificationExtensions.toSeq ++ p.methods.flatMap(_.specificationExtensions.toSeq)
      }
      .groupBy(_._1)
      .map { case (keyName, pairs) =>
        val values = pairs.map(_._2)
        val `type` = SpecificationExtensionRenderer.renderCombinedType(values)
        val name = strippedToCamelCase(keyName)
        val uncapitalisedName = uncapitalise(name)
        val capitalisedName = uncapitalisedName.capitalize
        s"""type ${capitalisedName}Extension = ${`type`}
           |val ${uncapitalisedName}ExtensionKey = new sttp.tapir.AttributeKey[${capitalisedName}Extension]("$packagePath.$objName.${capitalisedName}Extension")
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
    val extraImports = if (endpointsInMain.nonEmpty) s"$maybeJsonImport$maybeXmlImport$maybeSchemaImport" else ""
    val queryParamSupport =
      """
      |case class CommaSeparatedValues[T](values: List[T])
      |case class ExplodedValues[T](values: List[T])
      |trait ExtraParamSupport[T] {
      |  def decode(s: String): sttp.tapir.DecodeResult[T]
      |  def encode(t: T): String
      |}
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
      |    .mapDecode(values => DecodeResult.sequence(values.split(',').toSeq.map(e => support.rawDecode(List(e)))).map(s => CommaSeparatedValues(s.toList)))(_.values.map(support.encode).mkString(","))
      |}
      |implicit def makeUnexplodedQueryOptSeqCodecFromListHead[T](implicit support: sttp.tapir.Codec[List[String], T, sttp.tapir.CodecFormat.TextPlain]): sttp.tapir.Codec[List[String], Option[CommaSeparatedValues[T]], sttp.tapir.CodecFormat.TextPlain] = {
      |  sttp.tapir.Codec.listHeadOption[String, String, sttp.tapir.CodecFormat.TextPlain]
      |    .mapDecode{
      |      case None => DecodeResult.Value(None)
      |      case Some(values) => DecodeResult.sequence(values.split(',').toSeq.map(e => support.rawDecode(List(e)))).map(r => Some(CommaSeparatedValues(r.toList)))
      |    }(_.map(_.values.map(support.encode).mkString(",")))
      |}
      |implicit def makeExplodedQuerySeqCodecFromListSeq[T](implicit support: sttp.tapir.Codec[List[String], List[T], sttp.tapir.CodecFormat.TextPlain]): sttp.tapir.Codec[List[String], ExplodedValues[T], sttp.tapir.CodecFormat.TextPlain] = {
      |  support.mapDecode(l => DecodeResult.Value(ExplodedValues(l)))(_.values)
      |}
      |""".stripMargin

    val securityTypes = SecurityGenerator.genSecurityTypes(securityWrappers)
    val mainObj = s"""
        |package $packagePath
        |
        |object $objName {
        |
        |${indent(2)(imports(normalisedJsonLib) + extraImports)}
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
        |
        |${indent(2)(classDefns)}
        |${indent(2)(inlineDefns.mkString("\n"))}
        |
        |${indent(2)(maybeSpecificationExtensionKeys)}
        |
        |${indent(2)(endpointsInMain)}
        |${indent(2)(ServersGenerator.genServerDefinitions(doc.servers, targetScala3).getOrElse(""))}
        |}
        |""".stripMargin
    taggedObjs ++ jsonSerdeObj.map(s"${objName}JsonSerdes" -> _) ++ xmlSerdeObj.map(s"${objName}XmlSerdes" -> _) ++
      schemaObjs + (objName -> mainObj)
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

  def indent(i: Int)(str: String): String = {
    str.linesIterator.map(" " * i + _).mkString("\n")
  }

  def mapSchemaSimpleTypeToType(osst: OpenapiSchemaSimpleType, multipartForm: Boolean = false): (String, Boolean) = {
    osst match {
      case OpenapiSchemaDouble(nb) =>
        ("Double", nb)
      case OpenapiSchemaFloat(nb) =>
        ("Float", nb)
      case OpenapiSchemaInt(nb) =>
        ("Int", nb)
      case OpenapiSchemaLong(nb) =>
        ("Long", nb)
      case OpenapiSchemaDateTime(nb) =>
        ("java.time.Instant", nb)
      case OpenapiSchemaUUID(nb) =>
        ("java.util.UUID", nb)
      case OpenapiSchemaString(nb) =>
        ("String", nb)
      case OpenapiSchemaBoolean(nb) =>
        ("Boolean", nb)
      case OpenapiSchemaBinary(nb) if multipartForm =>
        ("sttp.model.Part[java.io.File]", nb)
      case OpenapiSchemaBinary(nb) =>
        ("Array[Byte]", nb)
      case OpenapiSchemaAny(nb) =>
        ("io.circe.Json", nb)
      case OpenapiSchemaRef(t) =>
        (t.split('/').last, false)
      case x => throw new NotImplementedError(s"Not all simple types supported! Found $x")
    }
  }

  def strippedToCamelCase(string: String): String = string
    .split("[^0-9a-zA-Z$_]")
    .filter(_.nonEmpty)
    .zipWithIndex
    .map { case (part, 0) => part; case (part, _) => part.capitalize }
    .mkString

  def uncapitalise(name: String): String = name.head.toLower +: name.tail
}
