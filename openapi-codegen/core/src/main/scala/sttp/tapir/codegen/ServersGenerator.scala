package sttp.tapir.codegen

import sttp.tapir.codegen.RootGenerator.indent
import sttp.tapir.codegen.openapi.models.OpenapiServer
import sttp.tapir.codegen.util.JavaEscape
import sttp.tapir.codegen.util.NameHelpers.safeVariableName

object ServersGenerator {

  // The server URL is emitted both as a backtick-quoted identifier and inside a `uri"..."` interpolator (where a `$`
  // would itself interpolate). A legitimate server URL contains none of these characters, so reject any that does
  // rather than attempt to escape every context. See GHSA-gpcc-36pq-8qxr.
  private def validateServerUrl(url: String): Unit =
    if (url.exists(c => c == '"' || c == '`' || c == '\\' || c == '$' || c.isControl))
      throw new IllegalArgumentException(
        s"Unsafe server URL '$url': must not contain quotes, backticks, backslashes, '$$' or control characters (see GHSA-gpcc-36pq-8qxr)"
      )

  def genServerDefinitions(servers: Seq[OpenapiServer], isScala3: Boolean): Option[String] = if (servers.isEmpty) None
  else
    Some {
      val defns = servers.map(genServerDefinition(_, isScala3)).mkString("\n\n")
      s"""
       |
       |object Servers {
       |  import sttp.model.Uri.UriContext
       |
       |${indent(2)(defns)}
       |
       |}""".stripMargin
    }
  private def genServerDefinition(server: OpenapiServer, isScala3: Boolean) = {
    validateServerUrl(server.url)
    if (server.variables.isEmpty) genServerUrlVal(server)
    else genServerDefinitionWithVariables(server, isScala3)
  }
  private def genServerUrlVal(server: OpenapiServer) = {
    s"""${genDescription(server.description)}val `${server.url}`: sttp.model.Uri = uri"${server.url}""""
  }
  private def genServerDefinitionWithVariables(server: OpenapiServer, isScala3: Boolean) = {
    val enumNames = server.variables.map { case (k, vs) =>
      (
        safeVariableName(k),
        vs.`enum`.map(i => safeVariableName(i)),
        vs.default.map(v => if (vs.`enum`.isEmpty) JavaEscape.quote(v) else safeVariableName(v))
      )
    }
    val enums = enumNames
      .map {
        case (e, elems, d) if elems.nonEmpty =>
          if (isScala3) {
            val maybeDefault = d
              .map(s => s"""
                           |object $e {
                           |  val default: $e = $s
                           |}""".stripMargin)
              .getOrElse("")
            s"""enum $e {
               |  case ${elems.mkString(", ")}
               |}$maybeDefault""".stripMargin
          } else {
            val maybeDefault = d.map(s => s"\n  val default: $e = $s").getOrElse("")
            s"""sealed trait $e extends enumeratum.EnumEntry
               |object $e extends enumeratum.Enum[$e] {
               |  val values = findValues
               |${indent(2)(elems.map(v => s"case object $v extends $e").mkString("\n"))}$maybeDefault
               |}""".stripMargin
          }
        case (e, _, d) => d.map(v => s"""val ${safeVariableName(s"default${e.capitalize}")} = $v""").getOrElse("")
      }
      .mkString("\n")
    // 'with default' should come after 'without'
    val (withDefault, withoutDefault) = enumNames.partition(_._3.isDefined)
    val enumParams = (withoutDefault ++ withDefault)
      .map {
        case (e, vs, d) if vs.nonEmpty => s"_$e: $e${d.map(_ => s" = $e.default").getOrElse("")}"
        case (e, _, d) if d.nonEmpty   => s"_$e: String${d.map(_ => s" = ${safeVariableName(s"default${e.capitalize}")}").getOrElse("")}"
        case (e, _, _)                 => s"_$e: String"
      }
      .mkString(", ")
    val urlStringFormat = server.variables
      .map { case (k, _) => k -> safeVariableName(k) }
      .foldLeft(server.url.replace("{", "${")) { case (acc, (next, v)) =>
        acc.replace(s"$${$next}", s"$${_$v}")
      }
    s"""${genDescription(server.description)}object `${server.url}` {
       |${indent(2)(enums)}
       |  def uri($enumParams): sttp.model.Uri =
       |    uri"$urlStringFormat"
       |}""".stripMargin
  }
  private def genDescription(description: Option[String]): String =
    // Neutralise the (untrusted) description before emitting it into a block comment: double every backslash so a
    // `*/` unicode escape cannot be reconstructed by Scala 2's unicode pre-scan (which runs before comment
    // lexing) into a `*/`, and textually break any literal `*/`. Either could otherwise close the comment early and
    // inject the code that follows. See GHSA-gpcc-36pq-8qxr.
    description
      .map(d => s"/*\n${indent(2)(d.replace("\\", "\\\\").replace("*/", "* /"))}\n*/\n")
      .getOrElse("")

}
