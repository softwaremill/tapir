package sttp.tapir.codegen.endpoints

import io.circe.Json
import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.endpoints.SimpleTypes.mapSchemaSimpleTypeToType
import sttp.tapir.codegen.endpoints.InAndOutComponents._
import sttp.tapir.codegen.endpoints.OutComponent.{generateSharedStatusCodeDisambiguators, statusCodeDisambigBaseTrait}
import sttp.tapir.codegen.json.JsonSerdeLib.JsonSerdeLib
import sttp.tapir.codegen.openapi.models._
import sttp.tapir.codegen.openapi.models.GenerationDirectives._
import sttp.tapir.codegen.openapi.models.OpenapiModels.{OpenapiDocument, OpenapiParameter, OpenapiPath}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._
import sttp.tapir.codegen.security.{SecurityDefn, SecurityGenerator, SecurityWrapperDefn}
import sttp.tapir.codegen.util.ErrUtils.bail
import sttp.tapir.codegen.util.NameHelpers.{indent, strippedToCamelCase}
import sttp.tapir.codegen.util.{JavaEscape, Location}
import sttp.tapir.codegen.validation.ValidationDefns
import sttp.tapir.codegen.xml.XmlSerdeLib
import sttp.tapir.codegen.xml.XmlSerdeLib.XmlSerdeLib

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

case class EndpointDetails(
    jsonParamRefs: Set[String],
    inlineDefns: Seq[String],
    xmlParamRefs: Set[String],
    securityWrappers: Set[SecurityWrapperDefn],
    statusCodeDisambig: Seq[(String, String)] = Nil
) {
  def merge(that: EndpointDetails) = EndpointDetails(
    jsonParamRefs ++ that.jsonParamRefs,
    inlineDefns ++ that.inlineDefns,
    xmlParamRefs ++ that.xmlParamRefs,
    securityWrappers ++ that.securityWrappers,
    statusCodeDisambig ++ that.statusCodeDisambig
  )
}
object EndpointDetails {
  val empty = EndpointDetails(Set.empty, Nil, Set.empty, Set.empty)
}

case class GeneratedEndpoints(
    namesAndParamsByFile: Seq[GeneratedEndpointsForFile],
    queryParamRefs: Set[String],
    definesEnumQueryParam: Boolean,
    details: EndpointDetails
) {
  def merge(that: GeneratedEndpoints): GeneratedEndpoints =
    GeneratedEndpoints(
      (namesAndParamsByFile ++ that.namesAndParamsByFile)
        .groupBy(_.maybeFileName)
        .map { case (fileName, endpoints) => GeneratedEndpointsForFile(fileName, endpoints.map(_.generatedEndpoints).reduce(_ ++ _)) }
        .toSeq,
      queryParamRefs ++ that.queryParamRefs,
      definesEnumQueryParam || that.definesEnumQueryParam,
      details.merge(that.details)
    )
}
case class EndpointDefs(
    endpointDecls: Map[Option[String], String],
    queryOrPathParamRefs: Set[String],
    enumsDefinedOnEndpointParams: Boolean,
    details: EndpointDetails
)

class EndpointGenerator {
  private[codegen] def allEndpoints: String = "generatedEndpoints"

