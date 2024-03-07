package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import enumeratum._
import io.circe.generic.auto._
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.model._
import sttp.model.headers.{CookieValueWithMeta, CookieWithMeta}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.EndpointExtensions._
import sttp.tapir.server.model._
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.tests.Basic._
import sttp.tapir.tests.TestUtil._
import sttp.tapir.tests._
import sttp.tapir.tests.data.{FruitAmount, FruitError}

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.ByteBuffer
import sttp.tapir.tests.Files.in_file_out_file

class ServerBasicTests[F[_], OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE],
    serverInterpreter: TestServerInterpreter[F, Any, OPTIONS, ROUTE],
    multipleValueHeaderSupport: Boolean = true,
    inputStreamSupport: Boolean = true,
    supportsUrlEncodedPathSegments: Boolean = true,
    supportsMultipleSetCookieHeaders: Boolean = true,
    invulnerableToUnsanitizedHeaders: Boolean = true,
    maxContentLength: Boolean = true
)(implicit
    m: MonadError[F]
) {
  import createServerTest._
  import serverInterpreter._

  def tests(): List[Test] =
    basicTests() ++
      methodMatchingTests() ++
      pathMatchingTests() ++
      pathMatchingMultipleEndpoints() ++
      customiseDecodeFailureHandlerTests() ++
      serverSecurityLogicTests() ++
      (if (inputStreamSupport) inputStreamTests() else Nil) ++
      (if (maxContentLength) maxContentLengthTests else Nil) ++
      exceptionTests()

  def basicTests(): List[Test] = List(
    testServer(in_query_out_string)((fruit: String) => pureResult(s"fruit: $fruit".asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?fruit=orange").send(backend).map(_.body shouldBe Right("fruit: orange"))
    },
    testServer(in_query_out_string, "with URL encoding")((fruit: String) => pureResult(s"fruit: $fruit".asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri?fruit=red%20apple").send(backend).map(_.body shouldBe Right("fruit: red apple"))
    },
    testServer[String, Nothing, String](in_query_out_infallible_string)((fruit: String) => pureResult(s"fruit: $fruit".asRight[Nothing])) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri?fruit=kiwi").send(backend).map(_.body shouldBe Right("fruit: kiwi"))
    },
    testServer(in_query_query_out_string) { case (fruit: String, amount: Option[Int]) => pureResult(s"$fruit $amount".asRight[Unit]) } {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri?fruit=orange").send(backend).map(_.body shouldBe Right("orange None")) *>
          basicRequest.get(uri"$baseUri?fruit=orange&amount=10").send(backend).map(_.body shouldBe Right("orange Some(10)"))
    },
    testServer(in_header_out_string)((p1: String) => pureResult(s"$p1".asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri").header("X-Role", "Admin").send(backend).map(_.body shouldBe Right("Admin"))
    },
    testServer(in_path_path_out_string) { case (fruit: String, amount: Int) => pureResult(s"$fruit $amount".asRight[Unit]) } {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/fruit/orange/amount/20").send(backend).map(_.body shouldBe Right("orange 20"))
    },
    testServer(in_path_path_out_string, "with URL encoding") { case (fruit: String, amount: Int) =>
      pureResult(s"$fruit $amount".asRight[Unit])
    } { (backend, baseUri) =>
      if (supportsUrlEncodedPathSegments) {
        basicRequest.get(uri"$baseUri/fruit/apple%2Fred/amount/20").send(backend).map(_.body shouldBe Right("apple/red 20"))
      } else {
        IO.pure(succeed)
      }
    },
    testServer(in_path, "Empty path should not be passed to path capture decoding") { _ => pureResult(Right(())) } { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/api/").send(backend).map(_.code shouldBe StatusCode.NotFound)
    },
    testServer(in_two_path_capture, "capturing two path parameters with the same specification") { case (a: Int, b: Int) =>
      pureResult(Right((a, b)))
    } { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/in/12/23").send(backend).map { response =>
        response.header("a") shouldBe Some("12")
        response.header("b") shouldBe Some("23")
      }
    },
    testServer(in_string_out_string)((b: String) => pureResult(b.asRight[Unit])) { (backend, baseUri) =>
      basicRequest.post(uri"$baseUri/api/echo").body("Sweet").send(backend).map(_.body shouldBe Right("Sweet"))
    },
    testServer(in_string_out_string, "with get method")((b: String) => pureResult(b.asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/api/echo").body("Sweet").send(backend).map(_.body shouldBe Symbol("left"))
    },
    testServer(in_header_before_path, "Header input before path capture input") { case (str: String, i: Int) =>
      pureResult((i, str).asRight[Unit])
    } { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/12").header("SomeHeader", "hello").send(backend).map { response =>
        response.body shouldBe Right("hello")
        response.header("IntHeader") shouldBe Some("12")
      }
    },
    testServer(in_json_out_json)((fa: FruitAmount) => pureResult(FruitAmount(fa.fruit + " banana", fa.amount * 2).asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest
          .post(uri"$baseUri/api/echo")
          .body("""{"fruit":"orange","amount":11}""")
          .send(backend)
          .map(_.body shouldBe Right("""{"fruit":"orange banana","amount":22}"""))
    },
    testServer(in_json_out_json, "with accept header")((fa: FruitAmount) => pureResult(fa.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .post(uri"$baseUri/api/echo")
        .body("""{"fruit":"banana","amount":12}""")
        .header(HeaderNames.Accept, sttp.model.MediaType.ApplicationJson.toString)
        .send(backend)
        .map(_.body shouldBe Right("""{"fruit":"banana","amount":12}"""))
    },
    testServer(in_json_out_json, "content type")((fa: FruitAmount) => pureResult(fa.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .post(uri"$baseUri/api/echo")
        .body("""{"fruit":"banana","amount":12}""")
        .send(backend)
        .map(_.contentType shouldBe Some(sttp.model.MediaType.ApplicationJson.toString))
    },
    testServer(in_byte_array_out_byte_array)((b: Array[Byte]) => pureResult(b.asRight[Unit])) { (backend, baseUri) =>
      basicRequest.post(uri"$baseUri/api/echo").body("banana kiwi".getBytes).send(backend).map(_.body shouldBe Right("banana kiwi"))
    },
    testServer(in_byte_buffer_out_byte_buffer)((b: ByteBuffer) => pureResult(b.asRight[Unit])) { (backend, baseUri) =>
      basicRequest.post(uri"$baseUri/api/echo").body("mango").send(backend).map(_.body shouldBe Right("mango"))
    },
    testServer(in_unit_out_json_unit, "unit json mapper")((_: Unit) => pureResult(().asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/api/unit").send(backend).map(_.body shouldBe Right("{}"))
    },
    testServer(in_unit_out_string, "default status mapper")((_: Unit) => pureResult("".asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/not-existing-path").send(backend).map(_.code shouldBe StatusCode.NotFound)
    },
    testServer(in_unit_error_out_string, "default error status mapper")((_: Unit) => pureResult("".asLeft[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/api").send(backend).map(_.code shouldBe StatusCode.BadRequest)
    },
    testServer(in_form_out_form)((fa: FruitAmount) => pureResult(fa.copy(fruit = fa.fruit.reverse, amount = fa.amount + 1).asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest
          .post(uri"$baseUri/api/echo")
          .body(Map("fruit" -> "plum", "amount" -> "10"))
          .send(backend)
          .map(_.body shouldBe Right("fruit=mulp&amount=11"))
    },
    testServer(in_query_params_out_string)((mqp: QueryParams) =>
      pureResult(mqp.toSeq.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString("&").asRight[Unit])
    ) { (backend, baseUri) =>
      val params = Map("name" -> "apple", "weight" -> "42", "kind" -> "very good")
      basicRequest
        .get(uri"$baseUri/api/echo/params?$params")
        .send(backend)
        .map(_.body shouldBe Right("kind=very good&name=apple&weight=42"))
    },
    testServer(endpoint.get.in(path[String]("pathParam")).in(queryParams).out(stringBody)) { case (pathParam: String, mqp: QueryParams) =>
      pureResult(s"pathParam:$pathParam queryParams:${mqp.toSeq.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString("&")}".asRight[Unit])
    } { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri/abc?xyz=123")
        .send(backend)
        .map(_.body shouldBe Right("pathParam:abc queryParams:xyz=123"))
    },
    testServer(in_query_params_out_string, "should support value-less query param")((mqp: QueryParams) =>
      pureResult(mqp.toMultiMap.map(data => s"${data._1}=${data._2.toList}").mkString("&").asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri/api/echo/params?flag")
        .send(backend)
        .map(_.body shouldBe Right("flag=List()"))
    },
    testServer(in_headers_out_headers)((hs: List[Header]) => pureResult(hs.map(h => Header(h.name, h.value.reverse)).asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest
          .get(uri"$baseUri/api/echo/headers")
          .headers(Header.unsafeApply("X-Fruit", "apple"), Header.unsafeApply("Y-Fruit", "Orange"))
          .send(backend)
          .map(_.headers should contain allOf (Header.unsafeApply("X-Fruit", "elppa"), Header.unsafeApply("Y-Fruit", "egnarO")))
    },
    testServer(in_paths_out_string)((ps: Seq[String]) => pureResult(ps.mkString(" ").asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/hello/it/is/me/hal").send(backend).map(_.body shouldBe Right("hello it is me hal"))
    },
    testServer(in_paths_out_string, "paths should match empty path")((ps: Seq[String]) => pureResult(ps.mkString(" ").asRight[Unit])) {
      (backend, baseUri) => basicRequest.get(uri"$baseUri").send(backend).map(_.body shouldBe Right(""))
    },
    testServer(in_query_out_string, "invalid query parameter")((fruit: String) => pureResult(s"fruit: $fruit".asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri?fruit2=orange").send(backend).map(_.code shouldBe StatusCode.BadRequest)
    },
    testServer(in_query_list_out_header_list)((l: List[String]) => pureResult(("v0" :: l).reverse.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri/api/echo/param-to-header?qq=${List("v1", "v2", "v3")}")
        .send(backend)
        .map { r =>
          if (multipleValueHeaderSupport) {
            r.headers.filter(_.is("hh")).map(_.value).toSet shouldBe Set("v3", "v2", "v1", "v0")
          } else {
            r.headers.filter(_.is("hh")).map(_.value).headOption should (contain("v3, v2, v1, v0") or contain("v3,v2,v1,v0"))
          }
        }
    },
    testServer(in_cookies_out_cookies)((cs: List[sttp.model.headers.Cookie]) =>
      pureResult(cs.map(c => CookieWithMeta.unsafeApply(c.name, c.value.reverse)).asRight[Unit])
    ) { (backend, baseUri) =>
      if (supportsMultipleSetCookieHeaders) {
        basicRequest.get(uri"$baseUri/api/echo/headers").cookies(("c1", "v1"), ("c2", "v2")).send(backend).map { r =>
          r.unsafeCookies.map(c => (c.name, c.value)).toSet shouldBe Set(("c1", "1v"), ("c2", "2v"))
        }
      } else {
        IO.pure(succeed)
      }
    },
    // Reproduces https://github.com/http4s/http4s/security/advisories/GHSA-5vcm-3xc3-w7x3
    testServer(in_query_out_cookie_raw, "should be invulnerable to response splitting from unsanitized headers")((q: String) =>
      pureResult((q, "test").asRight[Unit])
    ) { (backend, baseUri) =>
      if (invulnerableToUnsanitizedHeaders) {
        basicRequest
          .get(uri"$baseUri?q=hax0r%0d%0aContent-Length:+13%0d%0a%0aI+hacked+you")
          .send(backend)
          .map { r =>
            if (r.code == StatusCode.Ok) {
              r.body shouldBe Right("test")
            } else {
              r.code shouldBe StatusCode.InternalServerError
            }
          }
      } else {
        IO.pure(succeed)
      }
    },
    testServer(in_set_cookie_value_out_set_cookie_value)((c: CookieValueWithMeta) =>
      pureResult(c.copy(value = c.value.reverse).asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/api/echo/headers").header("Set-Cookie", "c1=xy; HttpOnly; Path=/").send(backend).map { r =>
        r.unsafeCookies.toList shouldBe List(
          CookieWithMeta.unsafeApply("c1", "yx", None, None, None, Some("/"), secure = false, httpOnly = true)
        )
      }
    },
    testServer(in_string_out_content_type_string, "dynamic content type")((b: String) => pureResult((b, "image/png").asRight[Unit])) {
      (backend, baseUri) =>
        basicStringRequest.post(uri"$baseUri/api/echo").body("test").send(backend).map { r =>
          r.contentType shouldBe Some("image/png")
          r.body shouldBe "test"
        }
    },
    testServer(in_content_type_out_string)((ct: String) => pureResult(ct.asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/api/echo").contentType("application/dicom+json").send(backend).map { r =>
        r.body shouldBe Right("application/dicom+json")
      }
    },
    testServer(in_content_type_fixed_header, "mismatch content-type")((_: Unit) => pureResult(().asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .post(uri"$baseUri/api/echo")
        .contentType(MediaType.ApplicationXml)
        .send(backend)
        .map(_.code shouldBe StatusCode.UnsupportedMediaType)
    },
    testServer(in_content_type_fixed_header, "missing content-type")((_: Unit) => pureResult(().asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .post(uri"$baseUri/api/echo")
        .send(backend)
        .map(_.code shouldBe StatusCode.BadRequest)
    },
    testServer(in_content_type_header_with_custom_decode_results, "mismatch content-type")((_: MediaType) =>
      pureResult(Either.right[Unit, Unit](()))
    ) { (backend, baseUri) =>
      basicRequest
        .post(uri"$baseUri/api/echo")
        .contentType(MediaType.ApplicationXml)
        .send(backend)
        .map(_.code shouldBe StatusCode.UnsupportedMediaType)
    },
    testServer(in_content_type_header_with_custom_decode_results, "missing content-type")((_: MediaType) =>
      pureResult(Either.right[Unit, Unit](()))
    ) { (backend, baseUri) =>
      basicRequest
        .post(uri"$baseUri/api/echo")
        .send(backend)
        .map(_.code shouldBe StatusCode.BadRequest)
    },
    testServer(in_unit_out_html)(_ => pureResult("<html />".asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/api/echo").send(backend).map { r =>
        r.contentType shouldBe Some("text/html; charset=UTF-8")
      }
    },
    testServer(in_unit_out_header_redirect)(_ => pureResult("http://new.com".asRight[Unit])) { (backend, baseUri) =>
      basicRequest.followRedirects(false).get(uri"$baseUri").send(backend).map { r =>
        r.code shouldBe StatusCode.PermanentRedirect
        r.header("Location") shouldBe Some("http://new.com")
      }
    },
    testServer(in_unit_out_fixed_header)(_ => pureResult(().asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri").send(backend).map { r => r.header("Location") shouldBe Some("Poland") }
    },
    testServer(in_optional_json_out_optional_json)((fa: Option[FruitAmount]) => pureResult(fa.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .post(uri"$baseUri/api/echo")
        .send(backend)
        .map { r =>
          r.code shouldBe StatusCode.Ok
          r.body shouldBe Right("")
        } >>
        basicRequest
          .post(uri"$baseUri/api/echo")
          .body("""{"fruit":"orange","amount":11}""")
          .send(backend)
          .map(_.body shouldBe Right("""{"fruit":"orange","amount":11}"""))
    },
    //
    testServer(in_string_out_status, "custom status code")((_: String) => pureResult(StatusCode(431).asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?fruit=apple").send(backend).map(_.code shouldBe StatusCode(431))
    },
    testServer(in_extract_request_out_string)((v: String) => pureResult(v.asRight[Unit])) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri").send(backend).map(_.body shouldBe "GET") >>
        basicStringRequest.post(uri"$baseUri").send(backend).map(_.body shouldBe "POST")
    },
    testServer(in_string_out_status)((v: String) =>
      pureResult((if (v == "apple") StatusCode.Accepted else StatusCode.NotFound).asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?fruit=apple").send(backend).map(_.code shouldBe StatusCode.Accepted) >>
        basicRequest.get(uri"$baseUri?fruit=orange").send(backend).map(_.code shouldBe StatusCode.NotFound)
    },
    //
    testServer(in_query_with_default_out_string)(in => pureResult(in.asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?p1=x").send(backend).map(_.body shouldBe Right("x")) >>
        basicRequest.get(uri"$baseUri").send(backend).map(_.body shouldBe Right("DEFAULT"))
    },
    testServer(in_fixed_content_type_header_out_string, "fixed multipart/form-data header input should ignore boundary directive")(_ =>
      pureResult("x".asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri/api")
        .headers(Header(HeaderNames.ContentType, "multipart/form-data; boundary=abc"))
        .send(backend)
        .map(_.body shouldBe Right("x"))
    },
    testServer(out_custom_content_type_empty_body)(k =>
      pureResult((if (k < 0) MediaType.ApplicationJson.toString() else MediaType.ApplicationXml.toString()).asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri?kind=-1")
        .send(backend)
        .map(_.contentType shouldBe Some(MediaType.ApplicationJson.toString())) >>
        basicRequest
          .get(uri"$baseUri?kind=1")
          .send(backend)
          .map(_.contentType shouldBe Some(MediaType.ApplicationXml.toString()))
    },
    testServer(out_custom_content_type_string_body)(k =>
      pureResult((if (k < 0) (MediaType.ApplicationJson.toString(), "{}") else (MediaType.ApplicationXml.toString(), "<>")).asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri?kind=-1")
        .send(backend)
        .map { r =>
          r.body shouldBe Right("{}")
          r.contentType shouldBe Some(MediaType.ApplicationJson.toString())
        } >>
        basicRequest
          .get(uri"$baseUri?kind=1")
          .send(backend)
          .map { r =>
            r.body shouldBe Right("<>")
            r.contentType shouldBe Some(MediaType.ApplicationXml.toString())
          }
    },
    testServer(in_raw_with_json_out_string) { case (s: String, fa: FruitAmount) =>
      pureResult((s.length + " " + fa.amount).asRight[Unit])
    } { (backend, baseUri) =>
      basicRequest
        .post(uri"$baseUri/api/echo")
        .body("""{"fruit":"orange","amount":11}""")
        .send(backend)
        .map(_.body shouldBe Right("30 11"))
    },
    testServer(in_flag_query_out_string) { input => pureResult(input.toString.asRight[Unit]) } { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?flag").send(backend).map(_.body shouldBe Right("Some(true)")) >>
        basicRequest.get(uri"$baseUri?flag=false").send(backend).map(_.body shouldBe Right("Some(false)")) >>
        basicRequest.get(uri"$baseUri").send(backend).map(_.body shouldBe Right("None"))
    },
    testServer(in_query_out_string, "should contain the content-length header")((fruit: String) =>
      pureResult(s"fruit: $fruit".asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?fruit=orange").send(backend).map(_.contentLength shouldBe Some(13))
    }
  )

  def methodMatchingTests(): List[Test] = List(
    testServer(endpoint, "GET empty endpoint")((_: Unit) => pureResult(().asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(baseUri).send(backend).map(_.body shouldBe Right(""))
    },
    testServer(endpoint, "POST empty endpoint")((_: Unit) => pureResult(().asRight[Unit])) { (backend, baseUri) =>
      basicRequest.post(baseUri).send(backend).map(_.body shouldBe Right(""))
    },
    testServer(out_fixed_content_type_header, "Fixed content-type header")((_: Unit) => pureResult("".asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.get(baseUri).send(backend).map(_.headers.toSet should contain(Header("Content-Type", "text/csv")))
    },
    testServer(endpoint.get, "GET a GET endpoint")((_: Unit) => pureResult(().asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(baseUri).send(backend).map(_.body shouldBe Right(""))
    },
    testServer(endpoint.get, "POST a GET endpoint")((_: Unit) => pureResult(().asRight[Unit])) { (backend, baseUri) =>
      basicRequest.post(baseUri).send(backend).map(_.body shouldBe Symbol("left"))
    }
  )

  def pathMatchingTests(): List[Test] = List(
    testServer(endpoint, "no path should match anything")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/nonemptypath").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/nonemptypath/nonemptypath2").send(backend).map(_.code shouldBe StatusCode.Ok)
    },
    testServer(in_root_path, "root path should not match non-root path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/nonemptypath").send(backend).map(_.code shouldBe StatusCode.NotFound)
    },
    testServer(in_root_path, "root path should match empty path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri").send(backend).map(_.code shouldBe StatusCode.Ok)
    },
    testServer(in_root_path, "root path should match root path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/").send(backend).map(_.code shouldBe StatusCode.Ok)
    },
    testServer(in_single_path, "single path should match single path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/api").send(backend).map(_.code shouldBe StatusCode.Ok)
    },
    testServer(in_single_path, "single path should match single/ path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/api/").send(backend).map(_.code shouldBe StatusCode.Ok)
    },
    testServer(in_path_paths_out_header_body, "Capturing paths after path capture") { case (i, paths) =>
      pureResult(Right((i, paths.mkString(","))))
    } { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/api/15/and/some/more/path").send(backend).map { r =>
        r.code shouldBe StatusCode.Ok
        r.header("IntPath") shouldBe Some("15")
        r.body shouldBe Right("some,more,path")
      }
    },
    testServer(in_path_paths_out_header_body, "Capturing paths after path capture (when empty)") { case (i, paths) =>
      pureResult(Right((i, paths.mkString(","))))
    } { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/api/15/and/").send(backend).map { r =>
        r.code shouldBe StatusCode.Ok
        r.header("IntPath") shouldBe Some("15")
        r.body shouldBe Right("")
      }
    },
    testServer(in_single_path, "single path should not match root path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri").send(backend).map(_.code shouldBe StatusCode.NotFound) >>
          basicRequest.get(uri"$baseUri/").send(backend).map(_.code shouldBe StatusCode.NotFound)
    },
    testServer(in_single_path, "single path should not match larger path")((_: Unit) => pureResult(Either.right[Unit, Unit](()))) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/api/echo/hello").send(backend).map(_.code shouldBe StatusCode.NotFound) >>
          basicRequest.get(uri"$baseUri/api/echo/").send(backend).map(_.code shouldBe StatusCode.NotFound)
    },
    testServerLogic(
      in_path_security_and_regular
        .serverSecurityLogic { _ => pureResult(().asRight[Unit]) }
        .serverLogic(_ => _ => pureResult("ok".asRight[Unit]))
    ) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri/auth/settings").send(backend).map(_.body shouldBe "ok")
    },
    testServerLogic(
      in_path_security_no_regular
        .serverSecurityLogic { _ => pureResult(().asRight[Unit]) }
        .serverLogic(_ => _ => pureResult("ok".asRight[Unit]))
    ) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri/auth").send(backend).map(_.body shouldBe "ok") >>
        basicStringRequest.get(uri"$baseUri/auth/extra").send(backend).map(_.code shouldBe StatusCode.NotFound)
    }
  )

  def pathMatchingMultipleEndpoints(): List[Test] = List(
    testServer(
      "two endpoints with increasingly specific path inputs: should match path exactly",
      NonEmptyList.of(
        route(endpoint.get.in("p1").out(stringBody).serverLogic((_: Unit) => pureResult("e1".asRight[Unit]))),
        route(endpoint.get.in("p1" / "p2").out(stringBody).serverLogic((_: Unit) => pureResult("e2".asRight[Unit])))
      )
    ) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri/p1").send(backend).map(_.body shouldBe "e1") >>
        basicStringRequest.get(uri"$baseUri/p1/p2").send(backend).map(_.body shouldBe "e2")
    },
    testServer(
      "two endpoints with a body defined as the first input: should only consume body when the path matches",
      NonEmptyList.of(
        route(
          endpoint.post
            .in(byteArrayBody)
            .in("p1")
            .out(stringBody)
            .serverLogic((s: Array[Byte]) => pureResult(s"p1 ${s.length}".asRight[Unit]))
        ),
        route(
          endpoint.post
            .in(byteArrayBody)
            .in("p2")
            .out(stringBody)
            .serverLogic((s: Array[Byte]) => pureResult(s"p2 ${s.length}".asRight[Unit]))
        )
      )
    ) { (backend, baseUri) =>
      basicStringRequest
        .post(uri"$baseUri/p2")
        .body("a" * 1000000)
        .send(backend)
        .map { r => r.body shouldBe "p2 1000000" }
    },
    testServer(
      "two endpoints with query defined as the first input, path segments as second input: should try the second endpoint if the path doesn't match",
      NonEmptyList.of(
        route(endpoint.get.in(query[String]("q1")).in("p1").serverLogic((_: String) => pureResult(().asRight[Unit]))),
        route(endpoint.get.in(query[String]("q2")).in("p2").serverLogic((_: String) => pureResult(().asRight[Unit])))
      )
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/p1?q1=10").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/p1?q2=10").send(backend).map(_.code shouldBe StatusCode.BadRequest) >>
        basicRequest.get(uri"$baseUri/p2?q2=10").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/p2?q1=10").send(backend).map(_.code shouldBe StatusCode.BadRequest)
    },
    testServer(
      "two endpoints with increasingly specific path inputs, first with a required query parameter: should match path exactly",
      NonEmptyList.of(
        route(endpoint.get.in("p1").in(query[String]("q1")).out(stringBody).serverLogic((_: String) => pureResult("e1".asRight[Unit]))),
        route(endpoint.get.in("p1" / "p2").out(stringBody).serverLogic((_: Unit) => pureResult("e2".asRight[Unit])))
      )
    ) { (backend, baseUri) => basicStringRequest.get(uri"$baseUri/p1/p2").send(backend).map(_.body shouldBe "e2") },
    testServer(
      "two endpoints with validation: should not try the second one if validation fails",
      NonEmptyList.of(
        route(
          endpoint.get.in("p1" / path[String].validate(Validator.minLength(5))).serverLogic((_: String) => pureResult(().asRight[Unit]))
        ),
        route(endpoint.get.in("p2").serverLogic((_: Unit) => pureResult(().asRight[Unit])))
      )
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/p1/abcde").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/p1/ab").send(backend).map(_.code shouldBe StatusCode.BadRequest) >>
        basicRequest.get(uri"$baseUri/p2").send(backend).map(_.code shouldBe StatusCode.Ok)
    },
    testServer(
      "endpoint with a security input and regular path input shouldn't shadow other endpoints",
      NonEmptyList.of(
        route(
          endpoint.get
            .in("p1")
            .securityIn(auth.bearer[String]())
            .out(stringBody)
            .serverSecurityLogicSuccess(_ => pureResult(()))
            .serverLogicSuccess(_ => _ => pureResult("ok1"))
        ),
        route(
          endpoint.get
            .in("p2")
            .out(stringBody)
            .serverLogicSuccess(_ => pureResult("ok2"))
        )
      )
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/p1").send(backend).map(_.code shouldBe StatusCode.Unauthorized) >>
        basicRequest.get(uri"$baseUri/p2").send(backend).map { r =>
          r.code shouldBe StatusCode.Ok
          r.body shouldBe Right("ok2")
        } >>
        basicRequest.get(uri"$baseUri/p1").header("Authorization", "Bearer 1234").send(backend).map { r =>
          r.code shouldBe StatusCode.Ok
          r.body shouldBe Right("ok1")
        }
    },
    testServer(
      "two endpoints, same path prefix, one without trailing slashes, second accepting trailing slashes",
      NonEmptyList.of(
        route(
          List[ServerEndpoint[Any, F]](
            endpoint.get.in("p1" / "p2").in(noTrailingSlash).out(stringBody).serverLogic((_: Unit) => pureResult("e1".asRight[Unit])),
            endpoint.get.in("p1" / "p2").in(paths).out(stringBody).serverLogic((_: List[String]) => pureResult("e2".asRight[Unit]))
          )
        )
      )
    ) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri/p1/p2").send(backend).map(_.body shouldBe "e1") >>
        basicStringRequest.get(uri"$baseUri/p1/p2/").send(backend).map(_.body shouldBe "e2") >>
        basicStringRequest.get(uri"$baseUri/p1/p2/p3").send(backend).map(_.body shouldBe "e2")
    }, {
      // https://github.com/softwaremill/tapir/issues/2811
      import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.OnDecodeFailure._
      testServer(
        "try other paths on decode failure if onDecodeFailureNextEndpoint",
        NonEmptyList.of(
          route(
            List[ServerEndpoint[Any, F]](
              endpoint.get
                .in("animal")
                .in(path[Animal]("animal").onDecodeFailureNextEndpoint)
                .out(stringBody)
                .serverLogicSuccess[F] {
                  case Animal.Dog => m.unit("This is a dog")
                  case Animal.Cat => m.unit("This is a cat")
                },
              endpoint.post
                .in("animal")
                .in("bird")
                .out(stringBody)
                .serverLogicSuccess[F] { _ =>
                  m.unit("This is a bird")
                }
            )
          )
        )
      ) { (backend, baseUri) =>
        basicStringRequest.post(uri"$baseUri/animal/bird").send(backend).map(_.body shouldBe "This is a bird")
      }
    },
    testServer(
      "two endpoints with different methods, first one with path parsing",
      NonEmptyList.of(
        route(
          List[ServerEndpoint[Any, F]](
            endpoint.get.in("p1" / path[Int]("id")).serverLogic((_: Int) => pureResult(().asRight[Unit])),
            endpoint.post.in("p1" / path[String]("id")).serverLogic((_: String) => pureResult(().asRight[Unit]))
          )
        )
      )
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/p1/123").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/p1/abc").send(backend).map(_.code shouldBe StatusCode.BadRequest) >>
        basicRequest.post(uri"$baseUri/p1/123").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.post(uri"$baseUri/p1/abc").send(backend).map(_.code shouldBe StatusCode.Ok)
    }
  )

  def customiseDecodeFailureHandlerTests(): List[Test] = List(
    testServer(
      in_path_fixed_capture_fixed_capture,
      "Returns 400 if path 'shape' matches, but failed to parse a path parameter, using a custom decode failure handler"
    )(_ => pureResult(Either.right[Unit, Unit](()))) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/customer/asd/orders/2").send(backend).map { response =>
        response.body shouldBe Left("Invalid value for: path parameter customer_id")
        response.code shouldBe StatusCode.BadRequest
      }
    },
    testServer(
      in_path_fixed_capture_fixed_capture,
      "Returns 404 if path 'shape' doesn't match"
    )(_ => pureResult(Either.right[Unit, Unit](()))) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/customer").send(backend).map(response => response.code shouldBe StatusCode.NotFound) >>
        basicRequest.get(uri"$baseUri/customer/asd").send(backend).map(response => response.code shouldBe StatusCode.NotFound) >>
        basicRequest
          .get(uri"$baseUri/customer/asd/orders/2/xyz")
          .send(backend)
          .map(response => response.code shouldBe StatusCode.NotFound)
    }, {
      testServer(
        endpoint.get.in("customer" / path[Int]("customer_id")),
        "Returns 400 if path 'shape' matches, but failed to parse a path parameter"
      )(_ => pureResult(Either.right[Unit, Unit](()))) { (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/customer/asd").send(backend).map { response =>
          response.body shouldBe Left("Invalid value for: path parameter customer_id")
          response.code shouldBe StatusCode.BadRequest
        }
      }
    }, {
      import DefaultDecodeFailureHandler.OnDecodeFailure._
      testServer(
        "Tries next endpoint if path 'shape' matches, but validation fails, using .onDecodeFailureNextEndpoint",
        NonEmptyList.of(
          route(
            List(
              endpoint.get
                .in("customer" / path[Int]("customer_id").validate(Validator.min(10)).onDecodeFailureNextEndpoint)
                .out(stringBody)
                .serverLogic[F]((_: Int) => pureResult("e1".asRight[Unit])),
              endpoint.get
                .in("customer" / path[String]("customer_id"))
                .out(stringBody)
                .serverLogic[F]((_: String) => pureResult("e2".asRight[Unit]))
            )
          )
        )
      ) { (backend, baseUri) =>
        basicStringRequest.get(uri"$baseUri/customer/20").send(backend).map(_.body shouldBe "e1") >>
          basicStringRequest.get(uri"$baseUri/customer/2").send(backend).map(_.body shouldBe "e2")
      }
    }
  )

  def serverSecurityLogicTests(): List[Test] = List(
    testServerLogic(
      endpoint
        .securityIn(query[String]("x"))
        .serverSecurityLogic(v => pureResult(v.toInt.asRight[Unit]))
        .in(query[String]("y"))
        .out(plainBody[Int])
        .serverLogic { x => y => pureResult((x * y.toInt).asRight[Unit]) },
      "server security logic - one input"
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?x=2&y=3").send(backend).map(_.body shouldBe Right("6"))
    },
    testServerLogic(
      endpoint
        .securityIn(query[String]("x"))
        .securityIn(query[String]("y"))
        .serverSecurityLogic { case (x, y) => pureResult((x.toInt + y.toInt).asRight[Unit]) }
        .in(query[String]("z"))
        .in(query[String]("u"))
        .out(plainBody[Int])
        .serverLogic { xy => { case (z, u) => pureResult((xy * z.toInt * u.toInt).asRight[Unit]) } },
      "server security logic - multiple inputs"
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?x=2&y=3&z=5&u=7").send(backend).map(_.body shouldBe Right("175"))
    }
  )

  def inputStreamTests(): List[Test] = List(
    testServer(in_input_stream_out_input_stream)((is: InputStream) =>
      pureResult((new ByteArrayInputStream(inputStreamToByteArray(is)): InputStream).asRight[Unit])
    ) { (backend, baseUri) => basicRequest.post(uri"$baseUri/api/echo").body("mango").send(backend).map(_.body shouldBe Right("mango")) },
    testServer(in_string_out_stream_with_header)(_ => pureResult(Right((new ByteArrayInputStream(Array.fill[Byte](128)(0)), Some(128))))) {
      (backend, baseUri) =>
        basicRequest.post(uri"$baseUri/api/echo").body("test string body").response(asByteArray).send(backend).map { r =>
          r.body.map(_.length) shouldBe Right(128)
          r.body.map(_.foreach(b => b shouldBe 0))
          r.headers.map(_.name.toLowerCase) should contain(HeaderNames.ContentLength.toLowerCase)
          r.header(HeaderNames.ContentLength) shouldBe Some("128")
        }
    }
  )

  def testPayloadTooLarge[I](
      testedEndpoint: PublicEndpoint[I, Unit, I, Any],
      maxLength: Int
  ) = testServer(
    testedEndpoint.maxRequestBodyLength(maxLength.toLong),
    "checks payload limit and returns 413 on exceeded max content length (request)"
  )(i => pureResult(i.asRight[Unit])) { (backend, baseUri) =>
    val tooLargeBody: String = List.fill(maxLength + 1)('x').mkString
    basicRequest.post(uri"$baseUri/api/echo").body(tooLargeBody).send(backend).map(_.code shouldBe StatusCode.PayloadTooLarge)
  }
  def testPayloadWithinLimit[I](
      testedEndpoint: PublicEndpoint[I, Unit, I, Any],
      maxLength: Int
  ) = testServer(
    testedEndpoint.attribute(AttributeKey[MaxContentLength], MaxContentLength(maxLength.toLong)),
    "checks payload limit and returns OK on content length  below or equal max (request)"
  )(i => pureResult(i.asRight[Unit])) { (backend, baseUri) =>
    val fineBody: String = List.fill(maxLength)('x').mkString
    basicRequest.post(uri"$baseUri/api/echo").body(fineBody).send(backend).map(_.code shouldBe StatusCode.Ok)
  }

  def maxContentLengthTests: List[Test] = {
    val maxLength = 17000 // To generate a few chunks of default size 8192 + some extra bytes
    List(
      testPayloadTooLarge(in_string_out_string, maxLength),
      testPayloadTooLarge(in_byte_array_out_byte_array, maxLength),
      testPayloadTooLarge(in_file_out_file, maxLength),
      testServer(
        in_input_stream_out_input_stream.maxRequestBodyLength(maxLength.toLong),
        "checks payload limit and returns 413 on exceeded max content length (request)"
      )(i => {
        // Forcing server logic to try to drain the InputStream
        suspendResult(i.readAllBytes()).map(_ => new ByteArrayInputStream(Array.empty[Byte]).asRight[Unit])
      }) { (backend, baseUri) =>
        val tooLargeBody: String = List.fill(maxLength + 1)('x').mkString
        basicRequest.post(uri"$baseUri/api/echo").body(tooLargeBody).response(asByteArray).send(backend).map { r =>
          r.code shouldBe StatusCode.PayloadTooLarge
        }
      },
      testPayloadTooLarge(in_byte_buffer_out_byte_buffer, maxLength),
      testPayloadWithinLimit(in_string_out_string, maxLength),
      testServer(
        in_input_stream_out_input_stream.maxRequestBodyLength(maxLength.toLong),
        "checks payload limit and returns OK on content length  below or equal max (request)"
      )(i => {
        // Forcing server logic to drain the InputStream
        suspendResult(i.readAllBytes()).map(_ => new ByteArrayInputStream(Array.empty[Byte]).asRight[Unit])
      }) { (backend, baseUri) =>
        val tooLargeBody: String = List.fill(maxLength)('x').mkString
        basicRequest.post(uri"$baseUri/api/echo").body(tooLargeBody).response(asByteArray).send(backend).map { r =>
          r.code shouldBe StatusCode.Ok
        }
      },
      testPayloadWithinLimit(in_byte_array_out_byte_array, maxLength),
      testPayloadWithinLimit(in_file_out_file, maxLength),
      testPayloadWithinLimit(in_byte_buffer_out_byte_buffer, maxLength)
    )
  }

  def exceptionTests(): List[Test] = List(
    testServer(endpoint, "handle exceptions")(_ => throw new RuntimeException()) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri").send(backend).map(_.code shouldBe StatusCode.InternalServerError)
    },
    testServer(
      "recover errors from exceptions",
      NonEmptyList.of(
        route(endpoint.in(query[String]("name")).errorOut(jsonBody[FruitError]).out(stringBody).serverLogicRecoverErrors(throwFruits))
      )
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?name=apple").send(backend).map(_.body shouldBe Right("ok")) >>
        basicRequest.get(uri"$baseUri?name=banana").send(backend).map { r =>
          r.code shouldBe StatusCode.BadRequest
          r.body shouldBe Left("""{"msg":"no bananas","code":102}""")
        } >>
        basicRequest.get(uri"$baseUri?name=orange").send(backend).map { r =>
          r.code shouldBe StatusCode.InternalServerError
          r.body shouldBe Symbol("left")
        }
    },
    testServer(
      "fail when status is 204 or 304, but there's a body",
      NonEmptyList.of(
        route(
          List(
            endpoint.in("no_content").out(jsonBody[Unit]).out(statusCode(StatusCode.NoContent)).serverLogicSuccess[F](_ => pureResult(())),
            endpoint
              .in("not_modified")
              .out(jsonBody[Unit])
              .out(statusCode(StatusCode.NotModified))
              .serverLogicSuccess[F](_ => pureResult(())),
            endpoint
              .in("one_of")
              .in(query[String]("select_err"))
              .errorOut(
                sttp.tapir.oneOf[ErrorInfo](
                  oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[NotFound])),
                  oneOfVariant(statusCode(StatusCode.NoContent).and(jsonBody[NoContentData]))
                )
              )
              .serverLogic[F] { selectErr =>
                if (selectErr == "no_content")
                  pureResult[F, Either[ErrorInfo, Unit]](Left(NoContentData("error")))
                else
                  pureResult[F, Either[ErrorInfo, Unit]](Left(NotFound("error")))
              }
          )
        )
      )
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/no_content").send(backend).map(_.code shouldBe StatusCode.InternalServerError) >>
        basicRequest.get(uri"$baseUri/not_modified").send(backend).map(_.code shouldBe StatusCode.InternalServerError) >>
        basicRequest.get(uri"$baseUri/one_of?select_err=no_content").send(backend).map(_.code shouldBe StatusCode.InternalServerError) >>
        basicRequest.get(uri"$baseUri/one_of?select_err=not_found").send(backend).map(_.code shouldBe StatusCode.NotFound)
    }
  )

  def throwFruits(name: String): F[String] =
    name match {
      case "apple"  => pureResult("ok")
      case "banana" => suspendResult(throw FruitError("no bananas", 102))
      case n        => suspendResult(throw new IllegalArgumentException(n))
    }
}
sealed trait Animal extends EnumEntry with EnumEntry.Lowercase

object Animal extends Enum[Animal] with TapirCodecEnumeratum {
  case object Dog extends Animal
  case object Cat extends Animal

  override def values = findValues
}

sealed trait ErrorInfo
case class NotFound(what: String) extends ErrorInfo
case class NoContentData(msg: String) extends ErrorInfo
