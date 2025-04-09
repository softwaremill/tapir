package sttp.tapir.codegen.openapi.models

import cats.implicits.toTraverseOps
import cats.syntax.either._
import OpenapiSchemaType.{OpenapiSchemaRef, OpenapiSchemaRefDecoder, OpenapiSchemaSimpleType}
import io.circe.Json
import sttp.tapir.codegen.BasicGenerator.strippedToCamelCase
import sttp.tapir.codegen.util.MapUtils
// https://swagger.io/specification/
object OpenapiModels {

  sealed trait Resolvable[T] {
    def resolve(input: Map[String, T]): T
    def toResolved(input: Map[String, T]): Resolved[T] = Resolved(resolve(input))
  }
  case class Resolved[T](t: T) extends Resolvable[T] {
    override def resolve(input: Map[String, T]): T = t
  }
  case class Ref[T](name: String) extends Resolvable[T] {
    override def resolve(input: Map[String, T]): T = input.getOrElse(name, throw new IllegalArgumentException(s"Cannot resolve $name"))
  }

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
      parameters: Seq[Resolvable[OpenapiParameter]] = Nil,
      specificationExtensions: Map[String, Json] = Map.empty
  )

  case class OpenapiPathMethod(
      methodType: String,
      parameters: Seq[Resolvable[OpenapiParameter]],
      responses: Seq[OpenapiResponse],
      requestBody: Option[OpenapiRequestBody],
      security: Map[String, Seq[String]] = Map.empty,
      summary: Option[String] = None,
      tags: Option[Seq[String]] = None,
      operationId: Option[String] = None,
      specificationExtensions: Map[String, Json] = Map.empty
  ) {
    def name(url: String) = strippedToCamelCase(operationId.getOrElse(methodType + url.capitalize))
    def resolvedParameters: Seq[OpenapiParameter] = parameters.collect { case Resolved(t) => t }
    def withResolvedParentParameters(
        pMap: Map[String, OpenapiParameter],
        pathParameters: Seq[Resolvable[OpenapiParameter]]
    ): OpenapiPathMethod = {
      val resolved = parameters.map(_.toResolved(pMap))
      val duplicates = resolved.groupBy(_.t.name).filter(_._2.size > 1).keys
      if (duplicates.nonEmpty) throw new IllegalArgumentException(s"Duplicate parameters ${duplicates.mkString(", ")}")
      val filteredParents: Seq[Resolved[OpenapiParameter]] =
        pathParameters.map(_.toResolved(pMap)).filterNot(p => resolved.exists(p.t.name == _.t.name))
      val parentDuplicates = filteredParents.groupBy(_.t.name).filter(_._2.size > 1).keys
      if (parentDuplicates.nonEmpty) throw new IllegalArgumentException(s"Duplicate parameters ${parentDuplicates.mkString(", ")}")
      this.copy(parameters = filteredParents ++ resolved)
    }
    val tapirCodegenDirectives: Set[String] = {
      specificationExtensions
        .collect { case (GenerationDirectives.extensionKey, json) => json.asArray.toSeq.flatMap(_.flatMap(_.asString)) }
        .flatten
        .toSet
    }
  }

  case class OpenapiParameter(
      name: String,
      in: String,
      required: Option[Boolean],
      description: Option[String],
      schema: OpenapiSchemaType,
      explode: Option[Boolean] = None
  ) {
    // default is true for query params, but headers must always be 'simple' style -- see https://swagger.io/docs/specification/serialization/
    def isExploded: Boolean = in != "header" && !explode.contains(false)
  }

  sealed trait OpenapiHeader {
    def resolved(name: String, doc: OpenapiDocument): OpenapiHeaderDef
  }
  object OpenapiHeader {
    import io.circe._
    implicit val OpenapiHeaderDecoder: Decoder[OpenapiHeader] =
      OpenapiSchemaRefDecoder
        .map(OpenapiHeaderRef(_))
        .or((c: HCursor) => {
          OpenapiParameterDecoder
            .tryDecode(c.withFocus(_.mapObject(("name" -> Json.fromString("inline")) +: ("in" -> Json.fromString("header")) +: _)))
            .map(OpenapiHeaderDef(_))
        })
  }
  case class OpenapiHeaderDef(param: OpenapiParameter) extends OpenapiHeader {
    def resolved(name: String, doc: OpenapiDocument): OpenapiHeaderDef =
      if (name == param.name) this else OpenapiHeaderDef(param.copy(name = name))
  }
  case class OpenapiHeaderRef($ref: OpenapiSchemaRef) extends OpenapiHeader {
    def resolved(name: String, doc: OpenapiDocument): OpenapiHeaderDef = {
      doc.components
        .flatMap(_.parameters.get($ref.name))
        .map(b => if (b.in != "header") throw new IllegalStateException(s"Referenced parameter ${$ref.name} is not header") else b)
        .map(b => OpenapiHeaderDef(b.copy(name = name)))
        .getOrElse(throw new IllegalStateException(s"Response component ${$ref.name} is referenced but not found"))
    }
  }

  sealed trait OpenapiResponse {
    def code: String
    def resolve(doc: OpenapiDocument): OpenapiResponseDef
  }
  case class OpenapiResponseDef(
      code: String,
      description: String,
      content: Seq[OpenapiResponseContent],
      headers: Map[String, OpenapiHeader] = Map.empty
  ) extends OpenapiResponse {
    def resolve(doc: OpenapiDocument): OpenapiResponseDef = this
  }
  case class OpenapiResponseRef(
      code: String,
      $ref: OpenapiSchemaRef
  ) extends OpenapiResponse {
    def strippedRef: String = $ref.name.stripPrefix("#/components/responses/")
    def resolve(doc: OpenapiDocument): OpenapiResponseDef =
      doc.components
        .flatMap(_.responses.get(strippedRef))
        .map(b => OpenapiResponseDef(code, b.description, b.content, b.headers))
        .getOrElse(throw new IllegalStateException(s"Response component ${$ref.name} is referenced but not found"))
  }

  sealed trait OpenapiRequestBody {
    def resolve(doc: OpenapiDocument): OpenapiRequestBodyDefn
  }
  case class OpenapiRequestBodyDefn(
      required: Boolean,
      description: Option[String],
      content: Seq[OpenapiRequestBodyContent]
  ) extends OpenapiRequestBody {
    def resolve(doc: OpenapiDocument): OpenapiRequestBodyDefn = this
  }
  case class OpenapiRequestRef(
      $ref: OpenapiSchemaRef
  ) extends OpenapiRequestBody {
    def strippedRef: String = $ref.name.stripPrefix("#/components/requestBodies/")
    def resolve(doc: OpenapiDocument): OpenapiRequestBodyDefn =
      doc.components
        .flatMap(_.requestBodies.get(strippedRef))
        .map(b => OpenapiRequestBodyDefn(b.required, Some(b.description), b.content))
        .getOrElse(throw new IllegalStateException(s"requestBody component ${$ref.name} is referenced but not found"))
  }

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
    implicit val InnerDecoder: Decoder[(String, Option[Seq[OpenapiResponseContent]], Map[String, OpenapiHeader])] = { (c: HCursor) =>
      for {
        description <- c.downField("description").as[String]
        content <- c.downField("content").as[Option[Seq[OpenapiResponseContent]]]
        headers <- c.getOrElse[Map[String, OpenapiHeader]]("headers")(Map.empty)
      } yield {
        (description, content, headers)
      }
    }
    implicit val EitherDecoder
        : Decoder[Either[OpenapiSchemaRef, (String, Option[Seq[OpenapiResponseContent]], Map[String, OpenapiHeader])]] =
      InnerDecoder.map(Right(_)).or(OpenapiSchemaRefDecoder.map(Left(_)))

    for {
      schema <- c
        .as[Map[String, Either[OpenapiSchemaRef, (String, Option[Seq[OpenapiResponseContent]], Map[String, OpenapiHeader])]]]
    } yield {
      schema.map {
        case (code, Right((desc, content, headers))) =>
          OpenapiResponseDef(code, desc, content.getOrElse(Nil), headers)
        case (code, Left(ref)) =>
          OpenapiResponseRef(code, ref)
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
      requestBodies <- c.as[Map[String, Holder]]
    } yield {
      requestBodies.map { case (ct, s) => OpenapiRequestBodyContent(ct, s.d) }.toSeq
    }
  }

  implicit val OpenapiRequestBodyDefnDecoder: Decoder[OpenapiRequestBodyDefn] = { (c: HCursor) =>
    for {
      requiredOpt <- c.downField("required").as[Option[Boolean]]
      description <- c.downField("description").as[Option[String]]
      content <- c.downField("content").as[Seq[OpenapiRequestBodyContent]]
    } yield {
      OpenapiRequestBodyDefn(required = requiredOpt.getOrElse(false), description, content)
    }
  }
  implicit val OpenapiRequestBodyDecoder: Decoder[OpenapiRequestBody] =
    OpenapiRequestBodyDefnDecoder.or(OpenapiSchemaRefDecoder.map(OpenapiRequestRef(_)))

  implicit val OpenapiInfoDecoder: Decoder[OpenapiInfo] = deriveDecoder[OpenapiInfo]
  implicit val OpenapiParameterDecoder: Decoder[OpenapiParameter] = deriveDecoder[OpenapiParameter]
  implicit def ResolvableDecoder[T: Decoder]: Decoder[Resolvable[T]] = { (c: HCursor) =>
    c.as[T].map(Resolved(_)).orElse(c.as[OpenapiSchemaRef].map(r => Ref(r.name)))
  }

  implicit val PartialOpenapiPathMethodDecoder: Decoder[OpenapiPathMethod] = { (c: HCursor) =>
    for {
      parameters <- c.getOrElse[Seq[Resolvable[OpenapiParameter]]]("parameters")(Nil)
      responses <- c.get[Seq[OpenapiResponse]]("responses")
      requestBody <- c.get[Option[OpenapiRequestBody]]("requestBody")
      security <- c.getOrElse[Seq[Map[String, Seq[String]]]]("security")(Nil)
      summary <- c.get[Option[String]]("summary")
      tags <- c.get[Option[Seq[String]]]("tags")
      operationId <- c.get[Option[String]]("operationId")
      specificationExtensionKeys = c.keys.toSeq.flatMap(_.filter(_.startsWith("x-")))
      specificationExtensions = specificationExtensionKeys
        .flatMap(key => c.downField(key).as[Option[Json]].toOption.flatten.map(key.stripPrefix("x-") -> _))
        .toMap
    } yield {
      OpenapiPathMethod(
        "--partial--",
        parameters,
        responses,
        requestBody,
        // This probably isn't the right semantics -- since it's a list of name-array pairs rather than a map, I assume
        // that the intended semantics are DNF. But we don't do anything with the scopes anyway for now.
        security.foldLeft(Map.empty[String, Seq[String]])(MapUtils.merge),
        summary,
        tags,
        operationId,
        specificationExtensions
      )
    }
  }

  implicit val PartialOpenapiPathDecoder: Decoder[OpenapiPath] = { (c: HCursor) =>
    for {
      parameters <- c
        .downField("parameters")
        .as[Option[Seq[Resolvable[OpenapiParameter]]]]
        .map(_.getOrElse(Nil))
      methods <- List("get", "put", "post", "delete", "options", "head", "patch", "connect", "trace")
        .traverse(method => c.downField(method).as[Option[OpenapiPathMethod]].map(_.map(_.copy(methodType = method))))
      specificationExtensionKeys = c.keys.toSeq.flatMap(_.filter(_.startsWith("x-")))
      specificationExtensions = specificationExtensionKeys
        .flatMap(key => c.downField(key).as[Option[Json]].toOption.flatten.map(key.stripPrefix("x-") -> _))
        .toMap
    } yield OpenapiPath("--partial--", methods.flatten, parameters, specificationExtensions)
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
      components <- c.downField("components").as[Option[OpenapiComponent]]
    } yield OpenapiDocument(openapi, info, paths, components)
  }

}