  def endpointDefs(
      doc: OpenapiDocument,
      useHeadTagForObjectNames: Boolean,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib,
      xmlSerdeLib: XmlSerdeLib,
      streamingImplementation: StreamingImplementation,
      generateEndpointTypes: Boolean,
      validators: ValidationDefns,
      generateValidators: Boolean,
      packageReuse: PackageReuseContext,
      seperateFilesForModels: Boolean,
      addDisambiguationCodes: Boolean
  ): EndpointDefs = {
    val capabilities = capabilityImpl(streamingImplementation)
    val components = Option(doc.components).flatten
    val GeneratedEndpoints(
      endpointsByFile,
      queryOrPathParamRefs,
      definesEnumQueryParam,
      details
    ) =
      doc.paths
        .map(
          generatedEndpoints(
            components,
            useHeadTagForObjectNames,
            targetScala3,
            jsonSerdeLib,
            xmlSerdeLib,
            streamingImplementation,
            doc,
            validators,
            generateValidators,
            packageReuse,
            seperateFilesForModels,
            addDisambiguationCodes
          )
        )
        .foldLeft(GeneratedEndpoints(Nil, Set.empty, false, EndpointDetails.empty))(_ merge _)
    val statusCodeDisambig = details.statusCodeDisambig.distinct
    val inlineDefnsWithStatusCodeDisambig =
      if (statusCodeDisambig.isEmpty) details.inlineDefns
      else
        Seq(s"sealed trait $statusCodeDisambigBaseTrait") ++ details.inlineDefns ++ generateSharedStatusCodeDisambiguators(
          statusCodeDisambig
        ).toSeq
    val enrichedDetails = details.copy(inlineDefns = inlineDefnsWithStatusCodeDisambig)
    val endpointDecls = endpointsByFile.map { case GeneratedEndpointsForFile(maybeFileName, ge) =>
      val definitions = ge
        .map { case GeneratedEndpoint(name, definition, maybeInlineDefns, types) =>
          val theCapabilities = if (definition.contains(".capabilities.")) capabilities else "Any"
          val endpointTypeDecl =
            if (generateEndpointTypes && packageReuse.reusedEndpointNames.contains(name))
              s"type ${name.capitalize}Endpoint = ${packageReuse.depPkg}.${maybeFileName.getOrElse(packageReuse.dependencyObjectName)}.${name.capitalize}Endpoint\n"
            else if (generateEndpointTypes)
              s"type ${name.capitalize}Endpoint = Endpoint[${types.securityTypes}, ${types.inTypes}, ${types.errTypes}, ${types.outTypes}, $theCapabilities]\n"
            else ""

          val maybeType = if (generateEndpointTypes) s": ${name.capitalize}Endpoint" else ""
          s"""${endpointTypeDecl}lazy val $name$maybeType =
             |${indent(2)(definition)}${maybeInlineDefns.fold("")("\n" + _)}
             |""".stripMargin
        }
        .mkString("\n")
      val allEP = s"lazy val $allEndpoints = List(${ge.map(_.name).mkString(", ")})"

      maybeFileName -> s"""|$definitions
                           |
                           |$allEP
                           |""".stripMargin
    }.toMap
    EndpointDefs(endpointDecls, queryOrPathParamRefs, definesEnumQueryParam, enrichedDetails)
  }

