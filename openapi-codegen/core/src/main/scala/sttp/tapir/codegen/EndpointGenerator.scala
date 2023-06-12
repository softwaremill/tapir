package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.{indent, mapSchemaSimpleTypeToType}
import sttp.tapir.codegen.openapi.models.OpenapiModels.{OpenapiDocument, OpenapiParameter, OpenapiPath, OpenapiRequestBody, OpenapiResponse}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaArray, OpenapiSchemaSimpleType}

class EndpointGenerator {

  private[codegen] def allEndpoints: String = "generatedEndpoints"

  def endpointDefs(doc: OpenapiDocument): String = {
    val ge = doc.paths.flatMap(generatedEndpoints)
    val definitions = ge
      .map { case (name, definition) =>
        s"""|val $name =
            |${indent(2)(definition)}
            |""".stripMargin
      }
      .mkString("\n")
    val allEP = s"val $allEndpoints = List(${ge.map(_._1).mkString(", ")})"

    s"""|$definitions
        |
        |$allEP
        |""".stripMargin
  }

  private[codegen] def generatedEndpoints(p: OpenapiPath): Seq[(String, String)] = {
    p.methods.map { m =>
      val definition =
        s"""|endpoint
            |  .${m.methodType}
            |  ${urlMapper(p.url, m.parameters)}
            |${indent(2)(ins(m.parameters, m.requestBody))}
            |${indent(2)(outs(m.responses))}
            |${indent(2)(tags(m.tags))}
            |""".stripMargin

      val name = m.methodType + p.url.split('/').map(_.replace("{", "").replace("}", "").toLowerCase.capitalize).mkString
      (name, definition)
    }
  }

  private def urlMapper(url: String, parameters: Seq[OpenapiParameter]): String = {
    // .in(("books" / path[String]("genre") / path[Int]("year")).mapTo[BooksFromYear])
    val inPath = url.split('/').filter(_.nonEmpty) map { segment =>
      if (segment.startsWith("{")) {
        val name = segment.drop(1).dropRight(1)
        val param = parameters.find(_.name == name)
        param.fold(throw new Error("URLParam not found!")) { p =>
          p.schema match {
            case st: OpenapiSchemaSimpleType =>
              val (t, _) = mapSchemaSimpleTypeToType(st)
              val desc = p.description.fold("")(d => s""".description("$d")""")
              s"""path[$t]("$name")$desc"""
            case _ => throw new NotImplementedError("Can't create non-simple params to url yet")
          }
        }
      } else {
        '"' + segment + '"'
      }
    }
    ".in((" + inPath.mkString(" / ") + "))"
  }

  private def ins(parameters: Seq[OpenapiParameter], requestBody: Option[OpenapiRequestBody]): String = {
    // .in(query[Limit]("limit").description("Maximum number of books to retrieve"))
    // .in(header[AuthToken]("X-Auth-Token"))
    val params = parameters
      .filter(_.in != "path")
      .map { param =>
        param.schema match {
          case st: OpenapiSchemaSimpleType =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            val desc = param.description.fold("")(d => s""".description("$d")""")
            s""".in(${param.in}[$t]("${param.name}")$desc)"""
          case _ => throw new NotImplementedError("Can't create non-simple params to input")
        }
      }
      .mkString("\n")

    val rqBody = requestBody.fold("") { b =>
      if (b.content.size != 1) throw new NotImplementedError("We can handle only one requestBody content!")
      s"\n.in(${contentTypeMapper(b.content.head.contentType, b.content.head.schema, b.required)})"
    }

    params + rqBody
  }

  private def tags(openapiTags: Option[Seq[String]]): String = {
    // .tags(List("A", "B"))
    openapiTags.map(_.distinct.mkString(".tags(List(\"", "\", \"", "\"))")).mkString
  }

  private def outs(responses: Seq[OpenapiResponse]) = {
    // .errorOut(stringBody)
    // .out(jsonBody[List[Book]])
    responses
      .map { resp =>
        if (resp.content.size != 1) throw new NotImplementedError("We can handle only one return content!")
        resp.code match {
          case "200" =>
            val content = resp.content.head
            s".out(${contentTypeMapper(content.contentType, content.schema)})"
          case "default" =>
            val content = resp.content.head
            s".errorOut(${contentTypeMapper(content.contentType, content.schema)})"
          case _ =>
            throw new NotImplementedError("Statuscode mapping is incomplete!")
        }
      }
      .sorted
      .mkString("\n")
  }

  private def contentTypeMapper(contentType: String, schema: OpenapiSchemaType, required: Boolean = true) = {
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
          case _ => throw new NotImplementedError("Can't create non-simple or array params as output")
        }
        val req = if (required) outT else s"Option[$outT]"
        s"jsonBody[$req]"
      case _ => throw new NotImplementedError("We only handle json and text!")
    }
  }

}
