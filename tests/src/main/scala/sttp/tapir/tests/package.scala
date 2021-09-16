package sttp.tapir

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import io.circe.generic.auto._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import com.softwaremill.tagging.{@@, Tagger}
import io.circe.{Decoder, Encoder}
import sttp.capabilities.Streams
import sttp.model.{Header, HeaderNames, MediaType, Part, QueryParams, StatusCode}
import sttp.model.headers.{Cookie, CookieValueWithMeta, CookieWithMeta}
import sttp.tapir.Codec.{PlainCodec, XmlCodec}
import sttp.tapir.CodecFormat.TextHtml
import sttp.tapir.model._

package object tests {
  val in_query_out_string: Endpoint[String, Unit, String, Any] = endpoint.in(query[String]("fruit")).out(stringBody)

  val in_query_out_infallible_string: Endpoint[String, Nothing, String, Any] =
    infallibleEndpoint.in(query[String]("fruit")).out(stringBody).name("infallible")

  val in_query_query_out_string: Endpoint[(String, Option[Int]), Unit, String, Any] =
    endpoint.in(query[String]("fruit")).in(query[Option[Int]]("amount")).out(stringBody)

  val in_header_out_string: Endpoint[String, Unit, String, Any] = endpoint.in(header[String]("X-Role")).out(stringBody)

  val in_path_path_out_string: Endpoint[(String, Int), Unit, String, Any] =
    endpoint.in("fruit" / path[String] / "amount" / path[Int]).out(stringBody)

  val in_two_path_capture: Endpoint[(Int, Int), Unit, (Int, Int), Any] = endpoint
    .in("in" / path[Int] / path[Int])
    .out(header[Int]("a") and header[Int]("b"))

  val in_string_out_string: Endpoint[String, Unit, String, Any] = endpoint.post.in("api" / "echo").in(stringBody).out(stringBody)

  val in_path: Endpoint[String, Unit, Unit, Any] = endpoint.get.in("api").in(path[String])

  val in_fixed_header_out_string: Endpoint[Unit, Unit, String, Any] =
    endpoint.in("secret").in(header("location", "secret")).out(stringBody)

  val in_mapped_query_out_string: Endpoint[List[Char], Unit, String, Any] =
    endpoint.in(query[String]("fruit").map(_.toList)(_.mkString(""))).out(stringBody).name("mapped query")

  val in_mapped_path_out_string: Endpoint[Fruit, Unit, String, Any] =
    endpoint.in(("fruit" / path[String]).mapTo[Fruit]).out(stringBody).name("mapped path")

  val in_mapped_path_path_out_string: Endpoint[FruitAmount, Unit, String, Any] =
    endpoint.in(("fruit" / path[String] / "amount" / path[Int]).mapTo[FruitAmount]).out(stringBody).name("mapped path path")

  val in_query_mapped_path_path_out_string: Endpoint[(FruitAmount, String), Unit, String, Any] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo[FruitAmount])
    .in(query[String]("color"))
    .out(stringBody)
    .name("query and mapped path path")

  val in_query_out_mapped_string: Endpoint[String, Unit, List[Char], Any] =
    endpoint.in(query[String]("fruit")).out(stringBody.map(_.toList)(_.mkString(""))).name("out mapped")

  val in_query_out_mapped_string_header: Endpoint[String, Unit, FruitAmount, Any] = endpoint
    .in(query[String]("fruit"))
    .out(stringBody.and(header[Int]("X-Role")).mapTo[FruitAmount])
    .name("out mapped")

  val in_header_before_path: Endpoint[(String, Int), Unit, (Int, String), Any] = endpoint
    .in(header[String]("SomeHeader"))
    .in(path[Int])
    .out(header[Int]("IntHeader") and stringBody)

  val in_json_out_json: Endpoint[FruitAmount, Unit, FruitAmount, Any] =
    endpoint.post
      .in("api" / "echo")
      .in(jsonBody[FruitAmount])
      .out(jsonBody[FruitAmount])
      .name("echo json")

  val in_content_type_fixed_header: Endpoint[Unit, Unit, Unit, Any] =
    endpoint.post
      .in("api" / "echo")
      .in(header(Header.contentType(MediaType.ApplicationJson)))

  implicit val mediaTypeCodec: Codec[String, MediaType, CodecFormat.TextPlain] =
    Codec.string.mapDecode(_ => DecodeResult.Mismatch("", ""))(_.toString())
  val in_content_type_header_with_custom_decode_results: Endpoint[MediaType, Unit, Unit, Any] =
    endpoint.post
      .in("api" / "echo")
      .in(header[MediaType]("Content-Type"))

  val in_byte_array_out_byte_array: Endpoint[Array[Byte], Unit, Array[Byte], Any] =
    endpoint.post.in("api" / "echo").in(byteArrayBody).out(byteArrayBody).name("echo byte array")

  val in_byte_buffer_out_byte_buffer: Endpoint[ByteBuffer, Unit, ByteBuffer, Any] =
    endpoint.post.in("api" / "echo").in(byteBufferBody).out(byteBufferBody).name("echo byte buffer")

  val in_input_stream_out_input_stream: Endpoint[InputStream, Unit, InputStream, Any] =
    endpoint.post.in("api" / "echo").in(inputStreamBody).out(inputStreamBody).name("echo input stream")

  val in_string_out_stream_with_header: Endpoint[String, Unit, (InputStream, Option[Long]), Any] =
    endpoint.post
      .in("api" / "echo")
      .in(stringBody)
      .out(inputStreamBody)
      .out(header[Option[Long]]("Content-Length"))
      .name("input string output stream with header")

  val in_file_out_file: Endpoint[TapirFile, Unit, TapirFile, Any] =
    endpoint.post.in("api" / "echo").in(fileBody).out(fileBody).name("echo file")

  val in_unit_out_json_unit: Endpoint[Unit, Unit, Unit, Any] =
    endpoint.in("api" / "unit").out(jsonBody[Unit])

  val in_unit_out_string: Endpoint[Unit, Unit, String, Any] =
    endpoint.in("api").out(stringBody)

  val in_unit_error_out_string: Endpoint[Unit, String, Unit, Any] =
    endpoint.in("api").errorOut(stringBody)

  val in_form_out_form: Endpoint[FruitAmount, Unit, FruitAmount, Any] =
    endpoint.post.in("api" / "echo").in(formBody[FruitAmount]).out(formBody[FruitAmount])

  val in_query_params_out_string: Endpoint[QueryParams, Unit, String, Any] =
    endpoint.get.in("api" / "echo" / "params").in(queryParams).out(stringBody)

  val in_headers_out_headers: Endpoint[List[Header], Unit, List[Header], Any] =
    endpoint.get.in("api" / "echo" / "headers").in(headers).out(headers)

  val in_json_out_headers: Endpoint[FruitAmount, Unit, List[Header], Any] =
    endpoint.get.in("api" / "echo" / "headers").in(jsonBody[FruitAmount]).out(headers)

  val in_paths_out_string: Endpoint[List[String], Unit, String, Any] =
    endpoint.get.in(paths).out(stringBody)

  val in_path_paths_out_header_body: Endpoint[(Int, List[String]), Unit, (Int, String), Any] =
    endpoint.get.in("api").in(path[Int]).in("and").in(paths).out(header[Int]("IntPath") and stringBody)

  val in_path_fixed_capture_fixed_capture: Endpoint[(Int, Int), Unit, Unit, Any] =
    endpoint.get.in("customer" / path[Int]("customer_id") / "orders" / path[Int]("order_id"))

  val in_query_list_out_header_list: Endpoint[List[String], Unit, List[String], Any] =
    endpoint.get.in("api" / "echo" / "param-to-header").in(query[List[String]]("qq")).out(header[List[String]]("hh"))

  def in_stream_out_stream[S](s: Streams[S]): Endpoint[s.BinaryStream, Unit, s.BinaryStream, S] = {
    val sb = streamTextBody(s)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))
    endpoint.post.in("api" / "echo").in(sb).out(sb)
  }

  def in_stream_out_stream_with_content_length[S](s: Streams[S]): Endpoint[(Long, s.BinaryStream), Unit, (Long, s.BinaryStream), S] = {
    val sb = streamTextBody[S](s)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))
    endpoint.post.in("api" / "echo").in(header[Long](HeaderNames.ContentLength)).in(sb).out(header[Long](HeaderNames.ContentLength)).out(sb)
  }

  val in_simple_multipart_out_multipart: Endpoint[FruitAmount, Unit, FruitAmount, Any] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitAmount]).out(multipartBody[FruitAmount]).name("echo simple")

  val in_simple_multipart_out_string: Endpoint[FruitAmount, Unit, String, Any] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitAmount]).out(stringBody)

  val in_simple_multipart_out_raw_string: Endpoint[FruitAmountWrapper, Unit, String, Any] = {
    endpoint.post.in("api" / "echo").in(multipartBody[FruitAmountWrapper]).out(stringBody)
  }

  val in_file_multipart_out_multipart: Endpoint[FruitData, Unit, FruitData, Any] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitData]).out(multipartBody[FruitData]).name("echo file")

  val in_raw_multipart_out_string: Endpoint[Seq[Part[Array[Byte]]], Unit, String, Any] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody).out(stringBody).name("echo raw parts")

  val in_cookie_cookie_out_header: Endpoint[(Int, String), Unit, List[String], Any] =
    endpoint.get
      .in("api" / "echo" / "headers")
      .in(cookie[Int]("c1"))
      .in(cookie[String]("c2"))
      .out(header[List[String]]("Set-Cookie"))

  val in_cookies_out_cookies: Endpoint[List[Cookie], Unit, List[CookieWithMeta], Any] =
    endpoint.get.in("api" / "echo" / "headers").in(cookies).out(setCookies)

  val in_set_cookie_value_out_set_cookie_value: Endpoint[CookieValueWithMeta, Unit, CookieValueWithMeta, Any] =
    endpoint.get.in("api" / "echo" / "headers").in(setCookie("c1")).out(setCookie("c1"))

  val in_root_path: Endpoint[Unit, Unit, Unit, Any] = endpoint.get.in("")

  val in_single_path: Endpoint[Unit, Unit, Unit, Any] = endpoint.get.in("api")

  val in_extract_request_out_string: Endpoint[String, Unit, String, Any] =
    endpoint.in(extractFromRequest(_.method.method)).out(stringBody)

  val in_auth_apikey_header_out_string: Endpoint[String, Unit, String, Any] =
    endpoint.in("auth").in(auth.apiKey(header[String]("X-Api-Key"))).out(stringBody)

  val in_auth_apikey_query_out_string: Endpoint[String, Unit, String, Any] =
    endpoint.in("auth").in(auth.apiKey(query[String]("api-key"))).out(stringBody)

  val in_auth_basic_out_string: Endpoint[UsernamePassword, Unit, String, Any] =
    endpoint.in("auth").in(auth.basic[UsernamePassword]()).out(stringBody)

  val in_auth_bearer_out_string: Endpoint[String, Unit, String, Any] = endpoint.in("auth").in(auth.bearer[String]()).out(stringBody)

  val in_string_out_status_from_string: Endpoint[String, Unit, Either[Int, String], Any] =
    endpoint
      .in(query[String]("fruit"))
      .out(
        oneOf[Either[Int, String]](
          oneOfMappingValueMatcher(StatusCode.Accepted, plainBody[Int].map(Left(_))(_.value)) { case Left(_: Int) => true },
          oneOfMappingValueMatcher(StatusCode.Ok, plainBody[String].map(Right(_))(_.value)) { case Right(_: String) => true }
        )
      )

  val in_int_out_value_form_exact_match: Endpoint[Int, Unit, String, Any] =
    endpoint
      .in("mapping")
      .in(query[Int]("num"))
      .out(
        oneOf(
          oneOfMappingExactMatcher(StatusCode.Accepted, plainBody[String])("A"),
          oneOfMappingExactMatcher(StatusCode.Ok, plainBody[String])("B")
        )
      )

  val in_string_out_status_from_type_erasure_using_partial_matcher: Endpoint[String, Unit, Option[Either[Int, String]], Any] = {
    import sttp.tapir.typelevel.MatchType

    endpoint
      .in(query[String]("fruit"))
      .out(
        oneOf[Option[Either[Int, String]]](
          oneOfMapping(StatusCode.NoContent, emptyOutput.map[None.type]((_: Unit) => None)(_ => ())),
          oneOfMappingValueMatcher(StatusCode.Accepted, jsonBody[Some[Left[Int, String]]])(
            implicitly[MatchType[Some[Left[Int, String]]]].partial
          ),
          oneOfMappingValueMatcher(StatusCode.Ok, jsonBody[Some[Right[Int, String]]])(
            implicitly[MatchType[Some[Right[Int, String]]]].partial
          )
        )
      )
  }
  val in_string_out_status_from_string_one_empty: Endpoint[String, Unit, Either[Unit, String], Any] =
    endpoint
      .in(query[String]("fruit"))
      .out(
        oneOf[Either[Unit, String]](
          oneOfMappingValueMatcher(StatusCode.Accepted, emptyOutput.map(Left(_))(_.value)) { case Left(_: Unit) => true },
          oneOfMappingValueMatcher(StatusCode.Ok, plainBody[String].map(Right(_))(_.value)) { case Right(_: String) => true }
        )
      )

  val in_string_out_status: Endpoint[String, Unit, StatusCode, Any] =
    endpoint.in(query[String]("fruit")).out(statusCode)

  val delete_endpoint: Endpoint[Unit, Unit, Unit, Any] =
    endpoint.delete.in("api" / "delete").out(statusCode(StatusCode.Ok).description("ok"))

  val in_string_out_content_type_string: Endpoint[String, Unit, (String, String), Any] =
    endpoint.in("api" / "echo").in(stringBody).out(stringBody).out(header[String]("Content-Type"))

  val in_content_type_out_string: Endpoint[String, Unit, String, Any] =
    endpoint.in("api" / "echo").in(header[String]("Content-Type")).out(stringBody)

  val in_unit_out_html: Endpoint[Unit, Unit, String, Any] =
    endpoint.in("api" / "echo").out(htmlBodyUtf8)

  val in_unit_out_header_redirect: Endpoint[Unit, Unit, String, Any] =
    endpoint.out(statusCode(StatusCode.PermanentRedirect)).out(header[String]("Location"))

  val in_unit_out_fixed_header: Endpoint[Unit, Unit, Unit, Any] =
    endpoint.out(header("Location", "Poland"))

  val in_optional_json_out_optional_json: Endpoint[Option[FruitAmount], Unit, Option[FruitAmount], Any] =
    endpoint.post.in("api" / "echo").in(jsonBody[Option[FruitAmount]]).out(jsonBody[Option[FruitAmount]])

  val in_optional_coproduct_json_out_optional_coproduct_json: Endpoint[Option[Entity], Unit, Option[Entity], Any] =
    endpoint.post.in("api" / "echo" / "coproduct").in(jsonBody[Option[Entity]]).out(jsonBody[Option[Entity]])

  val not_existing_endpoint: Endpoint[Unit, String, Unit, Any] =
    endpoint.in("api" / "not-existing").errorOut(oneOf(oneOfMapping(StatusCode.BadRequest, stringBody)))

  val in_header_out_header_unit_extended: Endpoint[(Unit, String), Unit, (Unit, String), Any] = {
    def addInputAndOutput[I, E, O](e: Endpoint[I, E, O, Any]): Endpoint[(I, String), E, (O, String), Any] = {
      e.in(header[String]("X")).out(header[String]("Y"))
    }

    addInputAndOutput(endpoint.in(header("A", "1")).out(header("B", "2")))
  }

  val in_4query_out_4header_extended: Endpoint[((String, String), String, String), Unit, ((String, String), String, String), Any] = {
    def addInputAndOutput[I, E, O](e: Endpoint[I, E, O, Any]): Endpoint[(I, String, String), E, (O, String, String), Any] = {
      e.in(query[String]("x").and(query[String]("y"))).out(header[String]("X").and(header[String]("Y")))
    }

    addInputAndOutput(endpoint.in(query[String]("a").and(query[String]("b"))).out(header[String]("A").and(header[String]("B"))))
  }

  val in_3query_out_3header_mapped_to_tuple: Endpoint[(String, String, String, String), Unit, (String, String, String, String), Any] =
    endpoint
      .in(query[String]("p1"))
      .in(query[String]("p2").map(x => (x, x))(_._1))
      .in(query[String]("p3"))
      .out(header[String]("P1"))
      .out(header[String]("P2").map(x => (x, x))(_._1))
      .out(header[String]("P3"))

  val in_2query_out_2query_mapped_to_unit: Endpoint[String, Unit, String, Any] =
    endpoint
      .in(query[String]("p1").map(_ => ())(_ => "DEFAULT_PARAM"))
      .in(query[String]("p2"))
      .out(header[String]("P1").map(_ => ())(_ => "DEFAULT_HEADER"))
      .out(header[String]("P2"))
      .name("mapped to unit")

  val in_query_with_default_out_string: Endpoint[String, Unit, String, Any] =
    endpoint
      .in(query[String]("p1").default("DEFAULT"))
      .out(stringBody)
      .name("Query with default")

  val out_fixed_content_type_header: Endpoint[Unit, Unit, String, Any] =
    endpoint.out(stringBody and header("Content-Type", "text/csv"))

  val out_json_or_default_json: Endpoint[String, Unit, Entity, Any] =
    endpoint.get
      .in("entity" / path[String]("type"))
      .out(
        oneOf[Entity](
          oneOfMapping[Person](StatusCode.Created, jsonBody[Person]),
          oneOfDefaultMapping[Organization](jsonBody[Organization])
        )
      )

  val out_no_content_or_ok_empty_output: Endpoint[Int, Unit, Unit, Any] = {
    val anyMatches: PartialFunction[Any, Boolean] = { case _ => true }

    endpoint
      .in("status")
      .in(query[Int]("statusOut"))
      .out(
        sttp.tapir.oneOf(
          oneOfMappingValueMatcher(StatusCode.NoContent, emptyOutput)(anyMatches),
          oneOfMappingValueMatcher(StatusCode.Ok, emptyOutput)(anyMatches)
        )
      )
  }

  val out_json_or_empty_output_no_content: Endpoint[Int, Unit, Either[Unit, Person], Any] =
    endpoint
      .in("status")
      .in(query[Int]("statusOut"))
      .out(
        sttp.tapir.oneOf[Either[Unit, Person]](
          oneOfMappingValueMatcher(StatusCode.NoContent, jsonBody[Person].map(Right(_))(_ => Person("", 0))) { case Person(_, _) => true },
          oneOfMappingValueMatcher(StatusCode.NoContent, emptyOutput.map(Left(_))(_ => ())) { case () => true }
        )
      )

  //

  object MultipleMediaTypes {
    implicit val schemaForPerson: Schema[Person] = Schema.derived[Person]
    implicit val schemaForOrganization: Schema[Organization] = Schema.derived[Organization]

    // <name>xxx</name>
    def fromClosedTags(tags: String): Organization = Organization(tags.split(">")(1).split("<").head)

    implicit val xmlCodecForOrganization: XmlCodec[Organization] =
      Codec.xml(xml => DecodeResult.Value(fromClosedTags(xml)))(o => s"<name>${o.name}-xml</name>")

    implicit val htmlCodecForOrganizationUTF8: Codec[String, Organization, CodecFormat.TextHtml] =
      Codec.anyStringCodec(TextHtml())(html => DecodeResult.Value(fromClosedTags(html)))(o => s"<p>${o.name}-utf8</p>")

    implicit val htmlCodecForOrganizationISO88591: Codec[String, Organization, CodecFormat.TextHtml] =
      Codec.anyStringCodec(TextHtml())(html => DecodeResult.Value(fromClosedTags(html)))(o => s"<p>${o.name}-iso88591</p>")

    val out_json_xml_text_common_schema: Endpoint[String, Unit, Organization, Any] =
      endpoint.get
        .in("content-negotiation" / "organization")
        .in(header[String](HeaderNames.Accept))
        .out(
          sttp.tapir.oneOf(
            oneOfMapping(StatusCode.Ok, jsonBody[Organization]),
            oneOfMapping(StatusCode.Ok, xmlBody[Organization]),
            oneOfMapping(StatusCode.Ok, anyFromStringBody(htmlCodecForOrganizationUTF8, StandardCharsets.UTF_8)),
            oneOfMapping(StatusCode.Ok, anyFromStringBody(htmlCodecForOrganizationISO88591, StandardCharsets.ISO_8859_1))
          )
        )

    val out_json_xml_different_schema: Endpoint[String, Unit, Entity, Any] =
      endpoint.get
        .in("content-negotiation" / "entity")
        .in(header[String]("Accept"))
        .out(
          sttp.tapir.oneOf[Entity](
            oneOfMapping(StatusCode.Ok, jsonBody[Person]),
            oneOfMapping(StatusCode.Ok, xmlBody[Organization])
          )
        )

    val organizationJson = "{\"name\":\"sml\"}"
    val organizationXml = "<name>sml-xml</name>"
    val organizationHtmlUtf8 = "<p>sml-utf8</p>"
    val organizationHtmlIso = "<p>sml-iso88591</p>"
  }

  //

  object Validation {
    type MyTaggedString = String @@ Tapir

    val in_query_tagged: Endpoint[String @@ Tapir, Unit, Unit, Any] = {
      implicit def plainCodecForMyTaggedString: PlainCodec[MyTaggedString] =
        Codec.string.map(_.taggedWith[Tapir])(identity).validate(Validator.pattern("apple|banana"))

      endpoint.in(query[String @@ Tapir]("fruit"))
    }

    val in_query: Endpoint[Int, Unit, Unit, Any] = {
      endpoint.in(query[Int]("amount").validate(Validator.min(0)))
    }

    val in_valid_json: Endpoint[ValidFruitAmount, Unit, Unit, Any] = {
      implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger()).validate(Validator.min(1).contramap(_.v))
      implicit val schemaForStringWrapper: Schema[StringWrapper] =
        Schema.string.validate(Validator.minLength(4).contramap(_.v))
      implicit val intEncoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
      implicit val intDecoder: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
      implicit val stringEncoder: Encoder[StringWrapper] = Encoder.encodeString.contramap(_.v)
      implicit val stringDecoder: Decoder[StringWrapper] = Decoder.decodeString.map(StringWrapper.apply)
      endpoint.in(jsonBody[ValidFruitAmount])
    }

    val in_valid_optional_json: Endpoint[Option[ValidFruitAmount], Unit, Unit, Any] = {
      implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger()).validate(Validator.min(1).contramap(_.v))
      implicit val schemaForStringWrapper: Schema[StringWrapper] =
        Schema.string.validate(Validator.minLength(4).contramap(_.v))
      implicit val intEncoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
      implicit val intDecoder: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
      implicit val stringEncoder: Encoder[StringWrapper] = Encoder.encodeString.contramap(_.v)
      implicit val stringDecoder: Decoder[StringWrapper] = Decoder.decodeString.map(StringWrapper.apply)
      endpoint.in(jsonBody[Option[ValidFruitAmount]])
    }

    val in_valid_query: Endpoint[IntWrapper, Unit, Unit, Any] = {
      implicit def plainCodecForWrapper: PlainCodec[IntWrapper] =
        Codec.int.map(IntWrapper.apply(_))(_.v).validate(Validator.min(1).contramap(_.v))
      endpoint.in(query[IntWrapper]("amount"))
    }

    val in_valid_json_collection: Endpoint[BasketOfFruits, Unit, Unit, Any] = {
      implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger()).validate(Validator.min(1).contramap(_.v))
      implicit val encoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
      implicit val decode: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)

      implicit val schemaForStringWrapper: Schema[StringWrapper] =
        Schema.string.validate(Validator.minLength(4).contramap(_.v))
      implicit val stringEncoder: Encoder[StringWrapper] = Encoder.encodeString.contramap(_.v)
      implicit val stringDecoder: Decoder[StringWrapper] = Decoder.decodeString.map(StringWrapper.apply)

      import sttp.tapir.tests.BasketOfFruits._
      implicit def validatedListEncoder[T: Encoder]: Encoder[ValidatedList[T]] = implicitly[Encoder[List[T]]].contramap(identity)
      implicit def validatedListDecoder[T: Decoder]: Decoder[ValidatedList[T]] =
        implicitly[Decoder[List[T]]].map(_.taggedWith[BasketOfFruits])
      implicit def schemaForValidatedList[T: Schema]: Schema[ValidatedList[T]] =
        implicitly[Schema[T]].asIterable[List].validate(Validator.minSize(1)).map(l => Some(l.taggedWith[BasketOfFruits]))(l => l)
      endpoint.in(jsonBody[BasketOfFruits])
    }

    val in_valid_map: Endpoint[Map[String, ValidFruitAmount], Unit, Unit, Any] = {
      // TODO: needed until Scala3 derivation supports
      implicit val schemaForStringWrapper: Schema[StringWrapper] = Schema(SchemaType.SString())
      implicit val encoderForStringWrapper: Encoder[StringWrapper] = Encoder.encodeString.contramap(_.v)
      implicit val decoderForStringWrapper: Decoder[StringWrapper] = Decoder.decodeString.map(StringWrapper.apply)

      implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger()).validate(Validator.min(1).contramap(_.v))
      implicit val encoderForIntWrapper: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
      implicit val decoderForIntWrapper: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
      endpoint.in(jsonBody[Map[String, ValidFruitAmount]])
    }

    val in_enum_class: Endpoint[Color, Unit, Unit, Any] = {
      implicit def plainCodecForColor: PlainCodec[Color] = Codec.derivedEnumeration[String, Color](
        (_: String) match {
          case "red"  => Some(Red)
          case "blue" => Some(Blue)
          case _      => None
        },
        _.toString.toLowerCase
      )
      endpoint.in(query[Color]("color"))
    }

    val in_optional_enum_class: Endpoint[Option[Color], Unit, Unit, Any] = {
      implicit def plainCodecForColor: PlainCodec[Color] = Codec.derivedEnumeration[String, Color](
        (_: String) match {
          case "red"  => Some(Red)
          case "blue" => Some(Blue)
          case _      => None
        },
        _.toString.toLowerCase
      )
      endpoint.in(query[Option[Color]]("color"))
    }

    val out_enum_object: Endpoint[Unit, Unit, ColorValue, Any] = {
      implicit def schemaForColor: Schema[Color] =
        Schema.string.validate(
          Validator.enumeration(
            List(Blue, Red),
            {
              case Red  => Some("red")
              case Blue => Some("blue")
            }
          )
        )
      endpoint.out(jsonBody[ColorValue])
    }

    val in_enum_values: Endpoint[IntWrapper, Unit, Unit, Any] = {
      implicit def plainCodecForWrapper: PlainCodec[IntWrapper] =
        Codec.int.map(IntWrapper.apply(_))(_.v).validate(Validator.enumeration(List(IntWrapper(1), IntWrapper(2))))
      endpoint.in(query[IntWrapper]("amount"))
    }

    val in_json_wrapper_enum: Endpoint[ColorWrapper, Unit, Unit, Any] = {
      implicit def schemaForColor: Schema[Color] = Schema.derivedEnumeration[Color](encode = Some(_.toString.toLowerCase))
      endpoint.in(jsonBody[ColorWrapper])
    }

    val in_valid_int_array: Endpoint[List[IntWrapper], Unit, Unit, Any] = {
      implicit val schemaForIntWrapper: Schema[IntWrapper] =
        Schema(SchemaType.SInteger()).validate(Validator.all(Validator.min(1), Validator.max(10)).contramap(_.v))
      implicit val encoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
      implicit val decode: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
      endpoint.in(jsonBody[List[IntWrapper]])
    }
  }

  //

  type Port = Int
}
