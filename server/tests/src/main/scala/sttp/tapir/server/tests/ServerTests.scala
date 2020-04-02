package sttp.tapir.server.tests

import java.io.{ByteArrayInputStream, File, InputStream}
import java.nio.ByteBuffer

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO, Resource}
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import sttp.client._
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import io.circe.generic.auto._
import org.scalatest.{Assertion, BeforeAndAfterAll, FunSuite, Matchers}
import sttp.model._
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults}
import sttp.tapir.tests.TestUtil._
import sttp.tapir.tests._

import scala.reflect.ClassTag

trait ServerTests[R[_], S, ROUTE] extends FunSuite with Matchers with BeforeAndAfterAll with StrictLogging {
  private val basicStringRequest = basicRequest.response(asStringAlways)

  def multipleValueHeaderSupport: Boolean = true
  def multipartInlineHeaderSupport: Boolean = true
  def streamingSupport: Boolean = true

  testServer(in_string_out_status_from_type_erasure_using_partial_matcher)((v: String) =>
    pureResult((if (v == "right") Some(Right("right")) else if (v == "left") Some(Left(42)) else None).asRight[Unit])
  ) { baseUri =>
    basicRequest.get(uri"$baseUri?fruit=nothing").send().map(_.code shouldBe StatusCode.NoContent) >>
      basicRequest.get(uri"$baseUri?fruit=right").send().map(_.code shouldBe StatusCode.Ok) >>
      basicRequest.get(uri"$baseUri?fruit=left").send().map(_.code shouldBe StatusCode.Accepted)
  }
  // method matching

  testServer(endpoint, "GET empty endpoint")((_: Unit) => pureResult(().asRight[Unit])) { baseUri =>
    basicRequest.get(baseUri).send().map(_.body shouldBe Right(""))
  }

  testServer(endpoint, "POST empty endpoint")((_: Unit) => pureResult(().asRight[Unit])) { baseUri =>
    basicRequest.post(baseUri).send().map(_.body shouldBe Right(""))
  }

  testServer(endpoint.get, "GET a GET endpoint")((_: Unit) => pureResult(().asRight[Unit])) { baseUri =>
    basicRequest.get(baseUri).send().map(_.body shouldBe Right(""))
  }

  testServer(endpoint.get, "POST a GET endpoint")((_: Unit) => pureResult(().asRight[Unit])) { baseUri =>
    basicRequest.post(baseUri).send().map(_.body shouldBe 'left)
  }

  //

  testServer(in_query_out_string)((fruit: String) => pureResult(s"fruit: $fruit".asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("fruit: orange"))
  }

  testServer[String, Nothing, String](in_query_out_infallible_string)((fruit: String) => pureResult(s"fruit: $fruit".asRight[Nothing])) {
    baseUri => basicRequest.get(uri"$baseUri?fruit=kiwi").send().map(_.body shouldBe Right("fruit: kiwi"))
  }

  testServer(in_query_query_out_string) { case (fruit: String, amount: Option[Int]) => pureResult(s"$fruit $amount".asRight[Unit]) } {
    baseUri =>
      basicRequest.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("orange None")) *>
        basicRequest.get(uri"$baseUri?fruit=orange&amount=10").send().map(_.body shouldBe Right("orange Some(10)"))
  }

