package sttp.tapir.codegen.openapi.models

import cats.syntax.either._

// https://swagger.io/specification/
object OpenapiModels {

  case class OpenapiDocument(
      openapi: String,
      // not used so not parsed; servers, contact, license, termsOfService
      info: OpenapiInfo,
      paths: Seq[OpenapiPath],
      components: Option[OpenapiComponent]
  )

  case class OpenapiInfo(
      // not used so not parsed; description
      title: String,
      version: String
  )

  case class OpenapiPath(
      url: String,
      methods: Seq[OpenapiPathMethod],
      parameters: Seq[OpenapiParameter] = Nil
  )

  case class OpenapiPathMethod(
      methodType: String,
      parameters: Seq[OpenapiParameter],
      responses: Seq[OpenapiResponse],
      requestBody: Option[OpenapiRequestBody],
      summary: Option[String] = None,
      tags: Option[Seq[String]] = None,
      operationId: Option[String] = None
  ) {
    def withParentParameters(pathParameters: Seq[OpenapiParameter]): OpenapiPathMethod =
      this.copy(parameters = pathParameters.filterNot(p => parameters.exists(p.name == _.name)) ++ parameters)
  }

  case class OpenapiParameter(
      name: String,
      in: String,
      required: Boolean,
      description: Option[String],
      schema: OpenapiSchemaType
  )

  case class OpenapiResponse(
      code: String,
      description: String,
      content: Seq[OpenapiResponseContent]
  )

  case class OpenapiRequestBody(
      required: Boolean,
      description: Option[String],
      content: Seq[OpenapiRequestBodyContent]
  )

  case class OpenapiResponseContent(
      contentType: String,
      schema: OpenapiSchemaType
  )

  case class OpenapiRequestBodyContent(
      contentType: String,
      schema: OpenapiSchemaType
  )

  // ///////////////////////////////////////////////////////
  // decoders
  // //////////////////////////////////////////////////////

  import io.circe._
  import io.circe.generic.semiauto._

  implicit class RicherACursor(c: ACursor) {
    def removeIfPresent(keys: String*): ACursor = keys.foldLeft(c) { (cursor, key) =>
      val withKeyFocus = cursor.downField(key)
      withKeyFocus.focus.fold(cursor)(_ => withKeyFocus.delete)
    }
  }

  implicit val OpenapiResponseContentDecoder: Decoder[Seq[OpenapiResponseContent]] = { (c: HCursor) =>
    case class Holder(d: OpenapiSchemaType)
    implicit val InnerDecoder: Decoder[Holder] = { (c: HCursor) =>
      for {
        schema <- c.downField("schema").as[OpenapiSchemaType]
      } yield {
        Holder(schema)
      }
    }
    for {
      responses <- c.as[Map[String, Holder]]
    } yield {
      responses.map { case (ct, s) => OpenapiResponseContent(ct, s.d) }.toSeq
    }
  }

  implicit val OpenapiResponseDecoder: Decoder[Seq[OpenapiResponse]] = { (c: HCursor) =>
    implicit val InnerDecoder: Decoder[(String, Seq[OpenapiResponseContent])] = { (c: HCursor) =>
      for {
        description <- c.downField("description").as[String]
        content <- c.downField("content").as[Seq[OpenapiResponseContent]]
      } yield {
        (description, content)
      }
    }
    for {
      schema <- c.as[Map[String, (String, Seq[OpenapiResponseContent])]]
    } yield {
      schema.map { case (code, (desc, content)) =>
        OpenapiResponse(code, desc, content)
      }.toSeq
    }
  }

  implicit val OpenapiRequestBodyContentDecoder: Decoder[Seq[OpenapiRequestBodyContent]] = { (c: HCursor) =>
    case class Holder(d: OpenapiSchemaType)
    implicit val InnerDecoder: Decoder[Holder] = { (c: HCursor) =>
      for {
        schema <- c.downField("schema").as[OpenapiSchemaType]
      } yield {
        Holder(schema)
      }
    }
    for {
      responses <- c.as[Map[String, Holder]]
    } yield {
      responses.map { case (ct, s) => OpenapiRequestBodyContent(ct, s.d) }.toSeq
    }
  }

