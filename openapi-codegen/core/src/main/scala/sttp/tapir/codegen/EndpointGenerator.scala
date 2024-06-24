package sttp.tapir.codegen
import io.circe.Json
import sttp.tapir.codegen.BasicGenerator.{indent, mapSchemaSimpleTypeToType, strippedToCamelCase}
import sttp.tapir.codegen.JsonSerdeLib.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels.{OpenapiDocument, OpenapiParameter, OpenapiPath, OpenapiRequestBody, OpenapiResponse}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaAny,
  OpenapiSchemaArray,
  OpenapiSchemaBinary,
  OpenapiSchemaEnum,
  OpenapiSchemaMap,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType
}
import sttp.tapir.codegen.openapi.models.{OpenapiComponent, OpenapiSchemaType, OpenapiSecuritySchemeType, SpecificationExtensionRenderer}
import sttp.tapir.codegen.util.JavaEscape

case class Location(path: String, method: String) {
  override def toString: String = s"${method.toUpperCase} ${path}"
}

case class GeneratedEndpoint(name: String, definition: String, maybeLocalEnums: Option[String])
case class GeneratedEndpointsForFile(maybeFileName: Option[String], generatedEndpoints: Seq[GeneratedEndpoint])

case class GeneratedEndpoints(
    namesAndParamsByFile: Seq[GeneratedEndpointsForFile],
    queryParamRefs: Set[String],
    jsonParamRefs: Set[String],
    definesEnumQueryParam: Boolean
) {
  def merge(that: GeneratedEndpoints): GeneratedEndpoints =
    GeneratedEndpoints(
      (namesAndParamsByFile ++ that.namesAndParamsByFile)
        .groupBy(_.maybeFileName)
        .map { case (fileName, endpoints) => GeneratedEndpointsForFile(fileName, endpoints.map(_.generatedEndpoints).reduce(_ ++ _)) }
        .toSeq,
      queryParamRefs ++ that.queryParamRefs,
      jsonParamRefs ++ that.jsonParamRefs,
      definesEnumQueryParam || that.definesEnumQueryParam
    )
}
case class EndpointDefs(
    endpointDecls: Map[Option[String], String],
    queryParamRefs: Set[String],
    jsonParamRefs: Set[String],
    enumsDefinedOnEndpointParams: Boolean
)

class EndpointGenerator {
  private def bail(msg: String)(implicit location: Location): Nothing = throw new NotImplementedError(s"$msg at $location")

  private[codegen] def allEndpoints: String = "generatedEndpoints"

  def endpointDefs(
      doc: OpenapiDocument,
      useHeadTagForObjectNames: Boolean,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib
  ): EndpointDefs = {
    val components = Option(doc.components).flatten
    val GeneratedEndpoints(endpointsByFile, queryParamRefs, jsonParamRefs, definesEnumQueryParam) =
      doc.paths
        .map(generatedEndpoints(components, useHeadTagForObjectNames, targetScala3, jsonSerdeLib))
        .foldLeft(GeneratedEndpoints(Nil, Set.empty, Set.empty, false))(_ merge _)
    val endpointDecls = endpointsByFile.map { case GeneratedEndpointsForFile(k, ge) =>
      val definitions = ge
        .map { case GeneratedEndpoint(name, definition, maybeEnums) =>
          s"""lazy val $name =
             |${indent(2)(definition)}${maybeEnums.fold("")("\n" + _)}
             |""".stripMargin
        }
        .mkString("\n")
      val allEP = s"lazy val $allEndpoints = List(${ge.map(_.name).mkString(", ")})"

      k -> s"""|$definitions
          |
          |$allEP
          |""".stripMargin
    }.toMap
    EndpointDefs(endpointDecls, queryParamRefs, jsonParamRefs, definesEnumQueryParam)
  }

