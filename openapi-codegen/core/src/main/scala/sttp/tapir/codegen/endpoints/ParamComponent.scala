package sttp.tapir.codegen.endpoints

import sttp.tapir.codegen.endpoints.SimpleTypes.mapSchemaSimpleTypeToType
import sttp.tapir.codegen.json.JsonSerdeLib.JsonSerdeLib
import io.circe.Json
import sttp.tapir.codegen.openapi.models.{DefaultValueRenderer, OpenapiSchemaType, RenderConfig}
import sttp.tapir.codegen.openapi.models.OpenapiModels.{OpenapiDocument, OpenapiParameter}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaEnum,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType
}
import sttp.tapir.codegen.util.ErrUtils.bail
import sttp.tapir.codegen.util.NameHelpers.strippedToCamelCase
import sttp.tapir.codegen.util.{JavaEscape, Location}
import sttp.tapir.codegen.validation.ValidationGenerator

object ParamComponent {

  // Valid OpenAPI parameter locations. `param.in` is emitted as a raw method name (e.g. `query[...](...)`),
  // so an unvalidated value could inject arbitrary code; reject anything outside this allow-list.
  // See GHSA-gpcc-36pq-8qxr.
  private val validParamLocations = Set("query", "header", "path", "cookie")
  private def checkParamLocation(param: OpenapiParameter): Unit =
    if (!validParamLocations.contains(param.in))
      throw new IllegalArgumentException(
        s"Unsupported parameter location 'in': '${param.in}' (parameter '${param.name}') (see GHSA-gpcc-36pq-8qxr)"
      )

  private def toOutType(baseType: String, isArray: Boolean, noOptionWrapper: Boolean) = (isArray, noOptionWrapper) match {
    case (true, true)   => s"List[$baseType]"
    case (true, false)  => s"Option[List[$baseType]]"
    case (false, true)  => baseType
    case (false, false) => s"Option[$baseType]"
  }

  private def renderDefault(
      doc: OpenapiDocument,
      tpe: OpenapiSchemaType,
      isOptional: Boolean,
      config: RenderConfig,
      default: Option[Json]
  ): String = default.fold("") { d =>
    val allSchemas = doc.components.map(_.schemas).getOrElse(Map.empty)
    s".default(${DefaultValueRenderer.render(allSchemas, tpe, isOptional, config)(d)})"
  }

  private[endpoints] def getEnumParamDefn(
      endpointName: String,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib,
      param: OpenapiParameter,
      e: OpenapiSchemaEnum,
      isArray: Boolean,
      doc: OpenapiDocument
  ): (String, Some[Seq[String]], String, String) = {
    checkParamLocation(param)
    val enumName = endpointName.capitalize + strippedToCamelCase(param.name).capitalize
    val enumParamRefs = if (param.in == "query" || param.in == "path") Set(enumName) else Set.empty[String]
    val enumDefn = EnumGenerator.generateEnum(
      enumName,
      e,
      targetScala3,
      enumParamRefs,
      jsonSerdeLib,
      Set.empty,
      false
    )

    def arrayType = if (param.isExploded) "ExplodedValues" else "CommaSeparatedValues"

    val tpe = if (isArray) s"$arrayType[$enumName]" else enumName
    val required = param.required.getOrElse(false)
    // 'exploded' params have no distinction between an empty list and an absent value, so don't wrap in 'Option' for them
    val noOptionWrapper = required || (isArray && param.isExploded)
    val req = if (noOptionWrapper) tpe else s"Option[$tpe]"
    val outType = toOutType(enumName, isArray, noOptionWrapper)

    def mapToList =
      if (!isArray) "" else if (noOptionWrapper) s".map(_.values)($arrayType(_))" else s".map(_.map(_.values))(_.map($arrayType(_)))"

    val desc = param.description.map(d => JavaEscape.escapeString(d)).fold("")(d => s""".description("$d")""")
    // path params have no defaults; defaults of inline enum arrays would need wrapping in CommaSeparatedValues / ExplodedValues
    val default =
      if (isArray || param.in == "path") "" else renderDefault(doc, e, !required, RenderConfig(Some(enumName)), param.schema.default)
    (s"""${param.in}[$req]("${JavaEscape.escapeString(param.name)}")$mapToList$desc$default""", Some(enumDefn), outType, enumName)
  }
  private[endpoints] def genParamDefn(
      endpointName: String,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib,
      param: OpenapiParameter,
      doc: OpenapiDocument,
      generateValidators: Boolean
  )(implicit
      location: Location
  ): (String, Option[Seq[String]], String) = {
    checkParamLocation(param)
    param.schema.`type` match {
      case st: OpenapiSchemaSimpleType =>
        val (t, _) = mapSchemaSimpleTypeToType(st)
        val required = param.required.getOrElse(false)
        val req = if (required) t else s"Option[$t]"
        val desc = param.description.map(JavaEscape.escapeString).fold("")(d => s""".description("$d")""")
        val defaultValue = st match {
          case ref: OpenapiSchemaRef => param.schema.default.orElse(ref.maybeResolved(doc).flatMap(_.default))
          case _                     => param.schema.default
        }
        val default = renderDefault(doc, st, !required, RenderConfig(), defaultValue)
        val validation = if (generateValidators) ValidationGenerator.mkValidations(doc, st, required) else ""
        (s"""${param.in}[$req]("${JavaEscape.escapeString(param.name)}")$validation$desc$default""", None, req)
      case OpenapiSchemaArray(st: OpenapiSchemaSimpleType, _, _, _) =>
        val (t, _) = mapSchemaSimpleTypeToType(st)
        val arrayType = if (param.isExploded) "ExplodedValues" else "CommaSeparatedValues"
        val arr = s"$arrayType[$t]"
        val required = param.required.getOrElse(false)
        // 'exploded' params have no distinction between an empty list and an absent value, so don't wrap in 'Option' for them
        val noOptionWrapper = required || param.isExploded
        val req = if (noOptionWrapper) arr else s"Option[$arr]"

        def mapToList = if (noOptionWrapper) s".map(_.values)($arrayType(_))" else s".map(_.map(_.values))(_.map($arrayType(_)))"

        val desc = param.description.map(JavaEscape.escapeString).fold("")(d => s""".description("$d")""")
        val outType = toOutType(t, true, noOptionWrapper)
        (s"""${param.in}[$req]("${JavaEscape.escapeString(param.name)}")$mapToList$desc""", None, outType)
      case e @ OpenapiSchemaEnum(_, _, _) =>
        getEnumParamDefn(endpointName, targetScala3, jsonSerdeLib, param, e, isArray = false, doc) match {
          case (a, b, c, _) => (a, b, c)
        }
      case OpenapiSchemaArray(e: OpenapiSchemaEnum, _, _, _) =>
        getEnumParamDefn(endpointName, targetScala3, jsonSerdeLib, param, e, isArray = true, doc) match {
          case (a, b, c, _) => (a, b, c)
        }
      case x => bail(s"Can't create non-simple params - found $x")
    }
  }
}
