package sttp.tapir.codegen.endpoints

import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.endpoints.Position._
import sttp.tapir.codegen.endpoints.SimpleTypes.mapSchemaSimpleTypeToType
import sttp.tapir.codegen.endpoints.InAndOutComponents._
import sttp.tapir.codegen.json.JsonSerdeLib.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.GenerationDirectives.{
  forceEager,
  forceRespEager,
  forceRespStreaming,
  forceStreaming,
  jsonBodyAsString
}
import sttp.tapir.codegen.openapi.models.OpenapiModels.{OpenapiDocument, OpenapiResponseContent, OpenapiResponseDef}
import sttp.tapir.codegen.openapi.models.{DefaultValueRenderer, OpenapiSchemaType, RenderConfig}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaMap,
  OpenapiSchemaObject,
  OpenapiSchemaOneOf,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType,
  OpenapiSchemaString
}
import sttp.tapir.codegen.util.ErrUtils.bail
import sttp.tapir.codegen.util.{JavaEscape, Location, NameHelpers}
import sttp.tapir.codegen.util.NameHelpers.indent
import sttp.tapir.codegen.validation.ValidationDefns
import sttp.tapir.codegen.xml.XmlSerdeLib.XmlSerdeLib

object OutComponent {
  // treats redirects as ok
  private val okStatus = """([23]\d\d)""".r
  private val errorStatus = """([45]\d\d)""".r

  private[endpoints] def outs(
      responses: Seq[OpenapiResponseDef],
      streamingImplementation: StreamingImplementation,
      doc: OpenapiDocument,
      targetScala3: Boolean,
      endpointName: String,
      jsonSerdeLib: JsonSerdeLib,
      xmlSerdeLib: XmlSerdeLib,
      tapirCodegenDirectives: Set[String],
      validators: ValidationDefns,
      generateValidators: Boolean,
      isReused: Boolean,
      packageReuse: PackageReuseContext,
      seperateFilesForModels: Boolean
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
      def wrapContent(content: OpenapiResponseContent, preferEager: Boolean = false) = {
        val schemaIsNullable = content.schema.nullable || (content.schema match {
          case ref: OpenapiSchemaRef =>
            doc.components.flatMap(_.schemas.get(ref.stripped).map(_.nullable)).contains(true)
          case _ => false
        })
        val MappedContentType(decl, tpe, maybeInlineDefn, inlineTypes) = {
          contentTypeMapper(
            content.contentType,
            content.schema,
            streamingImplementation,
            !(optional || schemaIsNullable),
            endpointName,
            if (isErrorPosition) Err else Response,
            preferEager,
            xmlSerdeLib,
            tapirCodegenDirectives,
            validators
          )
        }
        val inlineDefn =
          maybeInlineDefn.map(d => if (isReused) aliases(packageReuse, inlineTypes, seperateFilesForModels) else d)
        (decl, tpe, inlineDefn)
      }

      resp.content match {
        case Nil            => ("", None, None)
        case content +: Nil =>
          val (decl, tpe, maybeInlineDefn) = wrapContent(content)
          val d = s""".description("${JavaEscape.escapeString(resp.description)}")"""
          (s"$decl$d", Some(tpe), maybeInlineDefn)
        case seq =>
          // We cannot mix eager and streaming types when using oneOfBody
          val preferEager = seq.exists(c => eagerTypes.contains(c.contentType))
          val (decls, tpes, maybeInlineDefns) = seq.map(wrapContent(_, preferEager)).unzip3
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
            if (needsAliases && isReused) {
              val parentModelPath = packageReuse.modelRoot(seperateFilesForModels)
              val wrappers = declsByWrapperClassName
                .map { case (name, seq) =>
                  s"type $name = $parentModelPath.$name\nval $name = $parentModelPath.$name\n"
                }
                .toSeq
                .sorted
                .mkString("\n")
              Some(s"""
                   |type $traitName = $parentModelPath.$traitName
                   |type ${traitName}Full = $parentModelPath.${traitName}Full
                   |val ${traitName}Full = $parentModelPath.${traitName}Full
                   |$wrappers
                   |""".stripMargin)
            } else if (needsAliases) {
              def callers(tpe: String, impl: Boolean) = declsByWrapperClassName
                .flatMap { case (_, seq) =>
                  seq.map { case (_, t, _, ct) =>
                    s"""$tpe `$ct`: () => $t${if (impl) s""" = () => throw new RuntimeException("Body for content type $ct not provided")"""
                      else ","}"""
                  }
                }
                .toSeq
                .sorted
                .mkString("\n")

              val wrappers = declsByWrapperClassName
                .map { case (name, seq) =>
                  val defns = seq.map { case (_, t, _, ct) => s"""override def `$ct`: () => $t = () => value""" }.sorted.mkString("\n")
                  s"""case class ${name}(value: ${seq.head._2}) extends $traitName{
                     |${indent(2)(defns)}
                     |}""".stripMargin
                }
                .toSeq
                .sorted
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
            combine(didO.filterNot(_ => isReused), aliasDefns)
          )
      }
    }