  private[codegen] def generatedEndpoints(
      components: Option[OpenapiComponent],
      useHeadTagForObjectNames: Boolean,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib
  )(p: OpenapiPath): GeneratedEndpoints = {
    val parameters = components.map(_.parameters).getOrElse(Map.empty)
    val securitySchemes = components.map(_.securitySchemes).getOrElse(Map.empty)

    val (fileNamesAndParams, unflattenedParamRefs, definesParams) = p.methods
      .map(_.withResolvedParentParameters(parameters, p.parameters))
      .map { m =>
        implicit val location: Location = Location(p.url, m.methodType)

        val attributeString = {
          val pathAttributes = attributes(p.specificationExtensions)
          val operationAttributes = attributes(m.specificationExtensions)
          (pathAttributes, operationAttributes) match {
            case (None, None)                          => ""
            case (Some(atts), None)                    => indent(2)(atts)
            case (None, Some(atts))                    => indent(2)(atts)
            case (Some(pathAtts), Some(operationAtts)) => indent(2)(pathAtts + "\n" + operationAtts)
          }
        }

        val name = strippedToCamelCase(m.operationId.getOrElse(m.methodType + p.url.capitalize))
        val (inParams, maybeLocalEnums) = ins(m.resolvedParameters, m.requestBody, name, targetScala3, jsonSerdeLib)
        val definition =
          s"""|endpoint
              |  .${m.methodType}
              |  ${urlMapper(p.url, m.resolvedParameters)}
              |${indent(2)(security(securitySchemes, m.security))}
              |${indent(2)(inParams)}
              |${indent(2)(outs(m.responses))}
              |${indent(2)(tags(m.tags))}
              |$attributeString
              |""".stripMargin.linesIterator.filterNot(_.trim.isEmpty).mkString("\n")

        val maybeTargetFileName = if (useHeadTagForObjectNames) m.tags.flatMap(_.headOption) else None
        val queryParamRefs = m.resolvedParameters
          .collect { case queryParam: OpenapiParameter if queryParam.in == "query" => queryParam.schema }
          .collect { case ref: OpenapiSchemaRef if ref.isSchema => ref.stripped }
          .toSet
        val jsonParamRefs = (m.requestBody.toSeq.flatMap(_.content.map(c => (c.contentType, c.schema))) ++
          m.responses.flatMap(_.content.map(c => (c.contentType, c.schema))))
          .collect { case (contentType, schema) if contentType == "application/json" => schema }
          .collect {
            case ref: OpenapiSchemaRef if ref.isSchema                        => ref.stripped
            case OpenapiSchemaArray(ref: OpenapiSchemaRef, _) if ref.isSchema => ref.stripped
            case OpenapiSchemaArray(OpenapiSchemaAny(_), _) =>
              bail("Cannot generate schema for 'Any' with jsoniter library")
            case OpenapiSchemaArray(simple: OpenapiSchemaSimpleType, _) =>
              val name = BasicGenerator.mapSchemaSimpleTypeToType(simple)._1
              s"List[$name]"
            case simple: OpenapiSchemaSimpleType =>
              BasicGenerator.mapSchemaSimpleTypeToType(simple)._1
            case OpenapiSchemaMap(simple: OpenapiSchemaSimpleType, _) =>
              val name = BasicGenerator.mapSchemaSimpleTypeToType(simple)._1
              s"Map[String, $name]"
          }
          .toSet
        (
          (maybeTargetFileName, GeneratedEndpoint(name, definition, maybeLocalEnums)),
          (queryParamRefs, jsonParamRefs),
          maybeLocalEnums.isDefined
        )
      }
      .unzip3
    val (unflattenedQueryParamRefs, unflattenedJsonParamRefs) = unflattenedParamRefs.unzip
    val namesAndParamsByFile = fileNamesAndParams
      .groupBy(_._1)
      .toSeq
      .map { case (maybeTargetFileName, defns) => GeneratedEndpointsForFile(maybeTargetFileName, defns.map(_._2)) }
    GeneratedEndpoints(
      namesAndParamsByFile,
      unflattenedQueryParamRefs.foldLeft(Set.empty[String])(_ ++ _),
      unflattenedJsonParamRefs.foldLeft(Set.empty[String])(_ ++ _),
      definesParams.contains(true)
    )
  }

  private def urlMapper(url: String, parameters: Seq[OpenapiParameter])(implicit location: Location): String = {
    // .in(("books" / path[String]("genre") / path[Int]("year")).mapTo[BooksFromYear])
    val inPath = url.split('/').filter(_.nonEmpty) map { segment =>
      if (segment.startsWith("{")) {
        val name = segment.drop(1).dropRight(1)
        val param = parameters.find(_.name == name)
        param.fold(bail(s"URLParam $name not found!")) { p =>
          p.schema match {
            case st: OpenapiSchemaSimpleType =>
              val (t, _) = mapSchemaSimpleTypeToType(st)
              val desc = p.description.fold("")(d => s""".description("$d")""")
              s"""path[$t]("$name")$desc"""
            case _ => bail("Can't create non-simple params to url yet")
          }
        }
      } else {
        '"' + segment + '"'
      }
    }
    ".in((" + inPath.mkString(" / ") + "))"
  }

