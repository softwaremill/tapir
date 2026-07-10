package sttp.tapir.codegen.json

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaAllOf,
  OpenapiSchemaAny,
  OpenapiSchemaArray,
  OpenapiSchemaBinary,
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

import scala.annotation.tailrec

object JsonSerdeLib extends Enumeration {
  val Circe, Jsoniter, Zio = Value
  type JsonSerdeLib = Value
}

case class SerdeGenResponse(
    fileDefn: Option[String],
    explicitNonObjTypes: Seq[String] // needed for jsoniter serdes with inheritance
)

object JsonSerdeGenerator {
  def serdeDefs(
      doc: OpenapiDocument,
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      jsonParamRefs: Set[String],
      allTransitiveJsonParamRefs: Set[String],
      validateNonDiscriminatedOneOfs: Boolean,
      adtInheritanceMap: Map[String, Seq[String]],
      targetScala3: Boolean,
      schemasContainAny: Boolean,
      useCustomJsoniterSerdes: Boolean,
      packageReuse: PackageReuseContext,
      resolvableNonClassyOneOfSchemas: Seq[(String, OpenapiSchemaOneOf)],
      seperateFilesForModels: Boolean
  ): SerdeGenResponse = {
    val allSchemas: Map[String, OpenapiSchemaType] = doc.components.toSeq.flatMap(_.schemas).toMap

    jsonSerdeLib match {
      case JsonSerdeLib.Circe =>
        CirceSerdeImpl.genCirceSerdes(
          doc,
          allSchemas,
          allTransitiveJsonParamRefs,
          validateNonDiscriminatedOneOfs,
          packageReuse,
          seperateFilesForModels
        )
      case JsonSerdeLib.Jsoniter =>
        JsoniterSerdeImpl.genJsoniterSerdes(
          doc,
          allSchemas,
          jsonParamRefs,
          allTransitiveJsonParamRefs,
          adtInheritanceMap,
          validateNonDiscriminatedOneOfs,
          schemasContainAny,
          useCustomJsoniterSerdes,
          packageReuse,
          resolvableNonClassyOneOfSchemas
        )
      case JsonSerdeLib.Zio =>
        ZioSerdeImpl.genZioSerdes(doc, allSchemas, allTransitiveJsonParamRefs, validateNonDiscriminatedOneOfs, targetScala3, packageReuse)
    }
  }

}
