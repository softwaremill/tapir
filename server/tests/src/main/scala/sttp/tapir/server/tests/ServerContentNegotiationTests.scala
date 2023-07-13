package sttp.tapir.server.tests

import cats.effect.IO
import cats.implicits._
import org.scalatest
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir.tests.Basic.in_root_path
import sttp.tapir.tests.ContentNegotiation._

import sttp.tapir.tests._
import sttp.tapir.tests.data._

class ServerContentNegotiationTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit
    m: MonadError[F]
) {
  import createServerTest._
  import sttp.tapir.tests.Basic.byte_array

  def tests(): List[Test] = List(
    testServer(out_json_xml_text_common_schema)(_ => pureResult(Organization("sml").asRight[Unit])) { (backend, baseUri) =>
      def ok(body: String) = (StatusCode.Ok, body.asRight[String])
      def notAcceptable() = (StatusCode.NotAcceptable, "".asLeft[String])

      val cases: Map[(String, String), (StatusCode, Either[String, String])] = Map(
        ("application/json", "*") -> ok(organizationJson),
        ("application/xml", "*") -> ok(organizationXml),
        ("text/html", "*") -> ok(organizationHtmlUtf8),
        ("text/html;q=0.123, application/json;q=0.124, application/xml;q=0.125", "*") -> ok(organizationXml),
        ("application/xml, application/json", "*") -> ok(organizationXml),
        ("application/json, application/xml", "*") -> ok(organizationJson),
        ("application/xml;q=0.5, application/json;q=0.9", "*") -> ok(organizationJson),
        ("application/json;q=0.5, application/xml;q=0.5", "*") -> ok(organizationJson),
        ("application/json, application/xml, text/*;q=0.1", "iso-8859-1") -> ok(organizationHtmlIso),
        ("text/*;q=0.5, application/*", "*") -> ok(organizationJson),
        ("text/*;q=0.5, application/xml;q=0.3", "utf-8") -> ok(organizationHtmlUtf8),
        ("text/html", "utf-8;q=0.9, iso-8859-1;q=0.5") -> ok(organizationHtmlUtf8),
        ("text/html", "utf-8;q=0.5, iso-8859-1;q=0.9") -> ok(organizationHtmlIso),
        ("text/html", "utf-8, iso-8859-1") -> ok(organizationHtmlUtf8),
        ("text/html", "iso-8859-1, utf-8") -> ok(organizationHtmlIso),
        ("*/*", "iso-8859-1") -> ok(organizationHtmlIso),
        ("*/*", "*;q=0.5, iso-8859-1") -> ok(organizationHtmlIso),
        //
        ("text/html", "iso-8859-5") -> notAcceptable(),
        ("text/csv", "*") -> notAcceptable(),
        // in case of an invalid accepts header, the first mapping should be used
        ("text/html;(q)=xxx", "utf-8") -> ok(organizationJson)
      )

      cases.foldLeft(IO(scalatest.Assertions.succeed))((prev, next) => {
        val ((accept, acceptCharset), (code, body)) = next
        prev >> basicRequest
          .get(uri"$baseUri/content-negotiation/organization")
          .header(HeaderNames.Accept, accept)
          .header(HeaderNames.AcceptCharset, acceptCharset)
          .send(backend)
          .map { response =>
            response.code shouldBe code
            response.body shouldBe body
          }
      })
    },
    testServer(out_default_json_or_xml, testNameSuffix = "takes first content type when no accepts header")(_ =>
      pureResult(Organization("sml").asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/content-negotiation/organization").send(backend).map(_.body shouldBe Right("{\"name\":\"sml\"}"))
    },
    testServer(out_default_xml_or_json, testNameSuffix = "takes first content type when no accepts header")(_ =>
      pureResult(Organization("sml").asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/content-negotiation/organization").send(backend).map(_.body shouldBe Right("<name>sml-xml</name>"))
    },
    testServer(in_root_path, testNameSuffix = "accepts header without output body")(_ => pureResult(().asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.header(HeaderNames.Accept, "text/plain").get(uri"$baseUri").send(backend).map(_.code shouldBe StatusCode.Ok)
    },
    testServer(byte_array, testNameSuffix = "check response with header AcceptCharset")(_ => pureResult(Array.emptyByteArray.asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/bytes").header(HeaderNames.AcceptCharset, "utf-8").send(backend).map(_.code shouldBe StatusCode.Ok)
    }
  )
}