  testServer(in_header_out_string)((p1: String) => pureResult(s"$p1".asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri").header("X-Role", "Admin").send().map(_.body shouldBe Right("Admin"))
  }

  testServer(in_path_path_out_string) { case (fruit: String, amount: Int) => pureResult(s"$fruit $amount".asRight[Unit]) } { baseUri =>
    basicRequest.get(uri"$baseUri/fruit/orange/amount/20").send().map(_.body shouldBe Right("orange 20"))
  }

  testServer(in_path, "Empty path should not be passed to path capture decoding") { _ => pureResult(Right(())) } { baseUri =>
    basicRequest.get(uri"$baseUri/api/").send().map(_.code shouldBe StatusCode.NotFound)
  }

  testServer(in_two_path_capture, "capturing two path parameters with the same specification") {
    case (a: Int, b: Int) => pureResult(Right((a, b)))
  } { baseUri =>
    basicRequest.get(uri"$baseUri/in/12/23").send().map { response =>
      response.header("a") shouldBe Some("12")
      response.header("b") shouldBe Some("23")
    }
  }

  testServer(in_string_out_string)((b: String) => pureResult(b.asRight[Unit])) { baseUri =>
    basicRequest.post(uri"$baseUri/api/echo").body("Sweet").send().map(_.body shouldBe Right("Sweet"))
  }

  testServer(in_string_out_string, "with get method")((b: String) => pureResult(b.asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri/api/echo").body("Sweet").send().map(_.body shouldBe 'left)
  }

  testServer(in_mapped_query_out_string)((fruit: List[Char]) => pureResult(s"fruit length: ${fruit.length}".asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("fruit length: 6"))
  }

  testServer(in_mapped_path_out_string)((fruit: Fruit) => pureResult(s"$fruit".asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri/fruit/kiwi").send().map(_.body shouldBe Right("Fruit(kiwi)"))
  }

  testServer(in_mapped_path_path_out_string)((p1: FruitAmount) => pureResult(s"FA: $p1".asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri/fruit/orange/amount/10").send().map(_.body shouldBe Right("FA: FruitAmount(orange,10)"))
  }

  testServer(in_query_mapped_path_path_out_string) {
    case (fa: FruitAmount, color: String) => pureResult(s"FA: $fa color: $color".asRight[Unit])
  } { baseUri =>
    basicRequest
      .get(uri"$baseUri/fruit/orange/amount/10?color=yellow")
      .send()
      .map(_.body shouldBe Right("FA: FruitAmount(orange,10) color: yellow"))
  }

  testServer(in_query_out_mapped_string)((p1: String) => pureResult(p1.toList.asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("orange"))
  }

  testServer(in_query_out_mapped_string_header)((p1: String) => pureResult(FruitAmount(p1, p1.length).asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri?fruit=orange").send().map { r =>
      r.body shouldBe Right("orange")
      r.header("X-Role") shouldBe Some("6")
    }
  }

  testServer(in_header_before_path, "Header input before path capture input") {
    case (str: String, i: Int) => pureResult((i, str).asRight[Unit])
  } { baseUri =>
    basicRequest.get(uri"$baseUri/12").header("SomeHeader", "hello").send().map { response =>
      response.body shouldBe Right("hello")
      response.header("IntHeader") shouldBe Some("12")
    }
  }

  testServer(in_json_out_json)((fa: FruitAmount) => pureResult(FruitAmount(fa.fruit + " banana", fa.amount * 2).asRight[Unit])) { baseUri =>
    basicRequest
      .post(uri"$baseUri/api/echo")
      .body("""{"fruit":"orange","amount":11}""")
      .send()
      .map(_.body shouldBe Right("""{"fruit":"orange banana","amount":22}"""))
  }

  testServer(in_json_out_json, "with accept header")((fa: FruitAmount) => pureResult(fa.asRight[Unit])) { baseUri =>
    basicRequest
      .post(uri"$baseUri/api/echo")
      .body("""{"fruit":"banana","amount":12}""")
      .header(HeaderNames.Accept, sttp.model.MediaType.ApplicationJson.toString)
      .send()
      .map(_.body shouldBe Right("""{"fruit":"banana","amount":12}"""))
  }

  testServer(in_json_out_json, "content type")((fa: FruitAmount) => pureResult(fa.asRight[Unit])) { baseUri =>
    basicRequest
      .post(uri"$baseUri/api/echo")
      .body("""{"fruit":"banana","amount":12}""")
      .send()
      .map(_.contentType shouldBe Some(sttp.model.MediaType.ApplicationJson.toString))
  }

  testServer(in_byte_array_out_byte_array)((b: Array[Byte]) => pureResult(b.asRight[Unit])) { baseUri =>
    basicRequest.post(uri"$baseUri/api/echo").body("banana kiwi".getBytes).send().map(_.body shouldBe Right("banana kiwi"))
  }

  testServer(in_byte_buffer_out_byte_buffer)((b: ByteBuffer) => pureResult(b.asRight[Unit])) { baseUri =>
    basicRequest.post(uri"$baseUri/api/echo").body("mango").send().map(_.body shouldBe Right("mango"))
  }

  if (streamingSupport) {
    testServer(in_input_stream_out_input_stream)((is: InputStream) =>
      pureResult((new ByteArrayInputStream(inputStreamToByteArray(is)): InputStream).asRight[Unit])
    ) { baseUri => basicRequest.post(uri"$baseUri/api/echo").body("mango").send().map(_.body shouldBe Right("mango")) }
  }

  testServer(in_unit_out_json_unit, "unit json mapper")((_: Unit) => pureResult(().asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri/api/unit").send().map(_.body shouldBe Right("{}"))
  }

  testServer(in_unit_out_string, "default status mapper")((_: Unit) => pureResult("".asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri/not-existing-path").send().map(_.code shouldBe StatusCode.NotFound)
  }

  testServer(in_unit_error_out_string, "default error status mapper")((_: Unit) => pureResult("".asLeft[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri/api").send().map(_.code shouldBe StatusCode.BadRequest)
  }

  testServer(in_file_out_file)((file: File) => pureResult(file.asRight[Unit])) { baseUri =>
    basicRequest.post(uri"$baseUri/api/echo").body("pen pineapple apple pen").send().map(_.body shouldBe Right("pen pineapple apple pen"))
  }

  testServer(in_form_out_form)((fa: FruitAmount) => pureResult(fa.copy(fruit = fa.fruit.reverse, amount = fa.amount + 1).asRight[Unit])) {
    baseUri =>
      basicRequest
        .post(uri"$baseUri/api/echo")
        .body(Map("fruit" -> "plum", "amount" -> "10"))
        .send()
        .map(_.body shouldBe Right("fruit=mulp&amount=11"))
  }

  testServer(in_query_params_out_string)((mqp: MultiQueryParams) =>
    pureResult(mqp.toSeq.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString("&").asRight[Unit])
  ) { baseUri =>
    val params = Map("name" -> "apple", "weight" -> "42", "kind" -> "very good")
    basicRequest
      .get(uri"$baseUri/api/echo/params?$params")
      .send()
      .map(_.body shouldBe Right("kind=very good&name=apple&weight=42"))
  }

  testServer(in_headers_out_headers)((hs: List[Header]) =>
    pureResult(hs.map(h => Header.notValidated(h.name, h.value.reverse)).asRight[Unit])
  ) { baseUri =>
    basicRequest
      .get(uri"$baseUri/api/echo/headers")
      .headers(Header.unsafeApply("X-Fruit", "apple"), Header.unsafeApply("Y-Fruit", "Orange"))
      .send()
      .map(_.headers should contain allOf (Header.unsafeApply("X-Fruit", "elppa"), Header.unsafeApply("Y-Fruit", "egnarO")))
  }

  testServer(in_paths_out_string)((ps: Seq[String]) => pureResult(ps.mkString(" ").asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri/hello/it/is/me/hal").send().map(_.body shouldBe Right("hello it is me hal"))
  }

  testServer(in_paths_out_string, "paths should match empty path")((ps: Seq[String]) => pureResult(ps.mkString(" ").asRight[Unit])) {
    baseUri => basicRequest.get(uri"$baseUri").send().map(_.body shouldBe Right(""))
  }

  if (streamingSupport) {
    testServer(in_stream_out_stream[S])((s: S) => pureResult(s.asRight[Unit])) { baseUri =>
      basicRequest.post(uri"$baseUri/api/echo").body("pen pineapple apple pen").send().map(_.body shouldBe Right("pen pineapple apple pen"))
    }
  }

  testServer(in_simple_multipart_out_multipart)((fa: FruitAmount) =>
    pureResult(FruitAmount(fa.fruit + " apple", fa.amount * 2).asRight[Unit])
  ) { baseUri =>
    basicStringRequest
      .post(uri"$baseUri/api/echo/multipart")
      .multipartBody(multipart("fruit", "pineapple"), multipart("amount", "120"))
      .send()
      .map { r =>
        r.body should include regex "name=\"fruit\"[\\s\\S]*pineapple apple"
        r.body should include regex "name=\"amount\"[\\s\\S]*240"
      }
  }

  testServer(in_file_multipart_out_multipart)((fd: FruitData) =>
    pureResult(
      FruitData(
        Part("", writeToFile(readFromFile(fd.data.body).reverse), fd.data.otherDispositionParams, Seq())
          .header("X-Auth", fd.data.headers.find(_.is("X-Auth")).map(_.value).toString)
      ).asRight[Unit]
    )
  ) { baseUri =>
    val file = writeToFile("peach mario")
    basicStringRequest
      .post(uri"$baseUri/api/echo/multipart")
      .multipartBody(multipartFile("data", file).fileName("fruit-data.txt").header("X-Auth", "12Aa"))
      .send()
      .map { r =>
        r.code shouldBe StatusCode.Ok
        if (multipartInlineHeaderSupport) r.body should include regex "X-Auth: Some\\(12Aa\\)"
        r.body should include regex "name=\"data\"[\\s\\S]*oiram hcaep"
      }
  }

  testServer(in_query_out_string, "invalid query parameter")((fruit: String) => pureResult(s"fruit: $fruit".asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri?fruit2=orange").send().map(_.code shouldBe StatusCode.BadRequest)
  }

  testServer(in_query_list_out_header_list)((l: List[String]) => pureResult(("v0" :: l).reverse.asRight[Unit])) { baseUri =>
    basicRequest
      .get(uri"$baseUri/api/echo/param-to-header?qq=${List("v1", "v2", "v3")}")
      .send()
      .map { r =>
        if (multipleValueHeaderSupport) {
          r.headers.filter(_.is("hh")).map(_.value).toList shouldBe List("v3", "v2", "v1", "v0")
        } else {
          r.headers.filter(_.is("hh")).map(_.value).headOption should contain("v3, v2, v1, v0")
        }
      }
  }

  testServer(in_cookies_out_cookies)((cs: List[sttp.model.Cookie]) =>
    pureResult(cs.map(c => sttp.model.CookieWithMeta.unsafeApply(c.name, c.value.reverse)).asRight[Unit])
  ) { baseUri =>
    basicRequest.get(uri"$baseUri/api/echo/headers").cookies(("c1", "v1"), ("c2", "v2")).send().map { r =>
      r.cookies.map(c => (c.name, c.value)).toList shouldBe List(("c1", "1v"), ("c2", "2v"))
    }
  }

  testServer(in_set_cookie_value_out_set_cookie_value)((c: CookieValueWithMeta) =>
    pureResult(c.copy(value = c.value.reverse).asRight[Unit])
  ) { baseUri =>
    basicRequest.get(uri"$baseUri/api/echo/headers").header("Set-Cookie", "c1=xy; HttpOnly; Path=/").send().map { r =>
      r.cookies.toList shouldBe List(
        CookieWithMeta.unsafeApply("c1", "yx", None, None, None, Some("/"), secure = false, httpOnly = true)
      )
    }
  }

  testServer(in_string_out_content_type_string, "dynamic content type")((b: String) => pureResult((b, "image/png").asRight[Unit])) {
    baseUri =>
      basicStringRequest.get(uri"$baseUri/api/echo").body("test").send().map { r =>
        r.contentType shouldBe Some("image/png")
        r.body shouldBe "test"
      }
  }

  testServer(in_unit_out_header_redirect)(_ => pureResult("http://new.com".asRight[Unit])) { baseUri =>
    basicRequest.followRedirects(false).get(uri"$baseUri").send().map { r =>
      r.code shouldBe StatusCode.PermanentRedirect
      r.header("Location") shouldBe Some("http://new.com")
    }
  }

  testServer(in_unit_out_fixed_header)(_ => pureResult(().asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri").send().map { r => r.header("Location") shouldBe Some("Poland") }
  }

  testServer(in_optional_json_out_optional_json)((fa: Option[FruitAmount]) => pureResult(fa.asRight[Unit])) { baseUri =>
    basicRequest
      .post(uri"$baseUri/api/echo")
      .send()
      .map { r =>
        r.code shouldBe StatusCode.Ok
        r.body shouldBe Right("")
      } >>
      basicRequest
        .post(uri"$baseUri/api/echo")
        .body("""{"fruit":"orange","amount":11}""")
        .send()
        .map(_.body shouldBe Right("""{"fruit":"orange","amount":11}"""))
  }

  // path matching

  testServer(endpoint, "no path should match anything")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) { baseUri =>
    basicRequest.get(uri"$baseUri").send().map(_.code shouldBe StatusCode.Ok) >>
      basicRequest.get(uri"$baseUri/").send().map(_.code shouldBe StatusCode.Ok) >>
      basicRequest.get(uri"$baseUri/nonemptypath").send().map(_.code shouldBe StatusCode.Ok) >>
      basicRequest.get(uri"$baseUri/nonemptypath/nonemptypath2").send().map(_.code shouldBe StatusCode.Ok)
  }

  testServer(in_root_path, "root path should not match non-root path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) { baseUri =>
    basicRequest.get(uri"$baseUri/nonemptypath").send().map(_.code shouldBe StatusCode.NotFound)
  }

  testServer(in_root_path, "root path should match empty path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) { baseUri =>
    basicRequest.get(uri"$baseUri").send().map(_.code shouldBe StatusCode.Ok)
  }

  testServer(in_root_path, "root path should match root path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) { baseUri =>
    basicRequest.get(uri"$baseUri/").send().map(_.code shouldBe StatusCode.Ok)
  }

  testServer(in_single_path, "single path should match single path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) { baseUri =>
    basicRequest.get(uri"$baseUri/api").send().map(_.code shouldBe StatusCode.Ok)
  }

  testServer(in_single_path, "single path should match single/ path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) { baseUri =>
    basicRequest.get(uri"$baseUri/api/").send().map(_.code shouldBe StatusCode.Ok)
  }

  testServer(in_path_paths_out_header_body, "Capturing paths after path capture") {
    case (i, paths) =>
      pureResult(Right((i, paths.mkString(","))))
  } { baseUri =>
    basicRequest.get(uri"$baseUri/api/15/and/some/more/path").send().map { r =>
      r.code shouldBe StatusCode.Ok
      r.header("IntPath") shouldBe Some("15")
      r.body shouldBe Right("some,more,path")
    }
  }

  testServer(in_path_paths_out_header_body, "Capturing paths after path capture (when empty)") {
    case (i, paths) =>
      pureResult(Right((i, paths.mkString(","))))
  } { baseUri =>
    basicRequest.get(uri"$baseUri/api/15/and/").send().map { r =>
      r.code shouldBe StatusCode.Ok
      r.header("IntPath") shouldBe Some("15")
      r.body shouldBe Right("")
    }
  }

  testServer(in_single_path, "single path should not match root path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) { baseUri =>
    basicRequest.get(uri"$baseUri").send().map(_.code shouldBe StatusCode.NotFound) >>
      basicRequest.get(uri"$baseUri/").send().map(_.code shouldBe StatusCode.NotFound)
  }

  testServer(in_single_path, "single path should not match larger path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) { baseUri =>
    basicRequest.get(uri"$baseUri/api/echo/hello").send().map(_.code shouldBe StatusCode.NotFound) >>
      basicRequest.get(uri"$baseUri/api/echo/").send().map(_.code shouldBe StatusCode.NotFound)
  }

  testServer(in_string_out_status_from_string)((v: String) => pureResult((if (v == "apple") Right("x") else Left(10)).asRight[Unit])) {
    baseUri =>
      basicRequest.get(uri"$baseUri?fruit=apple").send().map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri?fruit=orange").send().map(_.code shouldBe StatusCode.Accepted)
  }

  testServer(in_string_out_status_from_string_one_empty)((v: String) =>
    pureResult((if (v == "apple") Right("x") else Left(())).asRight[Unit])
  ) { baseUri =>
    basicRequest.get(uri"$baseUri?fruit=apple").send().map(_.code shouldBe StatusCode.Ok) >>
      basicRequest.get(uri"$baseUri?fruit=orange").send().map(_.code shouldBe StatusCode.Accepted)
  }

  testServer(in_extract_request_out_string)((v: String) => pureResult(v.asRight[Unit])) { baseUri =>
    basicStringRequest.get(uri"$baseUri").send().map(_.body shouldBe "GET") >>
      basicStringRequest.post(uri"$baseUri").send().map(_.body shouldBe "POST")
  }

  testServer(in_string_out_status)((v: String) =>
    pureResult((if (v == "apple") StatusCode.Accepted else StatusCode.NotFound).asRight[Unit])
  ) { baseUri =>
    basicRequest.get(uri"$baseUri?fruit=apple").send().map(_.code shouldBe StatusCode.Accepted) >>
      basicRequest.get(uri"$baseUri?fruit=orange").send().map(_.code shouldBe StatusCode.NotFound)
  }

  // path shape matching

  val decodeFailureHandlerBadRequestOnPathFailure: DecodeFailureHandler =
    ServerDefaults.decodeFailureHandler.copy(
      respondWithStatusCode = ServerDefaults.FailureHandling
        .respondWithStatusCode(_, badRequestOnPathErrorIfPathShapeMatches = true, badRequestOnPathInvalidIfPathShapeMatches = true)
    )

  testServer(
    in_path_fixed_capture_fixed_capture,
    "Returns 400 if path 'shape' matches, but failed to parse a path parameter",
    Some(decodeFailureHandlerBadRequestOnPathFailure)
  )(_ => pureResult(Either.right[Unit, Unit](()))) { baseUri =>
    basicRequest.get(uri"$baseUri/customer/asd/orders/2").send().map { response =>
      response.body shouldBe Left("Invalid value for: path parameter customer_id")
      response.code shouldBe StatusCode.BadRequest
    }
  }

  testServer(
    in_path_fixed_capture_fixed_capture,
    "Returns 404 if path 'shape' doesn't match",
    Some(decodeFailureHandlerBadRequestOnPathFailure)
  )(_ => pureResult(Either.right[Unit, Unit](()))) { baseUri =>
    basicRequest.get(uri"$baseUri/customer").send().map(response => response.code shouldBe StatusCode.NotFound) >>
      basicRequest.get(uri"$baseUri/customer/asd").send().map(response => response.code shouldBe StatusCode.NotFound) >>
      basicRequest.get(uri"$baseUri/customer/asd/orders/2/xyz").send().map(response => response.code shouldBe StatusCode.NotFound)
  }

  // auth

  testServer(in_auth_apikey_header_out_string)((s: String) => pureResult(s.asRight[Unit])) { baseUri =>
    basicStringRequest.get(uri"$baseUri/auth").header("X-Api-Key", "1234").send().map(_.body shouldBe "1234")
  }

  testServer(in_auth_apikey_query_out_string)((s: String) => pureResult(s.asRight[Unit])) { baseUri =>
    basicStringRequest.get(uri"$baseUri/auth?api-key=1234").send().map(_.body shouldBe "1234")
  }

  testServer(in_auth_basic_out_string)((up: UsernamePassword) => pureResult(up.toString.asRight[Unit])) { baseUri =>
    basicStringRequest
      .get(uri"$baseUri/auth")
      .auth
      .basic("teddy", "bear")
      .send()
      .map(_.body shouldBe "UsernamePassword(teddy,Some(bear))")
  }

  testServer(in_auth_bearer_out_string)((s: String) => pureResult(s.asRight[Unit])) { baseUri =>
    basicStringRequest.get(uri"$baseUri/auth").auth.bearer("1234").send().map(_.body shouldBe "1234")
  }

  //

  testServer(
    "two endpoints with increasingly specific path inputs: should match path exactly",
    NonEmptyList.of(
      route(endpoint.get.in("p1").out(stringBody), (_: Unit) => pureResult("e1".asRight[Unit])),
      route(endpoint.get.in("p1" / "p2").out(stringBody), (_: Unit) => pureResult("e2".asRight[Unit]))
    )
  ) { baseUri =>
    basicStringRequest.get(uri"$baseUri/p1").send().map(_.body shouldBe "e1") >>
      basicStringRequest.get(uri"$baseUri/p1/p2").send().map(_.body shouldBe "e2")
  }

  testServer(
    "two endpoints with a body defined as the first input: should only consume body when the path matches",
    NonEmptyList.of(
      route(
        endpoint.post.in(rawBinaryBody[Array[Byte]]).in("p1").out(stringBody),
        (s: Array[Byte]) => pureResult(s"p1 ${s.length}".asRight[Unit])
      ),
      route(
        endpoint.post.in(rawBinaryBody[Array[Byte]]).in("p2").out(stringBody),
        (s: Array[Byte]) => pureResult(s"p2 ${s.length}".asRight[Unit])
      )
    )
  ) { baseUri =>
    basicStringRequest
      .post(uri"$baseUri/p2")
      .body("a" * 1000000)
      .send()
      .map { r => r.body shouldBe "p2 1000000" }
  }

  testServer(
    "two endpoints with query defined as the first input, path segments as second input: should try the second endpoint if the path doesn't match",
    NonEmptyList.of(
      route(endpoint.get.in(query[String]("q1")).in("p1"), (_: String) => pureResult(().asRight[Unit])),
      route(endpoint.get.in(query[String]("q2")).in("p2"), (_: String) => pureResult(().asRight[Unit]))
    )
  ) { baseUri =>
    basicRequest.get(uri"$baseUri/p1?q1=10").send().map(_.code shouldBe StatusCode.Ok) >>
      basicRequest.get(uri"$baseUri/p1?q2=10").send().map(_.code shouldBe StatusCode.BadRequest) >>
      basicRequest.get(uri"$baseUri/p2?q2=10").send().map(_.code shouldBe StatusCode.Ok) >>
      basicRequest.get(uri"$baseUri/p2?q1=10").send().map(_.code shouldBe StatusCode.BadRequest)
  }

  testServer(
    "two endpoints with increasingly specific path inputs, first with a required query parameter: should match path exactly",
    NonEmptyList.of(
      route(endpoint.get.in("p1").in(query[String]("q1")).out(stringBody), (_: String) => pureResult("e1".asRight[Unit])),
      route(endpoint.get.in("p1" / "p2").out(stringBody), (_: Unit) => pureResult("e2".asRight[Unit]))
    )
  ) { baseUri => basicStringRequest.get(uri"$baseUri/p1/p2").send().map(_.body shouldBe "e2") }

  testServer(
    "two endpoints with validation: should not try the second one if validation fails",
    NonEmptyList.of(
      route(endpoint.get.in("p1" / path[String].validate(Validator.minLength(5))), (_: String) => pureResult(().asRight[Unit])),
      route(endpoint.get.in("p2"), (_: Unit) => pureResult(().asRight[Unit]))
    )
  ) { baseUri =>
    basicRequest.get(uri"$baseUri/p1/abcde").send().map(_.code shouldBe StatusCode.Ok) >>
      basicRequest.get(uri"$baseUri/p1/ab").send().map(_.code shouldBe StatusCode.BadRequest) >>
      basicRequest.get(uri"$baseUri/p2").send().map(_.code shouldBe StatusCode.Ok)
  }

  //

  def throwFruits(name: String): R[String] = name match {
    case "apple"  => pureResult("ok")
    case "banana" => suspendResult(throw FruitError("no bananas", 102))
    case n        => suspendResult(throw new IllegalArgumentException(n))
  }

  testServer(
    "recover errors from exceptions",
    NonEmptyList.of(
      routeRecoverErrors(endpoint.in(query[String]("name")).errorOut(jsonBody[FruitError]).out(stringBody), throwFruits)
    )
  ) { baseUri =>
    basicRequest.get(uri"$baseUri?name=apple").send().map(_.body shouldBe Right("ok")) >>
      basicRequest.get(uri"$baseUri?name=banana").send().map { r =>
        r.code shouldBe StatusCode.BadRequest
        r.body shouldBe Left("""{"msg":"no bananas","code":102}""")
      } >>
      basicRequest.get(uri"$baseUri?name=orange").send().map { r =>
        r.code shouldBe StatusCode.InternalServerError
        r.body shouldBe 'left
      }
  }

  testServer(Validation.in_query_tagged, "support query validation with tagged type")((_: String) => pureResult(().asRight[Unit])) {
    baseUri =>
      basicRequest.get(uri"$baseUri?fruit=apple").send().map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri?fruit=orange").send().map(_.code shouldBe StatusCode.BadRequest) >>
        basicRequest.get(uri"$baseUri?fruit=banana").send().map(_.code shouldBe StatusCode.Ok)
  }

  testServer(Validation.in_query, "support query validation")((_: Int) => pureResult(().asRight[Unit])) { baseUri =>
    basicRequest.get(uri"$baseUri?amount=3").send().map(_.code shouldBe StatusCode.Ok) >>
      basicRequest.get(uri"$baseUri?amount=-3").send().map(_.code shouldBe StatusCode.BadRequest)
  }

  testServer(Validation.in_valid_json, "support jsonBody validation with wrapped type")((_: ValidFruitAmount) =>
    pureResult(().asRight[Unit])
  ) { baseUri =>
    basicRequest.get(uri"$baseUri").body("""{"fruit":"orange","amount":11}""").send().map(_.code shouldBe StatusCode.Ok) >>
      basicRequest.get(uri"$baseUri").body("""{"fruit":"orange","amount":0}""").send().map(_.code shouldBe StatusCode.BadRequest) >>
      basicRequest.get(uri"$baseUri").body("""{"fruit":"orange","amount":1}""").send().map(_.code shouldBe StatusCode.Ok)
  }

  testServer(Validation.in_valid_query, "support query validation with wrapper type")((_: IntWrapper) => pureResult(().asRight[Unit])) {
    baseUri =>
      basicRequest.get(uri"$baseUri?amount=11").send().map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri?amount=0").send().map(_.code shouldBe StatusCode.BadRequest) >>
        basicRequest.get(uri"$baseUri?amount=1").send().map(_.code shouldBe StatusCode.Ok)
  }

  testServer(Validation.in_valid_json_collection, "support jsonBody validation with list of wrapped type")((_: BasketOfFruits) =>
    pureResult(().asRight[Unit])
  ) { baseUri =>
    basicRequest.get(uri"$baseUri").body("""{"fruits":[{"fruit":"orange","amount":11}]}""").send().map(_.code shouldBe StatusCode.Ok) >>
      basicRequest.get(uri"$baseUri").body("""{"fruits": []}""").send().map(_.code shouldBe StatusCode.BadRequest) >>
      basicRequest
        .get(uri"$baseUri")
        .body("""{fruits":[{"fruit":"orange","amount":0}]}""")
        .send()
        .map(_.code shouldBe StatusCode.BadRequest)
  }

  //

  implicit lazy val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val backend: SttpBackend[IO, Nothing, NothingT] = AsyncHttpClientCatsBackend[IO]().unsafeRunSync()

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }

  //

  def pureResult[T](t: T): R[T]
  def suspendResult[T](t: => T): R[T]

  def route[I, E, O](
      e: Endpoint[I, E, O, S],
      fn: I => R[Either[E, O]],
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  ): ROUTE

  def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, S], fn: I => R[O])(implicit eClassTag: ClassTag[E]): ROUTE

  def server(routes: NonEmptyList[ROUTE], port: Port): Resource[IO, Unit]

  def portCounter: PortCounter

  def testServer[I, E, O](
      e: Endpoint[I, E, O, S],
      testNameSuffix: String = "",
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  )(
      fn: I => R[Either[E, O]]
  )(runTest: Uri => IO[Assertion]): Unit = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      NonEmptyList.of(route(e, fn, decodeFailureHandler))
    )(runTest)
  }

  def testServer(name: String, rs: => NonEmptyList[ROUTE])(runTest: Uri => IO[Assertion]): Unit = {
    val resources = for {
      port <- Resource.liftF(IO(portCounter.next()))
      _ <- Resource.liftF(IO(logger.info(s"Trying to bind to $port")))
      _ <- server(rs, port).onError {
        case e: Exception => Resource.liftF(IO(logger.error(s"Starting server on $port failed because of ${e.getMessage}")))
      }
    } yield uri"http://localhost:$port"

    if (testNameFilter forall name.contains) {
      test(name)(retryIfAddressAlreadyInUse(resources, 3).use(runTest).unsafeRunSync())
    }
  }

  private def retryIfAddressAlreadyInUse[A](r: Resource[IO, A], tries: Int): Resource[IO, A] = {
    r.recoverWith {
      case e: Exception if tries > 1 && e.getMessage.contains("Address already in use") =>
        logger.error(s"Exception when evaluating resource, retrying ${tries - 1} more times", e)
        retryIfAddressAlreadyInUse(r, tries - 1)
    }
  }

  // define to run a single test (in development)
  def testNameFilter: Option[String] = None
}
