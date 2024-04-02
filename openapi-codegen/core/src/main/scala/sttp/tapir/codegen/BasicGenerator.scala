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
  val Circe, Jsoniter = Value
  type JsonSerdeLib = Value
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
      validateNonDiscriminatedOneOfs: Boolean
  ): Map[String, String] = {
    val normalisedJsonLib = jsonSerdeLib.toLowerCase match {
      case "circe"    => JsonSerdeLib.Circe
      case "jsoniter" => JsonSerdeLib.Jsoniter
      case _ =>
        System.err.println(
          s"!!! Unrecognised value $jsonSerdeLib for json serde lib -- should be one of circe, jsoniter. Defaulting to circe !!!"
        )
        JsonSerdeLib.Circe
    }

    val EndpointDefs(endpointsByTag, queryParamRefs, jsonParamRefs) = endpointGenerator.endpointDefs(doc, useHeadTagForObjectNames)
    val GeneratedClassDefinitions(classDefns, extras) =
      classGenerator
        .classDefs(
          doc = doc,
          targetScala3 = targetScala3,
          queryParamRefs = queryParamRefs,
          jsonSerdeLib = normalisedJsonLib,
          jsonParamRefs = jsonParamRefs,
          fullModelPath = s"$packagePath.$objName",
          validateNonDiscriminatedOneOfs = validateNonDiscriminatedOneOfs
        )
        .getOrElse(GeneratedClassDefinitions("", None))
    val isSplit = extras.nonEmpty
    val internalImports =
      if (isSplit)
        s"""import $packagePath.$objName._
         |import ${objName}JsonSerdes._""".stripMargin
      else s"import $objName._"
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
    val extraObj = extras.map { body =>
      s"""package $packagePath
         |
         |object ${objName}JsonSerdes {
         |  import $packagePath.$objName._
         |${indent(2)(body)}
         |}""".stripMargin
    }
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
        val uncapitalisedName = name.head.toLower + name.tail
        val capitalisedName = name.head.toUpper + name.tail
        s"""type ${capitalisedName}Extension = ${`type`}
           |val ${uncapitalisedName}ExtensionKey = new sttp.tapir.AttributeKey[${capitalisedName}Extension]("$packagePath.$objName.${capitalisedName}Extension")
           |""".stripMargin
      }
      .mkString("\n")

    val serdeImport = if (isSplit && endpointsInMain.nonEmpty) s"\nimport $packagePath.${objName}JsonSerdes._" else ""
    val mainObj = s"""|
        |package $packagePath
        |
        |object $objName {
        |
        |${indent(2)(imports(normalisedJsonLib) + serdeImport)}
        |
        |${indent(2)(classDefns)}
        |
        |${indent(2)(maybeSpecificationExtensionKeys)}
        |
        |${indent(2)(endpointsInMain)}
        |
        |}
        |""".stripMargin
    taggedObjs ++ extraObj.map(s"${objName}JsonSerdes" -> _) + (objName -> mainObj)
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
}
