package sttp.tapir.codegen
import sttp.tapir.codegen.BasicGenerator.{indent, mapSchemaSimpleTypeToType}
import sttp.tapir.codegen.openapi.models.OpenapiModels.{OpenapiDocument, OpenapiParameter, OpenapiPath, OpenapiRequestBody, OpenapiResponse}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaBinary,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType
}
import sttp.tapir.codegen.openapi.models.{OpenapiComponent, OpenapiSchemaType, OpenapiSecuritySchemeType}
import sttp.tapir.codegen.util.JavaEscape

case class Location(path: String, method: String) {
  override def toString: String = s"${method.toUpperCase} ${path}"
}

class EndpointGenerator {
  private def bail(msg: String)(implicit location: Location): Nothing = throw new NotImplementedError(s"$msg at $location")

  private[codegen] def allEndpoints: String = "generatedEndpoints"

  def endpointDefs(doc: OpenapiDocument): String = {
    val components = Option(doc.components).flatten
    val ge = doc.paths.flatMap(generatedEndpoints(components))
    val definitions = ge
      .map { case (name, definition) =>
        s"""|lazy val $name =
            |${indent(2)(definition)}
            |""".stripMargin
      }
      .mkString("\n")
    val allEP = s"lazy val $allEndpoints = List(${ge.map(_._1).mkString(", ")})"

    s"""|$definitions
        |
        |$allEP
        |""".stripMargin
  }

  private[codegen] def generatedEndpoints(components: Option[OpenapiComponent])(p: OpenapiPath): Seq[(String, String)] = {
    val parameters = components.map(_.parameters).getOrElse(Map.empty)
    val securitySchemes = components.map(_.securitySchemes).getOrElse(Map.empty)

    p.methods.map(_.withResolvedParentParameters(parameters, p.parameters)).map { m =>
      implicit val location: Location = Location(p.url, m.methodType)
      val definition =
        s"""|endpoint
            |  .${m.methodType}
            |  ${urlMapper(p.url, m.resolvedParameters)}
            |${indent(2)(security(securitySchemes, m.security))}
            |${indent(2)(ins(m.resolvedParameters, m.requestBody))}
            |${indent(2)(outs(m.responses))}
            |${indent(2)(tags(m.tags))}
            |""".stripMargin

      val name = m.operationId
        .getOrElse(m.methodType + p.url.capitalize)
        .split("[^0-9a-zA-Z$_]")
        .filter(_.nonEmpty)
        .zipWithIndex
        .map { case (part, 0) => part; case (part, _) => part.capitalize }
        .mkString
      (name, definition)
    }
  }

  private def urlMapper(url: String, parameters: Seq[OpenapiParameter])(implicit location: Location): String = {
    // .in(("books" / path[String]("genre") / path[Int]("year")).mapTo[BooksFromYear])
    val inPath = url.split('/').filter(_.nonEmpty) map { segment =>
      if (segment.startsWith("{")) {
        val name = segment.drop(1).dropRight(1)
        val param = parameters.find(_.name == name)
        param.fold(throw new Error(s"URLParam $name not found!")) { p =>
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
            throw new Error(s"Unknown security scheme $schemeName!")
        }
      }
  }

  private def ins(parameters: Seq[OpenapiParameter], requestBody: Option[OpenapiRequestBody])(implicit location: Location): String = {
    // .in(query[Limit]("limit").description("Maximum number of books to retrieve"))
    // .in(header[AuthToken]("X-Auth-Token"))
    val params = parameters
      .filter(_.in != "path")
      .map { param =>
        param.schema match {
          case st: OpenapiSchemaSimpleType =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            val desc = param.description.map(d => JavaEscape.escapeString(d)).fold("")(d => s""".description("$d")""")
            s""".in(${param.in}[$t]("${param.name}")$desc)"""
          case x => bail(s"Can't create non-simple params to input - found $x")
        }
      }

    val rqBody = requestBody.flatMap { b =>
      if (b.content.isEmpty) None
      else if (b.content.size != 1) bail("We can handle only one requestBody content!")
      else Some(s".in(${contentTypeMapper(b.content.head.contentType, b.content.head.schema, b.required)})")
    }

    (params ++ rqBody).mkString("\n")
  }

  private def tags(openapiTags: Option[Seq[String]]): String = {
    // .tags(List("A", "B"))
    openapiTags.map(_.distinct.mkString(".tags(List(\"", "\", \"", "\"))")).mkString
  }

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
          case x => bail(s"Can't create non-simple or array params as output (found $x)")
        }
        val req = if (required) outT else s"Option[$outT]"
        s"jsonBody[$req]"

      case "multipart/form-data" =>
        schema match {
          case _: OpenapiSchemaBinary =>
            "multipartBody"
          case schemaRef: OpenapiSchemaRef =>
            val (t, _) = mapSchemaSimpleTypeToType(schemaRef)
            s"multipartBody[$t]"
          case x => bail(s"$contentType only supports schema ref or binary. Found $x")
        }

      case x => bail(s"Not all content types supported! Found $x")
    }
  }

}
