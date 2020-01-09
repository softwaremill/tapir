package sttp.tapir

import java.io.{File, InputStream, PrintWriter}
import java.nio.ByteBuffer

import com.github.ghik.silencer.silent
import io.circe.generic.auto._
import sttp.tapir.json.circe._
import com.softwaremill.macwire._
import com.softwaremill.tagging.{@@, Tagger}
import io.circe.{Decoder, Encoder}
import sttp.model.{Cookie, CookieValueWithMeta, CookieWithMeta, MultiQueryParams, StatusCode}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.model._

import scala.io.Source

package object tests {
  val in_query_out_string: Endpoint[String, Unit, String, Nothing] = endpoint.in(query[String]("fruit")).out(stringBody)

  val in_query_out_infallible_string: Endpoint[String, Nothing, String, Nothing] =
    infallibleEndpoint.in(query[String]("fruit")).out(stringBody).name("infallible")

  val in_query_query_out_string: Endpoint[(String, Option[Int]), Unit, String, Nothing] =
    endpoint.in(query[String]("fruit")).in(query[Option[Int]]("amount")).out(stringBody)

  val in_header_out_string: Endpoint[String, Unit, String, Nothing] = endpoint.in(header[String]("X-Role")).out(stringBody)

  val in_path_path_out_string: Endpoint[(String, Int), Unit, String, Nothing] =
    endpoint.in("fruit" / path[String] / "amount" / path[Int]).out(stringBody)

  val in_two_path_capture: Endpoint[(Int, Int), Unit, (Int, Int), Nothing] = endpoint
    .in("in" / path[Int] / path[Int])
    .out(header[Int]("a") and header[Int]("b"))

  val in_string_out_string: Endpoint[String, Unit, String, Nothing] = endpoint.post.in("api" / "echo").in(stringBody).out(stringBody)

  val in_path: Endpoint[String, Unit, Unit, Nothing] = endpoint.get.in("api").in(path[String])

  val in_fixed_header_out_string: Endpoint[Unit, Unit, String, Nothing] =
    endpoint.in("secret").in(header("location", "secret")).out(stringBody)

  val in_mapped_query_out_string: Endpoint[List[Char], Unit, String, Nothing] =
    endpoint.in(query[String]("fruit").map(_.toList)(_.mkString(""))).out(stringBody)

  val in_mapped_path_out_string: Endpoint[Fruit, Unit, String, Nothing] =
    endpoint.in(("fruit" / path[String]).mapTo(Fruit)).out(stringBody)

  val in_mapped_path_path_out_string: Endpoint[FruitAmount, Unit, String, Nothing] =
    endpoint.in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount)).out(stringBody)

  val in_query_mapped_path_path_out_string: Endpoint[(FruitAmount, String), Unit, String, Nothing] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount))
    .in(query[String]("color"))
    .out(stringBody)

  val in_query_out_mapped_string: Endpoint[String, Unit, List[Char], Nothing] =
    endpoint.in(query[String]("fruit")).out(stringBody.map(_.toList)(_.mkString("")))

  val in_query_out_mapped_string_header: Endpoint[String, Unit, FruitAmount, Nothing] = endpoint
    .in(query[String]("fruit"))
    .out(stringBody.and(header[Int]("X-Role")).mapTo(FruitAmount))

  val in_header_before_path: Endpoint[(String, Int), Unit, (Int, String), Nothing] = endpoint
    .in(header[String]("SomeHeader"))
    .in(path[Int])
    .out(header[Int]("IntHeader") and stringBody)

  val in_json_out_json: Endpoint[FruitAmount, Unit, FruitAmount, Nothing] =
    endpoint.post.in("api" / "echo").in(jsonBody[FruitAmount]).out(jsonBody[FruitAmount]).name("echo json")

  val in_byte_array_out_byte_array: Endpoint[Array[Byte], Unit, Array[Byte], Nothing] =
    endpoint.post.in("api" / "echo").in(binaryBody[Array[Byte]]).out(binaryBody[Array[Byte]]).name("echo byte array")

  val in_byte_buffer_out_byte_buffer: Endpoint[ByteBuffer, Unit, ByteBuffer, Nothing] =
    endpoint.post.in("api" / "echo").in(binaryBody[ByteBuffer]).out(binaryBody[ByteBuffer]).name("echo byte buffer")

  val in_input_stream_out_input_stream: Endpoint[InputStream, Unit, InputStream, Nothing] =
    endpoint.post.in("api" / "echo").in(binaryBody[InputStream]).out(binaryBody[InputStream]).name("echo input stream")

  val in_file_out_file: Endpoint[File, Unit, File, Nothing] =
    endpoint.post.in("api" / "echo").in(binaryBody[File]).out(binaryBody[File]).name("echo file")

  val in_unit_out_string: Endpoint[Unit, Unit, String, Nothing] =
    endpoint.in("api").out(stringBody)

  val in_unit_error_out_string: Endpoint[Unit, String, Unit, Nothing] =
    endpoint.in("api").errorOut(stringBody)

  val in_form_out_form: Endpoint[FruitAmount, Unit, FruitAmount, Nothing] =
    endpoint.post.in("api" / "echo").in(formBody[FruitAmount]).out(formBody[FruitAmount])

  val in_query_params_out_string: Endpoint[MultiQueryParams, Unit, String, Nothing] =
    endpoint.get.in("api" / "echo" / "params").in(queryParams).out(stringBody)

  val in_headers_out_headers: Endpoint[Seq[(String, String)], Unit, Seq[(String, String)], Nothing] =
    endpoint.get.in("api" / "echo" / "headers").in(headers).out(headers)

  val in_json_out_headers: Endpoint[FruitAmount, Unit, Seq[(String, String)], Nothing] =
    endpoint.get.in("api" / "echo" / "headers").in(jsonBody[FruitAmount]).out(headers)

  val in_paths_out_string: Endpoint[Seq[String], Unit, String, Nothing] =
    endpoint.get.in(paths).out(stringBody)

  val in_path_paths_out_header_body: Endpoint[(Int, Seq[String]), Unit, (Int, String), Nothing] =
    endpoint.get.in("api").in(path[Int]).in("and").in(paths).out(header[Int]("IntPath") and stringBody)

  val in_path_fixed_capture_fixed_capture: Endpoint[(Int, Int), Unit, Unit, Nothing] =
    endpoint.get.in("customer" / path[Int]("customer_id") / "orders" / path[Int]("order_id"))

  val in_query_list_out_header_list: Endpoint[List[String], Unit, List[String], Nothing] =
    endpoint.get.in("api" / "echo" / "param-to-header").in(query[List[String]]("qq")).out(header[List[String]]("hh"))

  def in_stream_out_stream[S]: Endpoint[S, Unit, S, S] = {
    val sb = streamBody[S](schemaFor[String], CodecFormat.TextPlain())
    endpoint.post.in("api" / "echo").in(sb).out(sb)
  }

  val in_simple_multipart_out_multipart: Endpoint[FruitAmount, Unit, FruitAmount, Nothing] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitAmount]).out(multipartBody[FruitAmount]).name("echo simple")

  val in_simple_multipart_out_string: Endpoint[FruitAmount, Unit, String, Nothing] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitAmount]).out(stringBody)

  val in_simple_multipart_out_raw_string: Endpoint[FruitAmountWrapper, Unit, String, Nothing] = {
    endpoint.post.in("api" / "echo").in(multipartBody[FruitAmountWrapper]).out(stringBody)
  }

  val in_file_multipart_out_multipart: Endpoint[FruitData, Unit, FruitData, Nothing] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitData]).out(multipartBody[FruitData]).name("echo file")

  val in_cookie_cookie_out_header: Endpoint[(Int, String), Unit, List[String], Nothing] =
    endpoint.get
      .in("api" / "echo" / "headers")
      .in(cookie[Int]("c1"))
      .in(cookie[String]("c2"))
      .out(header[List[String]]("Cookie"))

  val in_cookies_out_cookies: Endpoint[List[Cookie], Unit, List[CookieWithMeta], Nothing] =
    endpoint.get.in("api" / "echo" / "headers").in(cookies).out(setCookies)

  val in_set_cookie_value_out_set_cookie_value: Endpoint[CookieValueWithMeta, Unit, CookieValueWithMeta, Nothing] =
    endpoint.get.in("api" / "echo" / "headers").in(setCookie("c1")).out(setCookie("c1"))

  val in_root_path: Endpoint[Unit, Unit, Unit, Nothing] = endpoint.get.in("")

  val in_single_path: Endpoint[Unit, Unit, Unit, Nothing] = endpoint.get.in("api")

  val in_extract_request_out_string: Endpoint[String, Unit, String, Nothing] =
    endpoint.in(extractFromRequest(_.method.method)).out(stringBody)

  val in_auth_apikey_header_out_string: Endpoint[String, Unit, String, Nothing] =
    endpoint.in("auth").in(auth.apiKey(header[String]("X-Api-Key"))).out(stringBody)

  val in_auth_apikey_query_out_string: Endpoint[String, Unit, String, Nothing] =
    endpoint.in("auth").in(auth.apiKey(query[String]("api-key"))).out(stringBody)

  val in_auth_basic_out_string: Endpoint[UsernamePassword, Unit, String, Nothing] = endpoint.in("auth").in(auth.basic).out(stringBody)

  val in_auth_bearer_out_string: Endpoint[String, Unit, String, Nothing] = endpoint.in("auth").in(auth.bearer).out(stringBody)

  val in_string_out_status_from_string: Endpoint[String, Unit, Either[Int, String], Nothing] =
    endpoint
      .in(query[String]("fruit"))
      .out(
        oneOf[Either[Int, String]](
          statusMappingValueMatcher(StatusCode.Accepted, plainBody[Int].map(Left(_))(_.value)) { case Left(_: Int)   => true },
          statusMappingValueMatcher(StatusCode.Ok, plainBody[String].map(Right(_))(_.value)) { case Right(_: String) => true }
        )
      )

  val in_string_out_status_from_type_erasure_using_partial_matcher: Endpoint[String, Unit, Option[Either[Int, String]], Nothing] = {
    import sttp.tapir.typelevel.MatchType

    endpoint
      .in(query[String]("fruit"))
      .out(
        oneOf[Option[Either[Int, String]]](
          statusMapping(StatusCode.NoContent, emptyOutput.map[None.type](_ => None)(_ => ())),
          statusMappingValueMatcher(StatusCode.Accepted, jsonBody[Some[Left[Int, String]]])(
            implicitly[MatchType[Some[Left[Int, String]]]].partial
          ),
          statusMappingValueMatcher(StatusCode.Ok, jsonBody[Some[Right[Int, String]]])(
            implicitly[MatchType[Some[Right[Int, String]]]].partial
          )
        )
      )
  }
  val in_string_out_status_from_string_one_empty: Endpoint[String, Unit, Either[Unit, String], Nothing] =
    endpoint
      .in(query[String]("fruit"))
      .out(
        oneOf[Either[Unit, String]](
          statusMappingValueMatcher(StatusCode.Accepted, emptyOutput.map(Left(_))(_.value)) { case Left(_: Unit)     => true },
          statusMappingValueMatcher(StatusCode.Ok, plainBody[String].map(Right(_))(_.value)) { case Right(_: String) => true }
        )
      )

  val in_string_out_status: Endpoint[String, Unit, StatusCode, Nothing] =
    endpoint.in(query[String]("fruit")).out(statusCode)

  val delete_endpoint: Endpoint[Unit, Unit, Unit, Nothing] =
    endpoint.delete.in("api" / "delete").out(statusCode(StatusCode.Ok).description("ok"))

  val in_string_out_content_type_string: Endpoint[String, Unit, (String, String), Nothing] =
    endpoint.in("api" / "echo").in(stringBody).out(stringBody).out(header[String]("Content-Type"))

  val in_unit_out_header_redirect: Endpoint[Unit, Unit, String, Nothing] =
    endpoint.out(statusCode(StatusCode.PermanentRedirect)).out(header[String]("Location"))

  val in_unit_out_fixed_header: Endpoint[Unit, Unit, Unit, Nothing] =
    endpoint.out(header("Location", "Poland"))

  val in_optional_json_out_optional_json: Endpoint[Option[FruitAmount], Unit, Option[FruitAmount], Nothing] =
    endpoint.post.in("api" / "echo").in(jsonBody[Option[FruitAmount]]).out(jsonBody[Option[FruitAmount]])

  val in_optional_coproduct_json_out_optional_coproduct_json: Endpoint[Option[Entity], Unit, Option[Entity], Nothing] =
    endpoint.post.in("api" / "echo" / "coproduct").in(jsonBody[Option[Entity]]).out(jsonBody[Option[Entity]])

  //

  @silent("never used")
  object Validation {
    type MyTaggedString = String @@ Tapir

    val in_query_tagged: Endpoint[String @@ Tapir, Unit, Unit, Nothing] = {
      implicit def plainCodecForMyTaggedString(implicit uc: PlainCodec[String]): PlainCodec[MyTaggedString] =
        uc.map(_.taggedWith[Tapir])(identity).validate(Validator.pattern("apple|banana"))

      endpoint.in(query[String @@ Tapir]("fruit"))
    }

    val in_query: Endpoint[Int, Unit, Unit, Nothing] = {
      endpoint.in(query[Int]("amount").validate(Validator.min(0)))
    }

    val in_valid_json: Endpoint[ValidFruitAmount, Unit, Unit, Nothing] = {
      implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger)
      implicit val intEncoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
      implicit val intDecoder: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
      implicit val stringEncoder: Encoder[StringWrapper] = Encoder.encodeString.contramap(_.v)
      implicit val stringDecoder: Decoder[StringWrapper] = Decoder.decodeString.map(StringWrapper.apply)
      implicit val intValidator: Validator[IntWrapper] = Validator.min(1).contramap(_.v)
      implicit val stringValidator: Validator[StringWrapper] = Validator.minLength(4).contramap(_.v)
      endpoint.in(jsonBody[ValidFruitAmount])
    }

    val in_valid_optional_json: Endpoint[Option[ValidFruitAmount], Unit, Unit, Nothing] = {
      implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger)
      implicit val intEncoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
      implicit val intDecoder: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
      implicit val stringEncoder: Encoder[StringWrapper] = Encoder.encodeString.contramap(_.v)
      implicit val stringDecoder: Decoder[StringWrapper] = Decoder.decodeString.map(StringWrapper.apply)
      implicit val intValidator: Validator[IntWrapper] = Validator.min(1).contramap(_.v)
      implicit val stringValidator: Validator[StringWrapper] = Validator.minLength(4).contramap(_.v)
      endpoint.in(jsonBody[Option[ValidFruitAmount]])
    }

    val in_valid_query: Endpoint[IntWrapper, Unit, Unit, Nothing] = {
      implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger)
      implicit def plainCodecForWrapper(implicit uc: PlainCodec[Int]): PlainCodec[IntWrapper] =
        uc.map(IntWrapper.apply)(_.v).validate(Validator.min(1).contramap(_.v))
      endpoint.in(query[IntWrapper]("amount"))
    }

    val in_valid_json_collection: Endpoint[BasketOfFruits, Unit, Unit, Nothing] = {
      implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger)
      implicit val encoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
      implicit val decode: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
      implicit val v: Validator[IntWrapper] = Validator.min(1).contramap(_.v)

      implicit val stringEncoder: Encoder[StringWrapper] = Encoder.encodeString.contramap(_.v)
      implicit val stringDecoder: Decoder[StringWrapper] = Decoder.decodeString.map(StringWrapper.apply)
      implicit val stringValidator: Validator[StringWrapper] = Validator.minLength(4).contramap(_.v)

      import sttp.tapir.tests.BasketOfFruits._
      implicit def validatedListEncoder[T: Encoder]: Encoder[ValidatedList[T]] = implicitly[Encoder[List[T]]].contramap(identity)
      implicit def validatedListDecoder[T: Decoder]: Decoder[ValidatedList[T]] =
        implicitly[Decoder[List[T]]].map(_.taggedWith[BasketOfFruits])
      implicit def schemaForValidatedList[T: Schema]: Schema[ValidatedList[T]] = implicitly[Schema[T]].asArrayElement
      implicit def validatorForValidatedList[T: Validator]: Validator[ValidatedList[T]] =
        implicitly[Validator[T]].asIterableElements[ValidatedList].and(Validator.minSize(1).contramap(identity))
      endpoint.in(jsonBody[BasketOfFruits])
    }

    val in_valid_map: Endpoint[Map[String, ValidFruitAmount], Unit, Unit, Nothing] = {
      implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger)
      implicit val encoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
      implicit val decode: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
      implicit val v: Validator[IntWrapper] = Validator.min(1).contramap(_.v)
      endpoint.in(jsonBody[Map[String, ValidFruitAmount]])
    }

    val in_enum_class: Endpoint[Color, Unit, Unit, Nothing] = {
      implicit def schemaForColor: Schema[Color] = Schema(SchemaType.SString)
      implicit def plainCodecForColor: PlainCodec[Color] = {
        Codec.stringPlainCodecUtf8
          .map[Color]({
            case "red"  => Red
            case "blue" => Blue
          })(_.toString.toLowerCase)
          .validate(Validator.enum)
      }
      endpoint.in(query[Color]("color"))
    }

    val in_optional_enum_class: Endpoint[Option[Color], Unit, Unit, Nothing] = {
      implicit def schemaForColor: Schema[Color] = Schema(SchemaType.SString)
      implicit def plainCodecForColor: PlainCodec[Color] = {
        Codec.stringPlainCodecUtf8
          .map[Color]({
            case "red"  => Red
            case "blue" => Blue
          })(_.toString.toLowerCase)
          .validate(Validator.enum)
      }
      endpoint.in(query[Option[Color]]("color"))
    }

    val out_enum_object: Endpoint[Unit, Unit, ColorValue, Nothing] = {
      implicit def schemaForColor: Schema[Color] = Schema(SchemaType.SString)
      implicit def plainCodecForColor: PlainCodec[Color] = {
        Codec.stringPlainCodecUtf8
          .map[Color]({
            case "red"  => Red
            case "blue" => Blue
          })(_.toString.toLowerCase)
      }
      implicit def validatorForColor: Validator[Color] =
        Validator.enum(List(Blue, Red), { c =>
          Some(plainCodecForColor.encode(c))
        })
      endpoint.out(jsonBody[ColorValue])
    }

    val in_enum_values: Endpoint[IntWrapper, Unit, Unit, Nothing] = {
      implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger)
      implicit def plainCodecForWrapper(implicit uc: PlainCodec[Int]): PlainCodec[IntWrapper] =
        uc.map(IntWrapper.apply)(_.v).validate(Validator.enum(List(IntWrapper(1), IntWrapper(2))))
      endpoint.in(query[IntWrapper]("amount"))
    }

    val in_json_wrapper_enum: Endpoint[ColorWrapper, Unit, Unit, Nothing] = {
      implicit def schemaForColor: Schema[Color] = Schema(SchemaType.SString)
      implicit def colorValidator: Validator[Color] = Validator.enum.encode(_.toString.toLowerCase)
      endpoint.in(jsonBody[ColorWrapper])
    }

    val allEndpoints: Set[Endpoint[_, _, _, _]] = wireSet[Endpoint[_, _, _, _]]
  }

  //

  val allTestEndpoints: Set[Endpoint[_, _, _, _]] = wireSet[Endpoint[_, _, _, _]] ++ Validation.allEndpoints

  def writeToFile(s: String): File = {
    val f = File.createTempFile("test", "tapir")
    new PrintWriter(f) { write(s); close() }
    f.deleteOnExit()
    f
  }

  def readFromFile(f: File): String = {
    val s = Source.fromFile(f)
    try {
      s.mkString
    } finally {
      s.close()
    }
  }

  type Port = Int
}

case class ColorValue(color: Color, value: Int)

sealed trait Color
case object Blue extends Color
case object Red extends Color
