package sttp.tapir.codegen
import io.circe.Json
import sttp.tapir.codegen.BasicGenerator.{indent, mapSchemaSimpleTypeToType, strippedToCamelCase}
import sttp.tapir.codegen.JsonSerdeLib.JsonSerdeLib
import sttp.tapir.codegen.StreamingImplementation.StreamingImplementation
import sttp.tapir.codegen.XmlSerdeLib.XmlSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels.{
  OpenapiDocument,
  OpenapiParameter,
  OpenapiPath,
  OpenapiRequestBodyContent,
  OpenapiRequestBodyDefn,
  OpenapiResponseContent,
  OpenapiResponseDef
}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaAny,
  OpenapiSchemaArray,
  OpenapiSchemaBinary,
  OpenapiSchemaEnum,
  OpenapiSchemaMap,
  OpenapiSchemaObject,
  OpenapiSchemaOneOf,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType,
  OpenapiSchemaString
}
import sttp.tapir.codegen.openapi.models._
import sttp.tapir.codegen.openapi.models.OpenapiSecuritySchemeType.OAuth2FlowType
import sttp.tapir.codegen.util.JavaEscape

case class Location(path: String, method: String) {
  override def toString: String = s"${method.toUpperCase} ${path}"
}

case class EndpointTypes(security: Seq[String], in: Seq[String], err: Seq[String], out: Seq[String]) {
  private def toType(types: Seq[String]) = types match {
    case Nil      => "Unit"
    case t +: Nil => t
    case seq      => seq.mkString("(", ", ", ")")
  }
  def securityTypes = toType(security)
  def inTypes = toType(in)
  def errTypes = toType(err)
  def outTypes = toType(out)
}
case class GeneratedEndpoint(name: String, definition: String, maybeInlineDefns: Option[String], types: EndpointTypes)
case class GeneratedEndpointsForFile(maybeFileName: Option[String], generatedEndpoints: Seq[GeneratedEndpoint])

case class GeneratedEndpoints(
    namesAndParamsByFile: Seq[GeneratedEndpointsForFile],
    queryParamRefs: Set[String],
    jsonParamRefs: Set[String],
    definesEnumQueryParam: Boolean,
    inlineDefns: Seq[String],
    xmlParamRefs: Set[String]
) {
  def merge(that: GeneratedEndpoints): GeneratedEndpoints =
    GeneratedEndpoints(
      (namesAndParamsByFile ++ that.namesAndParamsByFile)
        .groupBy(_.maybeFileName)
        .map { case (fileName, endpoints) => GeneratedEndpointsForFile(fileName, endpoints.map(_.generatedEndpoints).reduce(_ ++ _)) }
        .toSeq,
      queryParamRefs ++ that.queryParamRefs,
      jsonParamRefs ++ that.jsonParamRefs,
      definesEnumQueryParam || that.definesEnumQueryParam,
      inlineDefns ++ that.inlineDefns,
      xmlParamRefs ++ that.xmlParamRefs
    )
}
case class EndpointDefs(
    endpointDecls: Map[Option[String], String],
    queryOrPathParamRefs: Set[String],
    jsonParamRefs: Set[String],
    enumsDefinedOnEndpointParams: Boolean,
    inlineDefns: Seq[String],
    xmlParamRefs: Set[String]
)

class EndpointGenerator {
  private def bail(msg: String)(implicit location: Location): Nothing = throw new NotImplementedError(s"$msg at $location")

  private def combine(inlineDefn1: Option[String], inlineDefn2: Option[String], separator: String = "\n") =
    (inlineDefn1, inlineDefn2) match {
      case (None, None)               => None
      case (Some(defn), None)         => Some(defn)
      case (None, Some(defn))         => Some(defn)
      case (Some(defn1), Some(defn2)) => Some(defn1 + separator + defn2)
    }
  private[codegen] def allEndpoints: String = "generatedEndpoints"

  private def capabilityImpl(streamingImplementation: StreamingImplementation): String = streamingImplementation match {
    case StreamingImplementation.Akka  => "sttp.capabilities.akka.AkkaStreams"
    case StreamingImplementation.FS2   => "sttp.capabilities.fs2.Fs2Streams[cats.effect.IO]"
    case StreamingImplementation.Pekko => "sttp.capabilities.pekko.PekkoStreams"
    case StreamingImplementation.Zio   => "sttp.capabilities.zio.ZioStreams"
  }
  private def capabilityType(streamingImplementation: StreamingImplementation): String = streamingImplementation match {
    case StreamingImplementation.FS2 => "fs2.Stream[cats.effect.IO, Byte]"
    case x                           => s"${capabilityImpl(x)}.BinaryStream"
  }

  def endpointDefs(
      doc: OpenapiDocument,
      useHeadTagForObjectNames: Boolean,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib,
      xmlSerdeLib: XmlSerdeLib,
      streamingImplementation: StreamingImplementation,
      generateEndpointTypes: Boolean
  ): EndpointDefs = {
    val capabilities = capabilityImpl(streamingImplementation)
    val components = Option(doc.components).flatten
    val GeneratedEndpoints(endpointsByFile, queryOrPathParamRefs, jsonParamRefs, definesEnumQueryParam, inlineDefns, xmlParamRefs) =
      doc.paths
        .map(
          generatedEndpoints(components, useHeadTagForObjectNames, targetScala3, jsonSerdeLib, xmlSerdeLib, streamingImplementation, doc)
        )
        .foldLeft(GeneratedEndpoints(Nil, Set.empty, Set.empty, false, Nil, Set.empty))(_ merge _)
    val endpointDecls = endpointsByFile.map { case GeneratedEndpointsForFile(k, ge) =>
      val definitions = ge
        .map { case GeneratedEndpoint(name, definition, maybeInlineDefns, types) =>
          val theCapabilities = if (definition.contains(".capabilities.")) capabilities else "Any"
          val endpointTypeDecl =
            if (generateEndpointTypes)
              s"type ${name.capitalize}Endpoint = Endpoint[${types.securityTypes}, ${types.inTypes}, ${types.errTypes}, ${types.outTypes}, $theCapabilities]\n"
            else ""

          val maybeType = if (generateEndpointTypes) s": ${name.capitalize}Endpoint" else ""
          s"""${endpointTypeDecl}lazy val $name$maybeType =
             |${indent(2)(definition)}${maybeInlineDefns.fold("")("\n" + _)}
             |""".stripMargin
        }
        .mkString("\n")
      val allEP = s"lazy val $allEndpoints = List(${ge.map(_.name).mkString(", ")})"

      k -> s"""|$definitions
          |
          |$allEP
          |""".stripMargin
    }.toMap
    EndpointDefs(endpointDecls, queryOrPathParamRefs, jsonParamRefs, definesEnumQueryParam, inlineDefns, xmlParamRefs)
  }