  implicit val OpenapiRequestBodyDecoder: Decoder[OpenapiRequestBody] = { (c: HCursor) =>
    implicit val InnerDecoder: Decoder[(String, Seq[OpenapiRequestBodyContent])] = { (c: HCursor) =>
      for {
        description <- c.downField("description").as[String]
        content <- c.downField("content").as[Seq[OpenapiRequestBodyContent]]
      } yield {
        (description, content)
      }
    }
    for {
      requiredOpt <- c.downField("required").as[Option[Boolean]]
      description <- c.downField("description").as[Option[String]]
      content <- c.downField("content").as[Seq[OpenapiRequestBodyContent]]
    } yield {
      OpenapiRequestBody(required = requiredOpt.getOrElse(false), description, content)
    }
  }

  implicit val OpenapiInfoDecoder: Decoder[OpenapiInfo] = deriveDecoder[OpenapiInfo]
  implicit val OpenapiParameterDecoder: Decoder[OpenapiParameter] = deriveDecoder[OpenapiParameter]
  implicit val OpenapiPathMethodDecoder: Decoder[Seq[OpenapiPathMethod]] = { (c: HCursor) =>
    implicit val InnerDecoder: Decoder[
      (
          Seq[OpenapiParameter],
          Seq[OpenapiResponse],
          Option[OpenapiRequestBody],
          Option[String],
          Option[Seq[String]],
          Option[String]
      )
    ] = { (c: HCursor) =>
      for {
        parameters <- c.downField("parameters").as[Seq[OpenapiParameter]].orElse(Right(List.empty[OpenapiParameter]))
        responses <- c.downField("responses").as[Seq[OpenapiResponse]]
        requestBody <- c.downField("requestBody").as[Option[OpenapiRequestBody]]
        summary <- c.downField("summary").as[Option[String]]
        tags <- c.downField("tags").as[Option[Seq[String]]]
        operationId <- c.downField("operationId").as[Option[String]]
      } yield {
        (parameters, responses, requestBody, summary, tags, operationId)
      }
    }
    for {
      methods <- c.as[
        Map[
          String,
          (
              Seq[OpenapiParameter],
              Seq[OpenapiResponse],
              Option[OpenapiRequestBody],
              Option[String],
              Option[Seq[String]],
              Option[String],
          )
        ]
      ]
    } yield {
      methods.map { case (t, (p, r, rb, s, tg, oid)) => OpenapiPathMethod(t, p, r, rb, s, tg, oid) }.toSeq
    }
  }

  implicit val OpenapiPathDecoder: Decoder[OpenapiPath] = { (c: HCursor) =>
    for {
      parameters <- c.downField("parameters").as[Seq[OpenapiParameter]].orElse(Right(List.empty[OpenapiParameter]))
      methods <- c.removeIfPresent("parameters", "$ref", "summary", "description", "servers").as[Seq[OpenapiPathMethod]]
    } yield OpenapiPath("--partial--", methods, parameters)
  }

  implicit val OpenapiPathsDecoder: Decoder[Seq[OpenapiPath]] = { (c: HCursor) =>
    for {
      paths <- c.as[Map[String, OpenapiPath]]
    } yield {
      paths.map { case (url, path) => path.copy(url = url) }.toSeq
    }
  }

  implicit val OpenapiDocumentDecoder: Decoder[OpenapiDocument] = { (c: HCursor) =>
    for {
      openapi <- c.downField("openapi").as[String]
      info <- c.downField("info").as[OpenapiInfo]
      paths <- c.downField("paths").as[Seq[OpenapiPath]]
      components <- c.downField("components").as[Option[OpenapiComponent]].orElse(Right(None))
    } yield OpenapiDocument(openapi, info, paths, components)
  }

}
