package sttp.tapir.codegen.endpoints

import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.endpoints.InAndOutComponents.{aliases, combine, contentTypeMapper, eagerTypes}
import sttp.tapir.codegen.endpoints.Position.Request
import sttp.tapir.codegen.json.JsonSerdeLib.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels
import sttp.tapir.codegen.openapi.models.OpenapiModels.{
  OpenapiDocument,
  OpenapiParameter,
  OpenapiRequestBodyContent,
  OpenapiRequestBodyDefn
}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.OpenapiSchemaRef
import sttp.tapir.codegen.util.{JavaEscape, Location}
import sttp.tapir.codegen.util.NameHelpers.indent
import sttp.tapir.codegen.validation.ValidationDefns
import sttp.tapir.codegen.xml.XmlSerdeLib.XmlSerdeLib

object InComponent {

  private[endpoints] def ins(
      parameters: Seq[OpenapiParameter],
      requestBody: Option[OpenapiRequestBodyDefn],
      endpointName: String,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib,
      xmlSerdeLib: XmlSerdeLib,
      streamingImplementation: StreamingImplementation,
      doc: OpenapiDocument,
      tapirCodegenDirectives: Set[String],
      validators: ValidationDefns,
      generateValidators: Boolean,
      isReused: Boolean,
      packageReuse: PackageReuseContext,
      seperateFilesForModels: Boolean
  )(implicit location: Location): (String, Option[String], Seq[String], Option[String]) = {

    // .in(query[Limit]("limit").description("Maximum number of books to retrieve"))
    // .in(header[AuthToken]("X-Auth-Token"))
    val (params, maybeEnumDefns, inTypes) = parameters
      .filter(_.in != "path")
      .map { param =>
        ParamComponent.genParamDefn(endpointName, targetScala3, jsonSerdeLib, param, doc, generateValidators)
      }
      .map { case (defn, enums, tpe) => (s".in($defn)", enums, tpe) }
      .unzip3

    def mapContent(
        content: OpenapiRequestBodyContent,
        required: Boolean,
        preferEager: Boolean = false
    ): (String, String, Option[String]) = {
      val schemaIsNullable = content.schema.nullable || (content.schema match {
        case ref: OpenapiSchemaRef =>
          doc.components.flatMap(_.schemas.get(ref.stripped).map(_.nullable)).contains(true)
        case _ => false
      })
      val MappedContentType(decl, tpe, maybeInlineDefn, inlineTypes) =
        contentTypeMapper(
          content.contentType,
          content.schema,
          streamingImplementation,
          required && !schemaIsNullable,
          endpointName,
          Request,
          preferEager,
          xmlSerdeLib,
          tapirCodegenDirectives,
          validators
        )

      val inlineDefn = maybeInlineDefn.map(d => if (isReused) aliases(packageReuse, inlineTypes, seperateFilesForModels) else d)
      (decl, tpe, inlineDefn)
    }
    val (rqBody, maybeReqType, maybeInlineDefns) = requestBody.flatMap { b =>
      if (b.content.isEmpty) None
      else if (b.content.size == 1) {
        val content: OpenapiModels.OpenapiRequestBodyContent = b.content.head
        val (decl, tpe, maybeInlineDefn) = mapContent(content, b.required)
        val d = b.description.map(s => s""".description("${JavaEscape.escapeString(s)}")""").getOrElse("")
        Some((s".in($decl$d)", tpe, maybeInlineDefn))
      } else {
        // We cannot mix eager and streaming types when using oneOfBody
        val preferEager = b.content.exists(c => eagerTypes.contains(c.contentType))
        val mapped = b.content.map(mapContent(_, b.required, preferEager))
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
          if (needsAliases && isReused) {
            val wrappers = declsByWrapperClassName
              .map { case (name, _) =>
                s"type $name = ${packageReuse.dependencyModelPath}.$name\nval $name = ${packageReuse.dependencyModelPath}.$name\n"
              }
              .toSeq
              .sorted
              .mkString("\n")
            Some(s"""
                    |type $traitName = ${packageReuse.dependencyModelPath}.$traitName
                    |$wrappers
                    |""".stripMargin)
          } else if (needsAliases) {
            val wrappers = declsByWrapperClassName
              .map { case (name, seq) => s"""case class ${name}(value: ${seq.head._2}) extends $traitName""" }
              .toSeq
              .sorted
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
            combine(didO.filterNot(_ => isReused), aliasDefns)
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
}
