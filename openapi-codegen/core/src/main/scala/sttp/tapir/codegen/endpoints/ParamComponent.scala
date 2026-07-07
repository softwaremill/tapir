package sttp.tapir.codegen.endpoints

import sttp.tapir.codegen.endpoints.SimpleTypes.mapSchemaSimpleTypeToType
import sttp.tapir.codegen.json.JsonSerdeLib.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels.{OpenapiDocument, OpenapiParameter}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaArray, OpenapiSchemaEnum, OpenapiSchemaSimpleType}
import sttp.tapir.codegen.util.ErrUtils.bail
import sttp.tapir.codegen.util.NameHelpers.strippedToCamelCase
import sttp.tapir.codegen.util.{JavaEscape, Location}
import sttp.tapir.codegen.validation.ValidationGenerator

object ParamComponent {

  private def toOutType(baseType: String, isArray: Boolean, noOptionWrapper: Boolean) = (isArray, noOptionWrapper) match {
    case (true, true)   => s"List[$baseType]"
    case (true, false)  => s"Option[List[$baseType]]"
    case (false, true)  => baseType
    case (false, false) => s"Option[$baseType]"
  }

  private[endpoints] def getEnumParamDefn(
      endpointName: String,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib,
      param: OpenapiParameter,
      e: OpenapiSchemaEnum,
      isArray: Boolean
  ): (String, Some[Seq[String]], String, String) = {
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
    (s"""${param.in}[$req]("${param.name}")$mapToList$desc""", Some(enumDefn), outType, enumName)
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
  ): (String, Option[Seq[String]], String) =
    param.schema match {
      case st: OpenapiSchemaSimpleType =>
        val (t, _) = mapSchemaSimpleTypeToType(st)
        val required = param.required.getOrElse(false)
        val req = if (required) t else s"Option[$t]"
        val desc = param.description.map(JavaEscape.escapeString).fold("")(d => s""".description("$d")""")
        val validation = if (generateValidators) ValidationGenerator.mkValidations(doc, st, required) else ""
        (s"""${param.in}[$req]("${param.name}")$validation$desc""", None, req)
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
        (s"""${param.in}[$req]("${param.name}")$mapToList$desc""", None, outType)
      case e @ OpenapiSchemaEnum(_, _, _) =>
        getEnumParamDefn(endpointName, targetScala3, jsonSerdeLib, param, e, isArray = false) match {
          case (a, b, c, _) => (a, b, c)
        }
      case OpenapiSchemaArray(e: OpenapiSchemaEnum, _, _, _) =>
        getEnumParamDefn(endpointName, targetScala3, jsonSerdeLib, param, e, isArray = true) match {
          case (a, b, c, _) => (a, b, c)
        }
      case x => bail(s"Can't create non-simple params - found $x")
    }
}