  private[codegen] def generatedEndpoints(
      components: Option[OpenapiComponent],
      useHeadTagForObjectNames: Boolean,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib,
      xmlSerdeLib: XmlSerdeLib,
      streamingImplementation: StreamingImplementation,
      doc: OpenapiDocument
  )(p: OpenapiPath): GeneratedEndpoints = {
    val parameters = components.map(_.parameters).getOrElse(Map.empty)
    val securitySchemes = components.map(_.securitySchemes).getOrElse(Map.empty)

    val (fileNamesAndParams, unflattenedParamRefs, inlineParamInfo) = p.methods
      .map(_.withResolvedParentParameters(parameters, p.parameters))
      .map { m =>
        implicit val location: Location = Location(p.url, m.methodType)
        try {
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

          val name = m.name(p.url)
          val (pathDecl, pathTypes) = urlMapper(p.url, m.resolvedParameters)
          val (securityDecl, securityTypes) = security(securitySchemes, m.security)
          val (inParams, maybeLocalEnums, inTypes, inlineInDefns) =
            ins(
              m.resolvedParameters,
              m.requestBody.map(_.resolve(doc)),
              name,
              targetScala3,
              jsonSerdeLib,
              xmlSerdeLib,
              streamingImplementation,
              doc
            )
          val (outDecl, outTypes, errTypes, inlineDefns) =
            outs(m.responses.map(_.resolve(doc)), streamingImplementation, doc, targetScala3, name, jsonSerdeLib, xmlSerdeLib)
          val allTypes = EndpointTypes(securityTypes.toSeq, pathTypes ++ inTypes, errTypes.toSeq, outTypes.toSeq)
          val inlineDefn = combine(inlineInDefns, inlineDefns)
          val definition =
            s"""|endpoint
              |  .${m.methodType}
              |  $pathDecl
              |${indent(2)(securityDecl)}
              |${indent(2)(inParams)}
              |${indent(2)(outDecl)}
              |${indent(2)(tags(m.tags))}
              |$attributeString
              |""".stripMargin.linesIterator.filterNot(_.trim.isEmpty).mkString("\n")

          val maybeTargetFileName = if (useHeadTagForObjectNames) m.tags.flatMap(_.headOption) else None
          val queryOrPathParamRefs = m.resolvedParameters
            .collect { case queryParam: OpenapiParameter if queryParam.in == "query" || queryParam.in == "path" => queryParam.schema }
            .collect {
              case ref: OpenapiSchemaRef if ref.isSchema                           => ref.stripped
              case OpenapiSchemaArray(ref: OpenapiSchemaRef, _, _) if ref.isSchema => ref.stripped
            }
            .toSet
          val xmlParamRefs: Seq[String] = (m.requestBody.toSeq.flatMap(_.resolve(doc).content.map(c => (c.contentType, c.schema))) ++
            m.responses.flatMap(_.resolve(doc).content.map(c => (c.contentType, c.schema))))
            .collect { case (contentType, schema) if contentType == "application/xml" => schema }
            .collect { case ref: OpenapiSchemaRef if ref.isSchema => ref.stripped }
          val jsonParamRefs = (m.requestBody.toSeq.flatMap(_.resolve(doc).content.map(c => (c.contentType, c.schema))) ++
            m.responses.flatMap(_.resolve(doc).content.map(c => (c.contentType, c.schema))))
            .collect { case (contentType, schema) if contentType == "application/json" => schema }
            .collect {
              case ref: OpenapiSchemaRef if ref.isSchema                           => ref.stripped
              case OpenapiSchemaArray(ref: OpenapiSchemaRef, _, _) if ref.isSchema => ref.stripped
              case OpenapiSchemaArray(OpenapiSchemaAny(_), _, _) =>
                bail("Cannot generate schema for 'Any' with jsoniter library")
              case OpenapiSchemaArray(simple: OpenapiSchemaSimpleType, _, _) =>
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
            (maybeTargetFileName, GeneratedEndpoint(name, definition, maybeLocalEnums, allTypes)),
            (queryOrPathParamRefs, jsonParamRefs, xmlParamRefs),
            (maybeLocalEnums.isDefined, inlineDefn)
          )
        } catch {
          case e: NotImplementedError => throw e
          case e: Throwable           => bail(s"Unexpected error (${e.getMessage})")
        }
      }
      .unzip3
    val (unflattenedQueryParamRefs, unflattenedJsonParamRefs, xmlParamRefs) = unflattenedParamRefs.unzip3
    val namesAndParamsByFile = fileNamesAndParams
      .groupBy(_._1)
      .toSeq
      .map { case (maybeTargetFileName, defns) => GeneratedEndpointsForFile(maybeTargetFileName, defns.map(_._2)) }
    val (definesParams, inlineDefns) = inlineParamInfo.unzip
    GeneratedEndpoints(
      namesAndParamsByFile,
      unflattenedQueryParamRefs.foldLeft(Set.empty[String])(_ ++ _),
      unflattenedJsonParamRefs.foldLeft(Set.empty[String])(_ ++ _),
      definesParams.contains(true),
      inlineDefns.flatten,
      xmlParamRefs.flatten.toSet
    )
  }

