package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.indent
import sttp.tapir.codegen.openapi.models.OpenapiServer
import sttp.tapir.codegen.util.NameHelpers.safeVariableName

object ServersGenerator {

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
        vs.default.map(v => if (vs.`enum`.isEmpty) '"' +: v :+ '"' else safeVariableName(v))
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
        case (e, vs, d) if vs.nonEmpty => s"_$e: $e${d.map(v => s" = $e.default").getOrElse("")}"
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
    description.map(d => s"/*\n${indent(2)(d)}\n*/\n").getOrElse("")

}