  private def security(securitySchemes: Map[String, OpenapiSecuritySchemeType], security: Seq[Seq[String]])(implicit location: Location) = {
    if (security.size > 1 || security.exists(_.size > 1))
      bail("We can handle only single security entry!")

    security.headOption
      .flatMap(_.headOption)
      .fold("") { schemeName =>
        securitySchemes.get(schemeName) match {
          case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeBearerType) =>
            ".securityIn(auth.bearer[String]())"

          case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeBasicType) =>
            ".securityIn(auth.basic[UsernamePassword]())"

          case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeApiKeyType(in, name)) =>
            s""".securityIn(auth.apiKey($in[String]("$name")))"""

          case None =>
            bail(s"Unknown security scheme $schemeName!")
        }
      }
  }

  private def ins(
      parameters: Seq[OpenapiParameter],
      requestBody: Option[OpenapiRequestBody],
      endpointName: String,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib
  )(implicit location: Location): (String, Option[String]) = {
    def getEnumParamDefn(param: OpenapiParameter, e: OpenapiSchemaEnum, isArray: Boolean) = {
      val enumName = endpointName.capitalize + strippedToCamelCase(param.name).capitalize
      val queryParamRefs = if (param.in == "query") Set(enumName) else Set.empty[String]
      val enumDefn = EnumGenerator.generateEnum(
        enumName,
        e,
        targetScala3,
        queryParamRefs,
        jsonSerdeLib,
        Set.empty
      )
      def arrayType = if (param.isExploded) "ExplodedValues" else "CommaSeparatedValues"
      val tpe = if (isArray) s"$arrayType[$enumName]" else enumName
      val required = param.required.getOrElse(true)
      // 'exploded' params have no distinction between an empty list and an absent value, so don't wrap in 'Option' for them
      val noOptionWrapper = required || (isArray && param.isExploded)
      val req = if (noOptionWrapper) tpe else s"Option[$tpe]"
      def mapToList =
        if (!isArray) "" else if (noOptionWrapper) s".map(_.values)($arrayType(_))" else s".map(_.map(_.values))(_.map($arrayType(_)))"
      val desc = param.description.map(d => JavaEscape.escapeString(d)).fold("")(d => s""".description("$d")""")
      s""".in(${param.in}[$req]("${param.name}")$mapToList$desc)""" -> Some(enumDefn)
    }
    // .in(query[Limit]("limit").description("Maximum number of books to retrieve"))
    // .in(header[AuthToken]("X-Auth-Token"))
    val (params, maybeEnumDefns) = parameters
      .filter(_.in != "path")
      .map { param =>
        param.schema match {
          case st: OpenapiSchemaSimpleType =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            val req = if (param.required.getOrElse(true)) t else s"Option[$t]"
            val desc = param.description.map(d => JavaEscape.escapeString(d)).fold("")(d => s""".description("$d")""")
            s""".in(${param.in}[$req]("${param.name}")$desc)""" -> None
          case OpenapiSchemaArray(st: OpenapiSchemaSimpleType, _) =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            val arrayType = if (param.isExploded) "ExplodedValues" else "CommaSeparatedValues"
            val arr = s"$arrayType[$t]"
            val required = param.required.getOrElse(true)
            // 'exploded' params have no distinction between an empty list and an absent value, so don't wrap in 'Option' for them
            val noOptionWrapper = required || param.isExploded
            val req = if (noOptionWrapper) arr else s"Option[$arr]"
            def mapToList = if (noOptionWrapper) s".map(_.values)($arrayType(_))" else s".map(_.map(_.values))(_.map($arrayType(_)))"
            val desc = param.description.map(d => JavaEscape.escapeString(d)).fold("")(d => s""".description("$d")""")
            s""".in(${param.in}[$req]("${param.name}")$mapToList$desc)""" -> None
          case e @ OpenapiSchemaEnum(_, _, _)              => getEnumParamDefn(param, e, isArray = false)
          case OpenapiSchemaArray(e: OpenapiSchemaEnum, _) => getEnumParamDefn(param, e, isArray = true)
          case x                                           => bail(s"Can't create non-simple params to input - found $x")
        }
      }
      .unzip

    val rqBody = requestBody.flatMap { b =>
      if (b.content.isEmpty) None
      else if (b.content.size != 1) bail(s"We can handle only one requestBody content! Saw ${b.content.map(_.contentType)}")
      else Some(s".in(${contentTypeMapper(b.content.head.contentType, b.content.head.schema, b.required)})")
    }

    (params ++ rqBody).mkString("\n") -> maybeEnumDefns.foldLeft(Option.empty[String]) {
      case (acc, None)            => acc
      case (None, Some(nxt))      => Some(nxt.mkString("\n"))
      case (Some(acc), Some(nxt)) => Some(acc + "\n" + nxt.mkString("\n"))
    }
  }

  private def tags(openapiTags: Option[Seq[String]]): String = {
    // .tags(List("A", "B"))
    openapiTags.map(_.distinct.mkString(".tags(List(\"", "\", \"", "\"))")).mkString
  }

  private def attributes(atts: Map[String, Json]): Option[String] = if (atts.nonEmpty) Some {
    atts
      .map { case (k, v) =>
        val camelCaseK = strippedToCamelCase(k)
        val uncapitalisedName = camelCaseK.head.toLower + camelCaseK.tail
        s""".attribute[${camelCaseK.capitalize}Extension](${uncapitalisedName}ExtensionKey, ${SpecificationExtensionRenderer.renderValue(
            v
          )})"""
      }
      .mkString("\n")
  }
  else None

  // treats redirects as ok
  private val okStatus = """([23]\d\d)""".r
  private val errorStatus = """([45]\d\d)""".r
  private def outs(responses: Seq[OpenapiResponse])(implicit location: Location) = {
    // .errorOut(stringBody)
    // .out(jsonBody[List[Book]])

    responses
      .map { resp =>
        val d = s""".description("${JavaEscape.escapeString(resp.description)}")"""
        resp.content match {
          case Nil =>
            resp.code match {
              case "200" | "default" => ""
              case okStatus(s)       => s".out(statusCode(sttp.model.StatusCode($s))$d)"
              case errorStatus(s)    => s".errorOut(statusCode(sttp.model.StatusCode($s))$d)"
            }
          case content +: Nil =>
            resp.code match {
              case "200" =>
                s".out(${contentTypeMapper(content.contentType, content.schema)}$d)"
              case okStatus(s) =>
                s".out(${contentTypeMapper(content.contentType, content.schema)}$d.and(statusCode(sttp.model.StatusCode($s))))"
              case "default" =>
                s".errorOut(${contentTypeMapper(content.contentType, content.schema)}$d)"
              case errorStatus(s) =>
                s".errorOut(${contentTypeMapper(content.contentType, content.schema)}$d.and(statusCode(sttp.model.StatusCode($s))))"
              case x =>
                bail(s"Statuscode mapping is incomplete! Cannot handle $x")
            }
          case _ => bail("We can handle only one return content!")
        }
      }
      .sorted
      .filter(_.nonEmpty)
      .mkString("\n")
  }

  private def contentTypeMapper(contentType: String, schema: OpenapiSchemaType, required: Boolean = true)(implicit location: Location) = {
    contentType match {
      case "text/plain" =>
        "stringBody"
      case "application/json" =>
        val outT = schema match {
          case st: OpenapiSchemaSimpleType =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            t
          case OpenapiSchemaArray(st: OpenapiSchemaSimpleType, _) =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            s"List[$t]"
          case OpenapiSchemaMap(st: OpenapiSchemaSimpleType, _) =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            s"Map[String, $t]"
          case x => bail(s"Can't create non-simple or array params as output (found $x)")
        }
        val req = if (required) outT else s"Option[$outT]"
        s"jsonBody[$req]"

      case "multipart/form-data" =>
        schema match {
          case _: OpenapiSchemaBinary =>
            "multipartBody"
          case schemaRef: OpenapiSchemaRef =>
            val (t, _) = mapSchemaSimpleTypeToType(schemaRef, multipartForm = true)
            s"multipartBody[$t]"
          case x => bail(s"$contentType only supports schema ref or binary. Found $x")
        }

      case x => bail(s"Not all content types supported! Found $x")
    }
  }

}