  private def urlMapper(url: String, parameters: Seq[OpenapiParameter])(implicit location: Location): (String, Seq[String]) = {
    // .in(("books" / path[String]("genre") / path[Int]("year")).mapTo[BooksFromYear])
    val (inPath, tpes) = url
      .split('/')
      .filter(_.nonEmpty)
      .map { segment =>
        if (segment.startsWith("{")) {
          val name = segment.drop(1).dropRight(1)
          val param = parameters.find(_.name == name)
          param.fold(bail(s"URLParam $name not found!")) { p =>
            p.schema match {
              case st: OpenapiSchemaSimpleType =>
                val (t, _) = mapSchemaSimpleTypeToType(st)
                val desc = p.description.fold("")(d => s""".description("$d")""")
                s"""path[$t]("$name")$desc""" -> Some(t)
              case _ => bail("Can't create non-simple params to url yet")
            }
          }
        } else {
          '"' + segment + '"' -> None
        }
      }
      .unzip
    ".in((" + inPath.mkString(" / ") + "))" -> tpes.toSeq.flatten
  }

  private def security(securitySchemes: Map[String, OpenapiSecuritySchemeType], security: Map[String, Seq[String]])(implicit
      location: Location
  ) = {

    // Would be nice to do something to respect scopes here
    val inner = security.flatMap { case (schemeName, _ /*scopes*/ ) =>
      securitySchemes.get(schemeName) match {
        case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeBearerType) =>
          Seq("auth.bearer[String]()" -> "Bearer")

        case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeBasicType) =>
          Seq("auth.basic[UsernamePassword]()" -> "Basic")

        case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeApiKeyType(in, name)) =>
          Seq(s"""auth.apiKey($in[String]("$name"))""" -> "ApiKey")

