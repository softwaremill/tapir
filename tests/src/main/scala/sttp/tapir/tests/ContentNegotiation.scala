package sttp.tapir.tests

import io.circe.generic.auto._
import sttp.model.{ContentTypeRange, HeaderNames, MediaType, StatusCode}
import sttp.tapir._
import sttp.tapir.Codec.XmlCodec
import sttp.tapir.CodecFormat.TextHtml
import sttp.tapir.json.circe.{circeCodec, jsonBody}
import sttp.tapir.tests.data.{Entity, Organization, Person}

import java.nio.charset.StandardCharsets

object ContentNegotiation {
  implicit val schemaForPerson: Schema[Person] = Schema.derived[Person]
  implicit val schemaForOrganization: Schema[Organization] = Schema.derived[Organization]

  // <name>xxx</name>
  private def fromClosedTags(tags: String): Organization = Organization(tags.split(">")(1).split("<").head)

  case class JsonCodecFormatOrganizationName() extends CodecFormat {
    override val mediaType: MediaType = MediaType.ApplicationJson.copy(otherParameters = Map("name" -> "unknown"))
  }

  implicit val xmlCodecForOrganization: XmlCodec[Organization] =
    Codec.xml(xml => DecodeResult.Value(fromClosedTags(xml)))(o => s"<name>${o.name}-xml</name>")

  implicit val htmlCodecForOrganizationUTF8: Codec[String, Organization, CodecFormat.TextHtml] =
    Codec.anyString(TextHtml())(html => DecodeResult.Value(fromClosedTags(html)))(o => s"<p>${o.name}-utf8</p>")

  implicit val htmlCodecForOrganizationISO88591: Codec[String, Organization, CodecFormat.TextHtml] =
    Codec.anyString(TextHtml())(html => DecodeResult.Value(fromClosedTags(html)))(o => s"<p>${o.name}-iso88591</p>")

  val out_json_xml_text_common_schema: PublicEndpoint[String, Unit, Organization, Any] =
    endpoint.get
      .in("content-negotiation" / "organization")
      .in(header[String](HeaderNames.Accept))
      .out(
        sttp.tapir.oneOf(
          oneOfVariant(StatusCode.Ok, jsonBody[Organization]),
          oneOfVariant(StatusCode.Ok, xmlBody[Organization]),
          oneOfVariant(StatusCode.Ok, stringBodyAnyFormat(htmlCodecForOrganizationUTF8, StandardCharsets.UTF_8)),
          oneOfVariant(StatusCode.Ok, stringBodyAnyFormat(htmlCodecForOrganizationISO88591, StandardCharsets.ISO_8859_1))
        )
      )

  val out_json_xml_different_schema: PublicEndpoint[String, Unit, Entity, Any] =
    endpoint.get
      .in("content-negotiation" / "entity")
      .in(header[String]("Accept"))
      .out(
        sttp.tapir.oneOf[Entity](
          oneOfVariant(StatusCode.Ok, jsonBody[Person]),
          oneOfVariant(StatusCode.Ok, xmlBody[Organization])
        )
      )

  val out_json_json_different_parameters: PublicEndpoint[Unit, Unit, Organization, Any] =
    endpoint.get
      .in("content-negotiation" / "organization-parameters")
      .out(
        sttp.tapir.oneOfBody(
          jsonBody[Organization], // first defining the case which should be used when there are no parameters in the `Accept` range
          jsonBody[Organization].copy(
            codec = circeCodec[Organization]
              .map(identity[Organization] _)(_.copy(name = "unknown"))
              .format(JsonCodecFormatOrganizationName())
          )
        )
      )

  val in_multipart_mixed_out_string: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get
      .in("content-negotiation" / "multipart-mixed")
      .in(sttp.tapir.oneOfBody(ContentTypeRange("multipart", "mixed", "*") -> stringBody))
      .out(stringBody)

  val out_default_json_or_xml: PublicEndpoint[Unit, Unit, Organization, Any] =
    endpoint.get
      .in("content-negotiation" / "organization")
      .out(
        sttp.tapir.oneOf(
          oneOfVariant(StatusCode.Ok, jsonBody[Organization]),
          oneOfVariant(StatusCode.Ok, xmlBody[Organization])
        )
      )

  val out_default_xml_or_json: PublicEndpoint[Unit, Unit, Organization, Any] =
    endpoint.get
      .in("content-negotiation" / "organization")
      .out(
        sttp.tapir.oneOf(
          oneOfVariant(StatusCode.Ok, xmlBody[Organization]),
          oneOfVariant(StatusCode.Ok, jsonBody[Organization])
        )
      )

  val organizationJson = "{\"name\":\"sml\"}"
  val organizationXml = "<name>sml-xml</name>"
  val organizationHtmlUtf8 = "<p>sml-utf8</p>"
  val organizationHtmlIso = "<p>sml-iso88591</p>"
}