  private[codegen] def generatedEndpoints(
      components: Option[OpenapiComponent],
      useHeadTagForObjectNames: Boolean,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib,
      xmlSerdeLib: XmlSerdeLib,
      streamingImplementation: StreamingImplementation,
      doc: OpenapiDocument,
      validators: ValidationDefns,
      generateValidators: Boolean,
      packageReuse: PackageReuseContext,
      seperateFilesForModels: Boolean,
      addDisambiguationCodes: Boolean
  )(p: OpenapiPath): GeneratedEndpoints = {
    val parameters = components.map(_.parameters).getOrElse(Map.empty)
    val securitySchemes = components.map(_.securitySchemes).getOrElse(Map.empty)

    val (fileNamesAndParams, unflattenedParamRefs, inlineParamInfo) = p.methods
      .map(_.withResolvedParentParameters(parameters, p.parameters))
      .map { m =>
        implicit val location: Location = Location(p.url, m.methodType)
        try {
          def attributeString = {
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
          val isReused = packageReuse.reusedEndpointNames.contains(name)
          def maybeName = m.operationId.map(n => s"""\n  .name("${JavaEscape.escapeString(n)}")""").getOrElse("")
          val UrlMapResponse(pathDecl, pathTypes, maybeSecurityPath, inlineUrlDefs) = UrlEndpointComponent.urlMapper(
            p.url,
            name,
            targetScala3,
            jsonSerdeLib,
            m.resolvedParameters,
            doc.pathsExtensions.get(securityPrefixKey).flatMap(_.asArray).toSeq.flatMap(_.flatMap(_.asString)),
            doc,
            generateValidators,
            isReused,
            packageReuse
          )
          val SecurityDefn(securityDecl, securityTypes, securityWrappers) =
            SecurityGenerator.security(securitySchemes, m.security.getOrElse(doc.security))
          val (inParams, maybeLocalEnums, inTypes, inlineInDefns) =
            InComponent.ins(
              m.resolvedParameters,
              m.requestBody.map(_.resolve(doc)),
              name,
              targetScala3,
              jsonSerdeLib,
              xmlSerdeLib,
              streamingImplementation,
              doc,
              m.tapirCodegenDirectives,
              validators,
              generateValidators,
              isReused,
              packageReuse,
              seperateFilesForModels
            )
          val (outDecl, outTypes, errTypes, inlineDefns, statusCodeDisambig) =
            OutComponent.outs(
              m.responses.map(_.resolve(doc)),
              streamingImplementation,
              doc,
              targetScala3,
              name,
              jsonSerdeLib,
              xmlSerdeLib,
              m.tapirCodegenDirectives,
              validators,
              generateValidators,
              isReused,
              packageReuse,
              seperateFilesForModels,
              addDisambiguationCodes
            )
          val allTypes = EndpointTypes(
            maybeSecurityPath.toSeq.flatMap(_._2) ++ securityTypes.toSeq,
            pathTypes ++ inTypes,
            errTypes.toSeq,
            outTypes.toSeq
          )
          val maybeInlineUrlDefs = if (inlineUrlDefs.isEmpty) None else Some(inlineUrlDefs.mkString("\n"))
          val inlineDefn = combine(maybeInlineUrlDefs, combine(inlineInDefns, inlineDefns))
          val sec = securityDecl.map(indent(2)(_) + "\n").getOrElse("")
          val securityPathDecl = maybeSecurityPath.map("\n  " + _._1).getOrElse("")
          val maybeTargetFileName = if (useHeadTagForObjectNames) m.tags.flatMap(_.headOption) else None
          val definition =
            if (isReused)
              s"${packageReuse.depPkg}.${maybeTargetFileName.getOrElse(packageReuse.dependencyObjectName)}.$name"
            else
              s"""|endpoint$maybeName
                  |  .${m.methodType}
                  |  $pathDecl
                  |$sec${indent(2)(inParams)}$securityPathDecl
                  |${indent(2)(outDecl)}
                  |${indent(2)(tags(m.tags))}
                  |$attributeString
                  |""".stripMargin.linesIterator.filterNot(_.trim.isEmpty).mkString("\n")

          val queryOrPathParamRefs = m.resolvedParameters
            .collect {
              case queryParam: OpenapiParameter if queryParam.in == "query" || queryParam.in == "path" || queryParam.in == "header" =>
                queryParam.schema
            }
            .collect {
              case ref: OpenapiSchemaRef if ref.isSchema                              => ref.stripped
              case OpenapiSchemaArray(ref: OpenapiSchemaRef, _, _, _) if ref.isSchema => ref.stripped
            }
            .toSet
          val xmlParamRefs: Seq[String] = (m.requestBody.toSeq.flatMap(_.resolve(doc).content.map(c => (c.contentType, c.schema))) ++
            m.responses.flatMap(_.resolve(doc).content.map(c => (c.contentType, c.schema))))
            .collect { case (contentType, schema) if contentType == "application/xml" => schema }
            .collect { case ref: OpenapiSchemaRef if ref.isSchema => ref.stripped }
          val jsonParamRefs = (m.requestBody.toSeq.flatMap(_.resolve(doc).content.map(c => (c.contentType, c.schema))) ++
            m.responses.flatMap(_.resolve(doc).content.map(c => (c.contentType, c.schema))))
            .filterNot(_ => m.tapirCodegenDirectives.contains(jsonBodyAsString))
            .collect { case (contentType, schema) if contentType == "application/json" => schema }
            .collect {
              case ref: OpenapiSchemaRef if ref.isSchema                              => ref.stripped
              case OpenapiSchemaArray(ref: OpenapiSchemaRef, _, _, _) if ref.isSchema => ref.stripped
              case OpenapiSchemaArray(OpenapiSchemaAny(_, tpe), _, _, _)              => AnyType.toCirceTpe(tpe)
              case OpenapiSchemaArray(simple: OpenapiSchemaSimpleType, _, _, _)       =>
                val name = mapSchemaSimpleTypeToType(simple)._1
                s"List[$name]"
              case simple: OpenapiSchemaSimpleType                         => mapSchemaSimpleTypeToType(simple)._1
              case OpenapiSchemaMap(simple: OpenapiSchemaSimpleType, _, _) =>
                val name = mapSchemaSimpleTypeToType(simple)._1
                s"Map[String, $name]"
            }
            .toSet
          (
            (maybeTargetFileName, GeneratedEndpoint(name, definition, maybeLocalEnums.filterNot(_ => isReused), allTypes)),
            (queryOrPathParamRefs, jsonParamRefs, xmlParamRefs),
            (maybeLocalEnums.isDefined && !isReused, inlineDefn, securityWrappers, statusCodeDisambig)
          )
        } catch {
          case e: NotImplementedError => throw e
          case e: Throwable           => bail(s"Unexpected error (${e.getMessage})", Some(e))
        }
      }
      .unzip3
    val (unflattenedQueryParamRefs, unflattenedJsonParamRefs, xmlParamRefs) = unflattenedParamRefs.unzip3
    val namesAndParamsByFile = fileNamesAndParams
      .groupBy(_._1)
      .toSeq
      .map { case (maybeTargetFileName, defns) => GeneratedEndpointsForFile(maybeTargetFileName, defns.map(_._2)) }
    val definesParams = inlineParamInfo.map(_._1)
    val inlineDefns = inlineParamInfo.map(_._2)
    val securityWrappers = inlineParamInfo.map(_._3)
    val statusCodeDisambigs = inlineParamInfo.map(_._4)
    GeneratedEndpoints(
      namesAndParamsByFile,
      unflattenedQueryParamRefs.foldLeft(Set.empty[String])(_ ++ _),
      definesParams.contains(true),
      EndpointDetails(
        unflattenedJsonParamRefs.foldLeft(Set.empty[String])(_ ++ _),
        inlineDefns.flatten,
        xmlParamRefs.flatten.toSet,
        securityWrappers.flatten.toSet,
        statusCodeDisambig = statusCodeDisambigs.flatten
      )
    )
  }

  private def tags(openapiTags: Option[Seq[String]]): String = {
    // .tags(List("A", "B")) -- tag strings come from the untrusted document and are emitted into a string literal,
    // so escape each one (as the .name/.description sites do). See GHSA-gpcc-36pq-8qxr.
    openapiTags.map(_.distinct.map(JavaEscape.escapeString).mkString(".tags(List(\"", "\", \"", "\"))")).mkString
  }

  private def attributes(atts: Map[String, Json]): Option[String] = if (atts.nonEmpty) Some {
    atts
      .map { case (k, v) =>
        val camelCaseK = strippedToCamelCase(k)
        val uncapitalisedName = camelCaseK.head.toLower +: camelCaseK.tail
        s""".attribute[${camelCaseK.capitalize}Extension](${uncapitalisedName}ExtensionKey, ${SpecificationExtensionRenderer.renderValue(
            v
          )})"""
      }
      .mkString("\n")
  }
  else None

}

case class MappedContentType(bodyImpl: String, bodyType: String, inlineDefns: Option[String] = None, inlineTypes: Seq[String] = Nil)
case class MappedOutGroup(decls: Option[String], types: Option[String], defns: Option[String], statusCodeDisambig: Seq[(String, String)] = Nil)