        case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeOAuth2Type(flows)) if flows.isEmpty => Nil
        case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeOAuth2Type(flows)) =>
          flows.map {
            case (OAuth2FlowType.password, _) =>
              "auth.bearer[String]()" -> "Bearer"
            case (OAuth2FlowType.`implicit`, f) =>
              val authUrl = f.authorizationUrl.getOrElse(bail("authorizationUrl required for implicit flow"))
              val refreshUrl = f.refreshUrl.map(u => s"""Some("$u")""").getOrElse("None")
              s"""auth.oauth2.implicitFlow("$authUrl", $refreshUrl)""" -> "Bearer"
            case (OAuth2FlowType.clientCredentials, f) =>
              val tokenUrl = f.tokenUrl.getOrElse(bail("tokenUrl required for clientCredentials flow"))
              val refreshUrl = f.refreshUrl.map(u => s"""Some("$u")""").getOrElse("None")
              s"""auth.oauth2.clientCredentialsFlow("$tokenUrl", $refreshUrl)""" -> "Bearer"
            case (OAuth2FlowType.authorizationCode, f) =>
              val authUrl = f.authorizationUrl.getOrElse(bail("authorizationUrl required for authorizationCode flow"))
              val tokenUrl = f.tokenUrl.getOrElse(bail("tokenUrl required for authorizationCode flow"))
              val refreshUrl = f.refreshUrl.map(u => s"""Some("$u")""").getOrElse("None")
              s"""auth.oauth2.authorizationCodeFlow("$authUrl", "$tokenUrl", $refreshUrl)""" -> "Bearer"
          }

        case None =>
          bail(s"Unknown security scheme $schemeName!")
      }
    }.toList
    inner.distinct match {
      case Nil                 => "" -> None
      case (h, "Basic") +: Nil => s".securityIn($h)" -> Some("UsernamePassword")
      case (h, _) +: Nil       => s".securityIn($h)" -> Some("String")
      case s =>
        s.map(_._2).distinct match {
          case h +: Nil =>
            h match {
              case "Bearer" => ".securityIn(auth.bearer[String]())" -> Some("String")
              case "Basic"  => ".securityIn(auth.basic[UsernamePassword]())" -> Some("UsernamePassword")
              case "ApiKey" => bail("Cannot support multiple api key authentication declarations on same endpoint")
            }
          case _ =>
            bail(
              "can only support multiple security declarations on the same endpoint when they resolve to the same input type (e.g. bearer auth header)"
            )
        }
    }
  }

  private def toOutType(baseType: String, isArray: Boolean, noOptionWrapper: Boolean) = (isArray, noOptionWrapper) match {
    case (true, true)   => s"List[$baseType]"
    case (true, false)  => s"Option[List[$baseType]]"
    case (false, true)  => baseType
    case (false, false) => s"Option[$baseType]"
  }

  private def getEnumParamDefn(
      endpointName: String,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib,
      param: OpenapiParameter,
      e: OpenapiSchemaEnum,
      isArray: Boolean
  ) = {
    val enumName = endpointName.capitalize + strippedToCamelCase(param.name).capitalize
    val enumParamRefs = if (param.in == "query" || param.in == "path") Set(enumName) else Set.empty[String]
    val enumDefn = EnumGenerator.generateEnum(
      enumName,
      e,
      targetScala3,
      enumParamRefs,
      jsonSerdeLib,
      Set.empty
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
    (s"""${param.in}[$req]("${param.name}")$mapToList$desc""", Some(enumDefn), outType)
  }

  private def genParamDefn(endpointName: String, targetScala3: Boolean, jsonSerdeLib: JsonSerdeLib, param: OpenapiParameter)(implicit
      location: Location
  ): (String, Option[Seq[String]], String) =
    param.schema match {
      case st: OpenapiSchemaSimpleType =>
        val (t, _) = mapSchemaSimpleTypeToType(st)
        val req = if (param.required.getOrElse(false)) t else s"Option[$t]"
        val desc = param.description.map(d => JavaEscape.escapeString(d)).fold("")(d => s""".description("$d")""")
        (s"""${param.in}[$req]("${param.name}")$desc""", None, req)
      case OpenapiSchemaArray(st: OpenapiSchemaSimpleType, _, _) =>
        val (t, _) = mapSchemaSimpleTypeToType(st)
        val arrayType = if (param.isExploded) "ExplodedValues" else "CommaSeparatedValues"
        val arr = s"$arrayType[$t]"
        val required = param.required.getOrElse(false)
        // 'exploded' params have no distinction between an empty list and an absent value, so don't wrap in 'Option' for them
        val noOptionWrapper = required || param.isExploded
        val req = if (noOptionWrapper) arr else s"Option[$arr]"

        def mapToList = if (noOptionWrapper) s".map(_.values)($arrayType(_))" else s".map(_.map(_.values))(_.map($arrayType(_)))"

        val desc = param.description.map(d => JavaEscape.escapeString(d)).fold("")(d => s""".description("$d")""")
        val outType = toOutType(t, true, noOptionWrapper)
        (s"""${param.in}[$req]("${param.name}")$mapToList$desc""", None, outType)
      case e @ OpenapiSchemaEnum(_, _, _) => getEnumParamDefn(endpointName, targetScala3, jsonSerdeLib, param, e, isArray = false)
      case OpenapiSchemaArray(e: OpenapiSchemaEnum, _, _) =>
        getEnumParamDefn(endpointName, targetScala3, jsonSerdeLib, param, e, isArray = true)
      case x => bail(s"Can't create non-simple params - found $x")
    }

  private def ins(
      parameters: Seq[OpenapiParameter],
      requestBody: Option[OpenapiRequestBodyDefn],
      endpointName: String,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib,
      xmlSerdeLib: XmlSerdeLib,
      streamingImplementation: StreamingImplementation,
      doc: OpenapiDocument
  )(implicit location: Location): (String, Option[String], Seq[String], Option[String]) = {

    // .in(query[Limit]("limit").description("Maximum number of books to retrieve"))
    // .in(header[AuthToken]("X-Auth-Token"))
    val (params, maybeEnumDefns, inTypes) = parameters
      .filter(_.in != "path")
      .map { param =>
        genParamDefn(endpointName, targetScala3, jsonSerdeLib, param)
      }
      .map { case (defn, enums, tpe) => (s".in($defn)", enums, tpe) }
      .unzip3

    def mapContent(content: OpenapiRequestBodyContent, required: Boolean, forceEager: Boolean = false) = {
      val schemaIsNullable = content.schema.nullable || (content.schema match {
        case ref: OpenapiSchemaRef =>
          doc.components.flatMap(_.schemas.get(ref.stripped).map(_.nullable)).contains(true)
        case _ => false
      })
      val MappedContentType(decl, tpe, maybeInlineDefn) =
        contentTypeMapper(
          content.contentType,
          content.schema,
          streamingImplementation,
          required && !schemaIsNullable,
          endpointName,
          "Request",
          forceEager,
          xmlSerdeLib
        )
      (decl, tpe, maybeInlineDefn)
    }
    val (rqBody, maybeReqType, maybeInlineDefns) = requestBody.flatMap { b =>
      if (b.content.isEmpty) None
      else if (b.content.size == 1) {
        val content: OpenapiModels.OpenapiRequestBodyContent = b.content.head
        val (decl, tpe, maybeInlineDefn) = mapContent(content, b.required)
        val d = b.description.map(s => s""".description("${JavaEscape.escapeString(s)}")""").getOrElse("")
        Some((s".in($decl$d)", tpe, maybeInlineDefn))
      } else {
        // These types all use 'eager' schemas; we cannot mix eager and streaming types when using oneOfBody
        val eagerTypes = Set("application/json", "application/xml", "text/plain", "text/html", "multipart/form-data")
        val forceEager = b.content.exists(c => eagerTypes.contains(c.contentType))
        val mapped = b.content.map(mapContent(_, b.required, forceEager))
        val (decls, tpes, maybeInlineDefns) = mapped.unzip3
        val distinctTypes = tpes.distinct
        // If the types are distinct, we need to produce wrappers with a common parent for oneOfBody to work. If they're
        // eager or lazy binary, wrappers make it easier to implement logic binding.
        val needsAliases =
          distinctTypes.size != 1 || tpes.head == "Array[Byte]" || tpes.head.contains("BinaryStream") || tpes.head.contains("fs2.Stream")
        val tpesAreBin = tpes.head.contains("BinaryStream") || tpes.head.contains("fs2.Stream")
        def wrapBinType(s: String) = if (tpesAreBin) s"sttp.tapir.EndpointIO.StreamBodyWrapper($s)" else s
        val traitName = s"${endpointName.capitalize}BodyIn"
        val declsByWrapperClassName = decls
          .zip(tpes)
          .zipWithIndex
          .map { case ((decl, t), i) =>
            val caseClassName =
              if (t == "Array[Byte]" || t.contains("BinaryStream") || tpes.head.contains("fs2.Stream"))
                s"${endpointName.capitalize}Body${i}In"
              else s"${endpointName.capitalize}Body${t.split('.').last.replaceAll("[\\]\\[]", "_")}In"
            (caseClassName, t, decl)
          }
          .groupBy(_._1)
        val aliasDefns =
          if (needsAliases) {
            val wrappers = declsByWrapperClassName
              .map { case (name, seq) => s"""case class ${name}(value: ${seq.head._2}) extends $traitName""" }
              .mkString("\n")
            Some(s"""
               |sealed trait $traitName extends Product with java.io.Serializable
               |$wrappers
               |""".stripMargin)
          } else None
        val classNameByDecl = declsByWrapperClassName.flatMap { case (className, seq) =>
          seq.map { case (_, _, decl) => decl -> className }
        }

        val tpe = if (needsAliases) traitName else tpes.head
        val distinctInlineDefns = maybeInlineDefns.flatten.distinct.mkString("\n")
        val didO = if (distinctInlineDefns.isEmpty) None else Some(distinctInlineDefns)
        val d = b.description.map(s => s""".description("${JavaEscape.escapeString(s)}")""").getOrElse("")
        val bodies =
          if (needsAliases)
            decls.zip(tpes).map { case (decl, _) =>
              wrapBinType(s"$decl.map(${classNameByDecl(decl)}(_))(_.value).widenBody[$traitName]$d")
            }
          else decls.map(_ + d)
        Some(
          (
            s""".in(oneOfBody[$tpe](
             |${indent(2)(bodies.mkString(",\n"))}))""".stripMargin,
            tpe,
            combine(didO, aliasDefns)
          )
        )
      }
    }.unzip3

    (
      (params ++ rqBody).mkString("\n"),
      maybeEnumDefns.foldLeft(Option.empty[String]) {
        case (acc, None)            => acc
        case (None, Some(nxt))      => Some(nxt.mkString("\n"))
        case (Some(acc), Some(nxt)) => Some(acc + "\n" + nxt.mkString("\n"))
      },
      inTypes ++ maybeReqType,
      maybeInlineDefns.foldLeft(Option.empty[String])(combine(_, _))
    )
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
  private def outs(
      responses: Seq[OpenapiResponseDef],
      streamingImplementation: StreamingImplementation,
      doc: OpenapiDocument,
      targetScala3: Boolean,
      endpointName: String,
      jsonSerdeLib: JsonSerdeLib,
      xmlSerdeLib: XmlSerdeLib
  )(implicit
      location: Location
  ) = {
    // .errorOut(stringBody)
    // .out(oneOfBody(jsonBody[List[Book]]))

    val (outs, errorOuts) = responses.partition { resp =>
      resp.code match {
        case okStatus(_)                => true
        case "default" | errorStatus(_) => false
        case x                          => bail(s"Statuscode mapping is incomplete! Cannot handle $x")
      }
    }

    def bodyFmt(resp: OpenapiResponseDef, isErrorPosition: Boolean, optional: Boolean = false): (String, Option[String], Option[String]) = {
      def wrapContent(content: OpenapiResponseContent, forceEager: Boolean = false) = {
        val schemaIsNullable = content.schema.nullable || (content.schema match {
          case ref: OpenapiSchemaRef =>
            doc.components.flatMap(_.schemas.get(ref.stripped).map(_.nullable)).contains(true)
          case _ => false
        })
        val MappedContentType(decl, tpe, maybeInlineDefn) =
          contentTypeMapper(
            content.contentType,
            content.schema,
            streamingImplementation,
            !(optional || schemaIsNullable),
            endpointName,
            "Response",
            isErrorPosition || forceEager,
            xmlSerdeLib
          )
        (decl, tpe, maybeInlineDefn)
      }
      resp.content match {
        case Nil => ("", None, None)
        case content +: Nil =>
          val (decl, tpe, maybeInlineDefn) = wrapContent(content)
          val d = s""".description("${JavaEscape.escapeString(resp.description)}")"""
          (s"$decl$d", Some(tpe), maybeInlineDefn)
        case seq =>
          // These types all use 'eager' schemas; we cannot mix eager and streaming types when using oneOfBody
          val eagerTypes = Set("application/json", "application/xml", "text/plain", "text/html", "multipart/form-data")
          val forceEager = seq.exists(c => eagerTypes.contains(c.contentType))
          val (decls, tpes, maybeInlineDefns) = seq.map(wrapContent(_, forceEager)).unzip3
          val distinctTypes = tpes.distinct
          // If the types are distinct, we need to produce wrappers with a common parent for oneOfBody to work. If they're
          // eager or lazy binary, wrappers make it easier to implement logic binding.
          val needsAliases =
            distinctTypes.size != 1 || tpes.head == "Array[Byte]" || tpes.head.contains("BinaryStream") || tpes.head.contains("fs2.Stream")
          val tpesAreBin = tpes.head.contains("BinaryStream") || tpes.head.contains("fs2.Stream")
          def wrapBinType(s: String) = if (tpesAreBin) s"sttp.tapir.EndpointIO.StreamBodyWrapper($s)" else s
          val suff = if (isErrorPosition) "Err" else "Out"
          val traitName = s"${endpointName.capitalize}Body$suff"
          val d = s""".description("${JavaEscape.escapeString(resp.description)}")"""
          val declsByWrapperClassName = decls
            .zip(tpes)
            .zip(seq.map(_.contentType))
            .zipWithIndex
            .map { case (((decl, t), ct), i) =>
              val caseClassName =
                if (t == "Array[Byte]" || t.contains("BinaryStream") || tpes.head.contains("fs2.Stream"))
                  s"${endpointName.capitalize}Body${i}$suff"
                else s"${endpointName.capitalize}Body${t.split('.').last.replaceAll("[\\]\\[]", "_")}$suff"
              (caseClassName, t, decl, ct)
            }
            .groupBy(_._1)
          val aliasDefns =
            if (needsAliases) {
              def callers(tpe: String, impl: Boolean) = declsByWrapperClassName
                .flatMap { case (_, seq) =>
                  seq.map { case (_, t, _, ct) =>
                    s"""$tpe `$ct`: () => $t${if (impl) s""" = () => throw new RuntimeException("Body for content type $ct not provided")"""
                      else ","}"""
                  }
                }
                .mkString("\n")
              val wrappers = declsByWrapperClassName
                .map { case (name, seq) =>
                  val defns = seq.map { case (_, t, _, ct) => s"""override def `$ct`: () => $t = () => value""" }.mkString("\n")
                  s"""case class ${name}(value: ${seq.head._2}) extends $traitName{
                     |${indent(2)(defns)}
                     |}""".stripMargin
                }
                .mkString("\n")
              Some(s"""
                      |sealed trait $traitName extends Product with java.io.Serializable {
                      |${indent(2)(callers("def", true))}
                      |}
                      |case class ${traitName}Full (
                      |${indent(2)(callers("override val", false))}
                      |) extends $traitName
                      |$wrappers
                      |""".stripMargin)
            } else None
          val classNameByDecl = declsByWrapperClassName.flatMap { case (className, seq) =>
            seq.map { case (_, _, decl, _) => decl -> className }
          }

          val tpe = if (needsAliases) traitName else tpes.head
          val bodies =
            if (needsAliases)
              decls.zip(tpes).zip(seq.map(_.contentType)).map { case ((decl, _), ct) =>
                wrapBinType(
                  s"$decl.map(${classNameByDecl(decl)}(_))(_.`$ct`())\n" +
                    s".map(_.asInstanceOf[$traitName])(p => ${classNameByDecl(decl)}(p.`$ct`()))$d"
                )
              }
            else decls.map(_ + d)
          val distinctInlineDefns = maybeInlineDefns.flatten.distinct.mkString("\n")
          val didO = if (distinctInlineDefns.isEmpty) None else Some(distinctInlineDefns)
          (
            s"""oneOfBody[$tpe](
               |${indent(2)(bodies.mkString(",\n"))})""".stripMargin,
            Some(tpe),
            combine(didO, aliasDefns)
          )
      }
    }
    def mappedGroup(group: Seq[OpenapiResponseDef], isErrorPosition: Boolean): (Option[String], Option[String], Option[String]) =
      group match {
        case Nil => (None, None, None)
        case resp +: Nil =>
          val (outHeaderDefns, outHeaderInlineEnums, outHeaderTypes) = resp.headers
            // according to api spec, content-type header should be ignored - cf https://swagger.io/specification/#response-object
            .filterNot(_._1.toLowerCase == "content-type")
            .map { case (name, defn) =>
              genParamDefn(endpointName, targetScala3, jsonSerdeLib, defn.resolved(name, doc).param)
            }
            .unzip3
          val hs = outHeaderDefns.map(d => s".and($d)").mkString
          def ht(wrap: Boolean = true) =
            if (outHeaderTypes.isEmpty) None
            else if (outHeaderTypes.size == 1) Some(outHeaderTypes.head)
            else if (!wrap) Some(outHeaderTypes.mkString(", "))
            else Some(s"(${outHeaderTypes.mkString(", ")})")
          def inlineHeaderEnumDefns = outHeaderInlineEnums.foldLeft(Seq.empty[String]) { (acc, next) => acc ++ next.toSeq.flatten } match {
            case Nil => None
            case s   => Some(s.mkString("\n"))
          }
          resp.content match {
            case Nil =>
              val d = s""".description("${JavaEscape.escapeString(resp.description)}")"""
              (
                resp.code match {
                  case "200" | "default" if outHeaderDefns.isEmpty => None
                  case "200"                                       => Some(s"statusCode(sttp.model.StatusCode(200))$hs$d")
                  case "default"                                   => Some(s"statusCode(sttp.model.StatusCode(400))$hs$d")
                  case okStatus(s)                                 => Some(s"statusCode(sttp.model.StatusCode($s))$hs$d")
                  case errorStatus(s)                              => Some(s"statusCode(sttp.model.StatusCode($s))$hs$d")
                },
                ht(),
                inlineHeaderEnumDefns
              )
            case s =>
              val (decl, maybeBodyType, inlineDefn) = bodyFmt(resp, isErrorPosition)
              val tpe =
                if (outHeaderTypes.isEmpty) maybeBodyType
                else if (maybeBodyType.isEmpty) ht()
                else maybeBodyType.map(t => s"($t, ${ht(false).get})")
              (
                Some(resp.code match {
                  case "200" | "default" => s"$decl$hs"
                  case okStatus(s)       => s"$decl$hs.and(statusCode(sttp.model.StatusCode($s)))"
                  case errorStatus(s)    => s"$decl$hs.and(statusCode(sttp.model.StatusCode($s)))"
                }),
                tpe,
                inlineDefn.map(_ ++ inlineHeaderEnumDefns.getOrElse("")).orElse(inlineHeaderEnumDefns)
              )
          }
        case many =>
          if (many.map(_.code).distinct.size != many.size) bail("Cannot construct schema for multiple responses with same status code")
          val contentCanBeEmpty = many.exists(_.content.isEmpty)
          val allResponsesAreEmpty = many.forall(o => o.content.isEmpty && o.headers.isEmpty)
          val headerNamesAndTypes = many.map { m =>
            m.headers
              .filterNot(_._1.toLowerCase == "content-type")
              .map { case (name, defn) =>
                genParamDefn(endpointName, targetScala3, jsonSerdeLib, defn.resolved(name, doc).param)
              }
              .toSeq
          }
          val posn = if (isErrorPosition) "error" else "output"
          // We can probably do better here, but it's very fiddly... For now, fail if we don't meet this condition
          if (headerNamesAndTypes.map(_.map { case (name, _, defn) => name -> defn }.toSet).distinct.size != 1)
            bail(s"Cannot generate code for differing response headers on $posn responses")
          val commonResponseHeaders = headerNamesAndTypes.head
          val (outHeaderDefns, outHeaderInlineEnums, outHeaderTypes) = commonResponseHeaders.unzip3
          val underscores = outHeaderDefns.map(_ => "_").mkString(", ")
          val hs = outHeaderDefns.map(d => s".and($d)").mkString
          def ht(wrap: Boolean = true) =
            if (outHeaderTypes.isEmpty) None
            else if (outHeaderTypes.size == 1) Some(outHeaderTypes.head)
            else if (!wrap) Some(outHeaderTypes.mkString(", "))
            else Some(s"(${outHeaderTypes.mkString(", ")})")
          val (oneOfs, types, inlineDefns) = many.map { m =>
            val (decl, maybeBodyType, inlineDefn1) = bodyFmt(m, isErrorPosition, optional = contentCanBeEmpty)
            val code = if (m.code == "default") "400" else m.code
            if (decl == "" && allResponsesAreEmpty && commonResponseHeaders.isEmpty)
              (
                s"oneOfVariantSingletonMatcher(sttp.model.StatusCode($code), " +
                  s"""emptyOutput.description("${JavaEscape.escapeString(m.description)}"))(())""",
                maybeBodyType,
                inlineDefn1
              )
            else if (decl == "" && commonResponseHeaders.isEmpty)
              (
                s"oneOfVariantSingletonMatcher(sttp.model.StatusCode($code), " +
                  s"""emptyOutput.description("${JavaEscape.escapeString(m.description)}"))(None)""",
                maybeBodyType,
                inlineDefn1
              )
            else if (decl == "") {
              (
                s"oneOfVariantValueMatcher(sttp.model.StatusCode($code), " +
                  s"""emptyOutputAs(None).description("${JavaEscape.escapeString(
                      m.description
                    )}")$hs){ case (None, $underscores) => true}""",
                maybeBodyType,
                inlineDefn1
              )
            } else {
              def withHeaderTypes(t: String): String = if (commonResponseHeaders.isEmpty) t else s"($t, ${ht(false).get})"
              def withUnderscores(t: String): String = if (commonResponseHeaders.isEmpty) t else s"($t, $underscores)"
              if (contentCanBeEmpty) {
                val (_, nonOptionalType, _) = bodyFmt(m, isErrorPosition)
                val maybeMap = if (m.content.size > 1) ".map(Some(_))(_.orNull)" else ""
                val someType = nonOptionalType.map(": " + _.replaceAll("^Option\\[(.+)]$", "$1")).getOrElse("")
                (
                  s"oneOfVariantValueMatcher(sttp.model.StatusCode(${code}), $decl$maybeMap$hs){ case ${withUnderscores(s"Some(_$someType)")} => true }",
                  maybeBodyType,
                  inlineDefn1
                )
              } else
                (
                  s"oneOfVariant${maybeBodyType.map(s => s"[${withHeaderTypes(s)}]").getOrElse("")}(sttp.model.StatusCode(${code}), $decl$hs)",
                  maybeBodyType,
                  inlineDefn1
                )
            }
          }.unzip3
          val parentMap = doc.components.toSeq
            .flatMap(_.schemas)
            .collect { case (k, v: OpenapiSchemaOneOf) =>
              v.types.map {
                case r: OpenapiSchemaRef        => r.stripped -> k
                case x: OpenapiSchemaSimpleType => mapSchemaSimpleTypeToType(x)._1 -> k
                case x                          => bail(s"Unexpected oneOf child type $x")
              }
            }
            .flatten
            .groupBy(_._1)
            .map { case (k, vs) => k -> vs.map(_._2) }
            .toMap
          val traitName = s"${endpointName.capitalize}Body${if (isErrorPosition) "Err" else "Out"}"
          val mappable = Set("application/json", "application/xml", "multipart/form-data")
          val allElemTypes = many
            .flatMap(y =>
              y.content.map(x =>
                (x.contentType, x.schema, y.content.size > 1 && y.content.map(_.contentType).exists(!mappable.contains(_)))
              )
            )
            .map {
              case (_, _, true)                                                 => traitName
              case (ct, _, _) if ct.startsWith("text/") && isErrorPosition      => "String"
              case ("text/plain" | "text/html", _, _)                           => "String"
              case (ct, r: OpenapiSchemaRef, _) if mappable.contains(ct)        => r.stripped
              case (ct, x: OpenapiSchemaSimpleType, _) if mappable.contains(ct) => mapSchemaSimpleTypeToType(x)._1
              case (ct, x, _) if mappable.contains(ct) => bail(s"Unexpected oneOf elem type $x with content type $ct")
              case (_, _, _) if isErrorPosition        => "Array[Byte]"
              case (_, _, _)                           => capabilityType(streamingImplementation)
            }
            .distinct
          val commmonType = {
            if (allResponsesAreEmpty) "Unit"
            else if (contentCanBeEmpty && allElemTypes.size == 1) s"Option[${allElemTypes.head}]"
            else if (allElemTypes.size == 1) allElemTypes.head
            else {
              val baseType = allElemTypes.map { s => parentMap.getOrElse(s, Nil).toSet }.reduce(_ intersect _) match {
                case s if s.isEmpty && targetScala3 => types.flatten.mkString(" | ")
                case s if s.isEmpty                 => "Any"
                case s if targetScala3              => s.mkString(" & ")
                case s                              => s.mkString(" with ")
              }
              if (contentCanBeEmpty) s"Option[$baseType]" else baseType
            }
          }
          val oneOfType = if (commonResponseHeaders.isEmpty) commmonType else s"($commmonType, ${ht(false).get})"
          (
            Some(s"oneOf[$oneOfType](${oneOfs.mkString("\n  ", ",\n  ", "")})"),
            Some(oneOfType),
            (inlineDefns ++ outHeaderInlineEnums.map(_.map(_.mkString("\n")))).foldLeft(Option.empty[String])(combine(_, _))
          )
      }

    val (outDecls, outTypes, inlineOutDefns) = mappedGroup(outs, false)
    val mappedOuts = outDecls.map(s => s".out($s)")
    val (errDecls, errTypes, inlineErrDefns) = mappedGroup(errorOuts, true)
    val mappedErrorOuts = errDecls.map(s => s".errorOut($s)")

    (Seq(mappedErrorOuts, mappedOuts).flatten.mkString("\n"), outTypes, errTypes, combine(inlineOutDefns, inlineErrDefns))
  }

  private def inlineDefn(endpointName: String, position: String, schemaRef: OpenapiSchemaObject) = {
    require(schemaRef.properties.forall(_._2.`type`.isInstanceOf[OpenapiSchemaSimpleType]))
    val inlineClassName = endpointName.capitalize + position
    val properties = schemaRef.properties.map { case (k, v) =>
      val (st, nb) = mapSchemaSimpleTypeToType(v.`type`.asInstanceOf[OpenapiSchemaSimpleType], multipartForm = true)
      val optional = !schemaRef.required.contains(k) || nb
      val t = if (optional) s"Option[$st]" else st
      val default = v.default
        .map(j => " = " + DefaultValueRenderer.render(Map.empty, v.`type`, optional, RenderConfig())(j))
        .getOrElse(if (optional) " = None" else "")
      s"$k: $t$default"
    }
    val inlineClassDefn =
      s"""case class $inlineClassName (
         |${indent(2)(properties.mkString(",\n"))}
         |)""".stripMargin
    inlineClassName -> Some(inlineClassDefn)
  }
  private def contentTypeMapper(
      contentType: String,
      schema: OpenapiSchemaType,
      streamingImplementation: StreamingImplementation,
      required: Boolean,
      endpointName: String,
      position: String,
      forceEager: Boolean, // no streaming support for errorOut
      xmlSerdeLib: XmlSerdeLib
  )(implicit location: Location): MappedContentType = {
    contentType match {
      case "text/plain" =>
        MappedContentType("stringBody", "String")
      case "text/html" =>
        MappedContentType("htmlBodyUtf8", "String")
      case "application/xml" if xmlSerdeLib != XmlSerdeLib.NoSupport =>
        val (outT, maybeInline, maybeAlias) = schema match {
          case st: OpenapiSchemaSimpleType =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            (t, None, None)
          case a @ OpenapiSchemaArray(st: OpenapiSchemaSimpleType, _, _) =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            val xmlSeqConfig = XmlSerdeGenerator.genTopLevelSeqSerdes(xmlSerdeLib, a, endpointName, position)
            (s"List[$t]", xmlSeqConfig, Some(endpointName.capitalize + position))
          case x => bail(s"Only ref, primitive (and arrays of either) schemas supported for xml body (found $x)")
        }
        val req = if (required) outT else s"Option[$outT]"
        def toList = if (required) ".toList" else ".map(_.toList)"
        val bodyType = maybeAlias.map(a => s"xmlBody[$a].map(_.asInstanceOf[$req]$toList)(_.asInstanceOf[$a])").getOrElse(s"xmlBody[$req]")
        MappedContentType(bodyType, req, maybeInline)
      case "application/json" =>
        val (outT, maybeInline) = schema match {
          case st: OpenapiSchemaSimpleType =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            t -> None
          case OpenapiSchemaArray(st: OpenapiSchemaSimpleType, _, _) =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            s"List[$t]" -> None
          case OpenapiSchemaMap(st: OpenapiSchemaSimpleType, _) =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            s"Map[String, $t]" -> None
          case schemaRef: OpenapiSchemaObject if schemaRef.properties.forall(_._2.`type`.isInstanceOf[OpenapiSchemaSimpleType]) =>
            inlineDefn(endpointName, position, schemaRef)
          case x => bail(s"Can't create non-simple or array params as output (found $x)")
        }
        val req = if (required) outT else s"Option[$outT]"
        MappedContentType(s"jsonBody[$req]", req, maybeInline)

      case "multipart/form-data" =>
        schema match {
          case _: OpenapiSchemaBinary =>
            MappedContentType("multipartBody", "Seq[Part[Array[Byte]]]")
          case schemaRef: OpenapiSchemaRef =>
            val (t, _) = mapSchemaSimpleTypeToType(schemaRef, multipartForm = true)
            MappedContentType(s"multipartBody[$t]", t)
          case schemaRef: OpenapiSchemaObject if schemaRef.properties.forall(_._2.`type`.isInstanceOf[OpenapiSchemaSimpleType]) =>
            val (inlineClassName, inlineClassDefn) = inlineDefn(endpointName, position, schemaRef)
            MappedContentType(s"multipartBody[$inlineClassName]", inlineClassName, inlineClassDefn)
          case x => bail(s"$contentType only supports schema ref or binary, or simple inline property maps with string values. Found $x")
        }
      case other => failoverBinaryCase(other, schema, forceEager, streamingImplementation)
      case x     => bail(s"Not all content types supported! Found $x")
    }
  }
  private def failoverBinaryCase(
      contentType: String,
      schema: OpenapiSchemaType,
      isErrorPosition: Boolean,
      streamingImplementation: StreamingImplementation
  )(implicit location: Location): MappedContentType = {
    def codec(baseType: String, contentType: String) = baseType match {
      case "Array[Byte]" =>
        s"Codec.id[$baseType, `${contentType}CodecFormat`](`${contentType}CodecFormat`(), Schema.schemaForByteArray)"
      case "String" =>
        s"Codec.id[$baseType, `${contentType}CodecFormat`](`${contentType}CodecFormat`(), Schema.schemaForString)"
    }

    def eagerBody = contentType match {
      case "application/octet-stream" => "rawBinaryBody(sttp.tapir.RawBodyType.ByteArrayBody)"
      case o if o.startsWith("text/") => s"stringBodyUtf8AnyFormat(${codec("String", o)})"
      case "application/xml"          => s"EndpointIO.Body(RawBodyType.ByteArrayBody, CodecFormat.Xml(), EndpointIO.Info.empty)"
      case o                          => s"EndpointIO.Body(RawBodyType.ByteArrayBody, ${codec("Array[Byte]", o)}, EndpointIO.Info.empty)"
    }
    def streamingBody = contentType match {
      case "application/octet-stream" => "CodecFormat.OctetStream()"
      case "application/xml"          => "CodecFormat.Xml()"
      case o                          => s"`${o}CodecFormat`()"
    }
    if (isErrorPosition) MappedContentType(eagerBody, if (contentType.startsWith("text/")) "String" else "Array[Byte]")
    else {
      val capability = capabilityImpl(streamingImplementation)
      val tpe = capabilityType(streamingImplementation)
      schema match {
        case _: OpenapiSchemaString =>
          MappedContentType(s"streamTextBody($capability)($streamingBody)", tpe)
        case schema =>
          val outT = schema match {
            case st: OpenapiSchemaSimpleType =>
              val (t, _) = mapSchemaSimpleTypeToType(st)
              t
            case OpenapiSchemaArray(st: OpenapiSchemaSimpleType, _, _) =>
              val (t, _) = mapSchemaSimpleTypeToType(st)
              s"List[$t]"
            case OpenapiSchemaMap(st: OpenapiSchemaSimpleType, _) =>
              val (t, _) = mapSchemaSimpleTypeToType(st)
              s"Map[String, $t]"
            case x => bail(s"Can't create this param as output (found $x)")
          }
          MappedContentType(s"streamBody($capability)(Schema.binary[$outT], $streamingBody)", tpe)
      }
    }
  }

}

case class MappedContentType(bodyImpl: String, bodyType: String, inlineDefns: Option[String] = None)