    def mappedGroup(group: Seq[OpenapiResponseDef], isErrorPosition: Boolean): MappedOutGroup =
      group match {
        case Nil         => MappedOutGroup(None, None, None)
        case resp +: Nil =>
          val (outHeaderDefns, outHeaderInlineEnums, outHeaderTypes) = resp.getHeaders.map { case (name, defn) =>
            ParamComponent.genParamDefn(endpointName, targetScala3, jsonSerdeLib, defn.resolved(name, doc).param, doc, generateValidators)
          }.unzip3
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
              MappedOutGroup(
                resp.code match {
                  case "200" | "default" if outHeaderDefns.isEmpty => None
                  case "200"                                       => Some(s"statusCode(sttp.model.StatusCode(200))$d$hs")
                  case "default"                                   => Some(s"statusCode(sttp.model.StatusCode(400))$d$hs")
                  case okStatus(s)                                 => Some(s"statusCode(sttp.model.StatusCode($s))$d$hs")
                  case errorStatus(s)                              => Some(s"statusCode(sttp.model.StatusCode($s))$d$hs")
                },
                ht(),
                inlineHeaderEnumDefns
              )
            case _ =>
              val (decl, maybeBodyType, inlineDefn) = bodyFmt(resp, isErrorPosition)
              val tpe =
                if (outHeaderTypes.isEmpty) maybeBodyType
                else if (maybeBodyType.isEmpty) ht()
                else maybeBodyType.map(t => s"($t, ${ht(false).get})")
              val tpeIsBin = maybeBodyType.exists(t => t.contains("BinaryStream") || t.contains("fs2.Stream"))
              MappedOutGroup(
                Some(resp.code match {
                  case "200" | "default" if !tpeIsBin || hs.isEmpty => s"$decl$hs"
                  case "200" | "default"                            => s"$decl.toEndpointIO$hs"
                  case okStatus(s) if tpeIsBin                      => s"$decl.toEndpointIO$hs.and(statusCode(sttp.model.StatusCode($s)))"
                  case okStatus(s)                                  => s"$decl$hs.and(statusCode(sttp.model.StatusCode($s)))"
                  case errorStatus(s)                               => s"$decl$hs.and(statusCode(sttp.model.StatusCode($s)))"
                }),
                tpe,
                inlineDefn.map(_ ++ inlineHeaderEnumDefns.getOrElse("")).orElse(inlineHeaderEnumDefns)
              )
          }
        case many =>
          if (many.map(_.code).distinct.size != many.size) bail("Cannot construct schema for multiple responses with same status code")
          val contentCanBeEmpty = many.exists(_.content.isEmpty)
          val allBodiesAreEmpty = many.forall(_.content.isEmpty)
          val allResponsesAreEmpty = allBodiesAreEmpty && many.forall(_.getHeaders.isEmpty)
          val (noHeaders, hs, outHeaderDefns, matchHeaders, headerTypes, headerTopType) =
            headerDefns(targetScala3, jsonSerdeLib, doc, generateValidators)(endpointName, many)
          val (oneOfs, types, inlineDefns) = many.map { m =>
            val (decl, maybeBodyType, inlineDefn1) = bodyFmt(m, isErrorPosition, optional = contentCanBeEmpty)
            val code = if (m.code == "default") "400" else m.code
            if (decl == "" && allResponsesAreEmpty && noHeaders)
              (
                s"oneOfVariantSingletonMatcher(sttp.model.StatusCode($code), " +
                  s"""emptyOutput.description("${JavaEscape.escapeString(m.description)}"))(())""",
                maybeBodyType,
                inlineDefn1
              )
            else if (decl == "" && noHeaders)
              (
                s"oneOfVariantSingletonMatcher(sttp.model.StatusCode($code), " +
                  s"""emptyOutput.description("${JavaEscape.escapeString(m.description)}"))(None)""",
                maybeBodyType,
                inlineDefn1
              )
            else if (decl == "") {
              val s =
                if (allBodiesAreEmpty)
                  s"oneOfVariantValueMatcher(sttp.model.StatusCode($code), " +
                    s"""emptyOutput.description("${JavaEscape.escapeString(m.description)}")""" +
                    s""".and(${hs(m)})){ case ${matchHeaders(m)} => true}"""
                else
                  s"oneOfVariantValueMatcher(sttp.model.StatusCode($code), " +
                    s"""emptyOutputAs(None).description("${JavaEscape.escapeString(
                        m.description
                      )}").and(${hs(m)})){ case (None, ${matchHeaders(m)}) => true}"""
              (s, maybeBodyType, inlineDefn1)
            } else {
              def bodyAndHeaderTypes(bodyType: String): String =
                if (noHeaders) bodyType
                else if (allBodiesAreEmpty) headerTypes(m)
                else s"($bodyType, ${headerTypes(m)})"

              def matchBodyAndHeaders(matchBody: String): String = if (noHeaders) matchBody else s"($matchBody, ${matchHeaders(m)})"

              val tpeIsBin = maybeBodyType.exists(t => t.contains("BinaryStream") || t.contains("fs2.Stream"))
              val maybeStrict = if (tpeIsBin) ".toEndpointIO" else ""

              def h = hs(m) match {
                case s if s.isEmpty => "";
                case s              => s".and($s)"
              }

              if (contentCanBeEmpty) {
                val (_, nonOptionalType, _) = bodyFmt(m, isErrorPosition)
                val maybeMap = if (m.content.size > 1 || tpeIsBin) ".map(Some(_))(_.orNull)" else ""
                val someType = nonOptionalType.map(": " + _.replaceAll("^Option\\[(.+)]$", "$1")).getOrElse("")
                (
                  s"oneOfVariantValueMatcher(sttp.model.StatusCode(${code}), $decl$maybeStrict$maybeMap$h){ case ${matchBodyAndHeaders(s"Some(_$someType)")} => true }",
                  maybeBodyType,
                  inlineDefn1
                )
              } else
                (
                  s"oneOfVariant${maybeBodyType.map(s => s"[${bodyAndHeaderTypes(s)}]").getOrElse("")}(sttp.model.StatusCode(${code}), $decl$maybeStrict$h)",
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
          val bodyIsStreaming = (!isErrorPosition && tapirCodegenDirectives.contains(forceRespStreaming)) ||
            (!isErrorPosition && tapirCodegenDirectives.contains(forceStreaming))
          val bodyIsEager = !bodyIsStreaming && (isErrorPosition ||
            (!isErrorPosition && tapirCodegenDirectives.contains(forceRespEager)) ||
            (!isErrorPosition && tapirCodegenDirectives.contains(forceEager)))
          val allElemTypes = many
            .flatMap(y =>
              y.content.map(x =>
                (x.contentType, x.schema, y.content.size > 1 && y.content.map(_.contentType).exists(!mappable.contains(_)))
              )
            )
            .map {
              case (_, _, _) if bodyIsStreaming                            => capabilityType(streamingImplementation)
              case (_, _, true)                                            => traitName
              case (ct, _, _) if ct.startsWith("text/") && isErrorPosition => "String"
              case ("text/plain" | "text/html", _, _)                      => "String"
              case ("application/json", _, _) if tapirCodegenDirectives.contains(jsonBodyAsString) => "String"
              case (ct, r: OpenapiSchemaRef, _) if mappable.contains(ct)                           => r.stripped
              case (ct, x: OpenapiSchemaSimpleType, _) if mappable.contains(ct)                    => mapSchemaSimpleTypeToType(x)._1
              case (ct, x, _) if mappable.contains(ct) => bail(s"Unexpected oneOf elem type $x with content type $ct")
              case (_, _, _) if bodyIsEager            => "Array[Byte]"
              case (_, _, _)                           => capabilityType(streamingImplementation)
            }
            .distinct
          val commmonType = {
            if (allResponsesAreEmpty || allElemTypes.isEmpty) "Unit"
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
          val oneOfType = if (noHeaders) commmonType else if (allBodiesAreEmpty) headerTopType else s"($commmonType, $headerTopType)"
          MappedOutGroup(
            Some(s"oneOf[$oneOfType](${oneOfs.mkString("\n  ", ",\n  ", "")})"),
            Some(oneOfType),
            (inlineDefns ++ outHeaderDefns).foldLeft(Option.empty[String])(combine(_, _))
          )
      }

    val MappedOutGroup(outDecls, outTypes, inlineOutDefns) = mappedGroup(outs, false)
    val mappedOuts = outDecls.map(s => s".out($s)")
    val MappedOutGroup(errDecls, errTypes, inlineErrDefns) = mappedGroup(errorOuts, true)
    val mappedErrorOuts = errDecls.map(s => s".errorOut($s)")

    (Seq(mappedErrorOuts, mappedOuts).flatten.mkString("\n"), outTypes, errTypes, combine(inlineOutDefns, inlineErrDefns))
  }

  private def headerDefns(targetScala3: Boolean, jsonSerdeLib: JsonSerdeLib, doc: OpenapiDocument, generateValidators: Boolean)(
      endpointName: String,
      many: Seq[OpenapiResponseDef]
  )(implicit
      location: Location
  ): (Boolean, OpenapiResponseDef => String, Seq[Option[String]], OpenapiResponseDef => String, OpenapiResponseDef => String, String) = {
    val (paramNames, headerNamesAndTypes) = many.map { m =>
      m.getHeaders
        .map { case (name, defn) =>
          val param = defn.resolved(name, doc).param
          NameHelpers.safeVariableName(param.name) ->
            ParamComponent.genParamDefn(endpointName, targetScala3, jsonSerdeLib, param, doc, generateValidators)
        }
        .toSeq
        .sortBy(_._1)
        .unzip
    }.unzip
    if (headerNamesAndTypes.forall(_.isEmpty)) (true, _ => "", Nil, _ => "", _ => "", "")
    else if (headerNamesAndTypes.map(_.map { case (name, _, defn) => name -> defn }.toSet).distinct.size == 1) {
      val commonResponseHeaders = headerNamesAndTypes.head
      val (outHeaderDefns, outHeaderInlineEnums, outHeaderTypes) = commonResponseHeaders.unzip3
      val underscores = outHeaderDefns.map(_ => "_").mkString(", ")
      val hs = (_: OpenapiResponseDef) => outHeaderDefns.zipWithIndex.map { case (d, 0) => d; case (d, _) => s".and($d)" }.mkString
      val noHeaders = commonResponseHeaders.isEmpty

      val ht = (_: OpenapiResponseDef) =>
        if (outHeaderTypes.isEmpty) bail("Should not try to construct header types if no headers are required")
        else if (outHeaderTypes.size == 1) outHeaderTypes.head
        else s"(${outHeaderTypes.mkString(", ")})"

      val headerTopType = if (outHeaderTypes.isEmpty) "Unit" else ht(null)

      val enumDefns = outHeaderInlineEnums.map(_.map(_.mkString("\n")))
      (noHeaders, hs, enumDefns, (_: OpenapiResponseDef) => underscores, ht, headerTopType)
    } else {
      val traitName = s"${endpointName.capitalize}ResponseHeader"

      val (headerMappingDefns, headerModelDefns, enumDefns: Seq[Seq[String]]) = headerNamesAndTypes
        .zip(many.map(_.code))
        .zip(paramNames)
        .map {
          case ((s, c), _) if s.isEmpty =>
            val objName = s"$traitName$c"
            (s"emptyOutputAs($objName)", s"case object $objName extends $traitName", Seq.empty[String])
          case ((s, c), names) =>
            val className = s"$traitName$c"
            val (headerDefns, headerInlineEnums, headerTypes) = s.unzip3
            val rawOutput = headerDefns.zipWithIndex.map { case (d, 0) => d; case (d, _) => s".and($d)" }.mkString
            val output =
              if (s.size == 1) s"$rawOutput.map($className(_))(_.${names.head})"
              else s"($rawOutput).map(($className.apply _).tupled)($className.unapply(_).get)"
            val fields = names.zip(headerTypes).map { case (n, t) => s"$n: $t" }.mkString(", ")
            (output, s"""case class $traitName$c($fields) extends $traitName""", headerInlineEnums.flatten.flatten)
        }
        .unzip3
      val mappingsByCode = headerMappingDefns
        .zip(many.map(_.code))
        .map { case (s, c) => c -> s }
        .toMap
      val tpesByCode = headerModelDefns
        .zip(many.map(_.code))
        .map { case (s, c) => c -> s }
        .toMap
      val headerTypeDefns =
        s"""sealed trait ${endpointName.capitalize}ResponseHeader
           |${tpesByCode.values.toSeq.sorted.mkString("\n")}
           |""".stripMargin
      def ht(m: OpenapiResponseDef) = tpesByCode(m.code)
      def getMapping(m: OpenapiResponseDef) = mappingsByCode(m.code)
      def getMatch(m: OpenapiResponseDef) =
        if (m.getHeaders.isEmpty) s"$traitName${m.code}"
        else s"(_: $traitName${m.code})"
      val enums = enumDefns.flatten.distinct.map(Some(_))
      (false, getMapping, Some(headerTypeDefns) +: enums, getMatch, ht, traitName)
    }
  }

}
