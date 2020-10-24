package codegen

import codegen.openapi.models.OpenapiModels.{
  OpenapiDocument,
  OpenapiParameter,
  OpenapiPath,
  OpenapiRequestBody,
  OpenapiResponse,
  OpenapiResponseContent
}
import codegen.openapi.models.OpenapiSchemaType
import codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaBoolean,
  OpenapiSchemaDouble,
  OpenapiSchemaFloat,
  OpenapiSchemaInt,
  OpenapiSchemaLong,
  OpenapiSchemaObject,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType,
  OpenapiSchemaString
}

object BasicGenerator {

  def generateObjects(doc: OpenapiDocument) = {
    s"""|
        |$packageStr
        |
        |object $obbjName {
        |
        |${indent(2)(imports)}
        |
        |${indent(2)(classDefs(doc))}
        |
        |${indent(2)(endpointDefs(doc))}
        |
        |}
        |""".stripMargin
  }

  def packageStr: String = "package sttp.tapir.generated"

  def obbjName = "TapirGeneratedEndpoints"

  def imports: String =
    """import sttp.tapir._
      |import sttp.tapir.json.circe._
      |import io.circe.generic.auto._
      |""".stripMargin

  def allEndpoints: String = "generatedEndpoints"

  def indent(i: Int)(str: String): String = {
    str.lines.map(" " * i + _ + "\n").mkString
  }

  def urlMapper(url: String, parameters: Seq[OpenapiParameter]): String = {
    //.in(("books" / path[String]("genre") / path[Int]("year")).mapTo(BooksFromYear))
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

  def ins(parameters: Seq[OpenapiParameter], requestBody: Option[OpenapiRequestBody]): String = {
    //.in(query[Limit]("limit").description("Maximum number of books to retrieve"))
    //.in(header[AuthToken]("X-Auth-Token"))
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
      s"\n.in(${contentTypeMapper(b.contentType, b.schema, b.required)})"
    }

    params + rqBody
  }

  def contentTypeMapper(contentType: String, schema: OpenapiSchemaType, required: Boolean = true) = {
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
  def outs(responses: Seq[OpenapiResponse]) = {
    //.errorOut(stringBody)
    //.out(jsonBody[List[Book]])
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
  def generatedEndpoints(p: OpenapiPath): Seq[(String, String)] = {
    p.methods.map { m =>
      val definition =
        s"""|endpoint
            |  .${m.methodType}
            |  ${urlMapper(p.url, m.parameters)}
            |${indent(2)(ins(m.parameters, m.requestBody))}
            |${indent(2)(outs(m.responses))}
            |""".stripMargin

      val name = m.methodType + p.url.split('/').map(_.replace("{", "").replace("}", "").toLowerCase.capitalize).mkString
      (name, definition)
    }
  }

  private def innerTypePrinter(key: String, tp: String, optional: Boolean) = {
    val fixedType = if (optional) s"Option[$tp]" else tp
    s"$key: $fixedType"
  }

  private def mapSchemaSimpleTypeToType(osst: OpenapiSchemaSimpleType): (String, Boolean) = {
    osst match {
      case OpenapiSchemaDouble(nb) =>
        ("Double", nb)
      case OpenapiSchemaFloat(nb) =>
        ("Float", nb)
      case OpenapiSchemaInt(nb) =>
        ("Int", nb)
      case OpenapiSchemaLong(nb) =>
        ("Long", nb)
      case OpenapiSchemaString(nb) =>
        ("String", nb)
      case OpenapiSchemaBoolean(nb) =>
        ("Boolean", nb)
      case OpenapiSchemaRef(t) =>
        (t.split('/').last, false)
      case _ => throw new NotImplementedError("Not all simple types supported!")
    }
  }
  def classDefs(doc: OpenapiDocument): String = {
    val classes = doc.components.schemas.map {
      case (name, OpenapiSchemaObject(fields, required, _)) =>
        val fs = fields.map {
          case (k, st: OpenapiSchemaSimpleType) =>
            val t = mapSchemaSimpleTypeToType(st)
            innerTypePrinter(k, t._1, t._2)
          case _ => throw new NotImplementedError("Only nonnested simple types supported!")
        }
        s"""|case class $name (
            |${indent(2)(fs.mkString(",\n"))}
            |)""".stripMargin
      case _ => throw new NotImplementedError("Only objects supported!")
    }
    classes.mkString("\n")
  }

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
}
