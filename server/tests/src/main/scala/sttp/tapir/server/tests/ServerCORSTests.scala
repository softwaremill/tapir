package sttp.tapir.server.tests

import cats.implicits.catsSyntaxEitherId
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.model.headers.Origin
import sttp.model.{Header, HeaderNames, Method, StatusCode, Uri}
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedMethods
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}
import sttp.tapir.server.tests.ServerCORSTests.{Body, fixedStringBody, noop, preflightRequest}
import sttp.tapir.tests.Test

import scala.concurrent.duration._

class ServerCORSTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit m: MonadError[F]) {

  import createServerTest._

  def tests(): List[Test] = preflightTests ++ requestTests

  val preflightTests = List(
    testServer(
      endpoint.post.in("path").out(stringBody),
      "CORS with default config; valid preflight request",
      _.corsInterceptor(CORSInterceptor.default[F])
    )(_ => pureResult("foo".asRight[Unit])) { (backend, baseUri) =>
      preflightRequest(baseUri)
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.NoContent
          response.headers should contain allOf (
            Header.accessControlAllowOrigin("*"),
            Header.accessControlAllowMethods(CORSConfig.default.allowedMethods.asInstanceOf[AllowedMethods.Some].methods.toList: _*),
            Header.accessControlAllowHeaders("X-Foo"),
            Header.vary(HeaderNames.AccessControlRequestMethod, HeaderNames.AccessControlRequestHeaders)
          )
          response.headers.map(_.name) should contain noneOf (
            HeaderNames.AccessControlMaxAge,
            HeaderNames.AccessControlAllowCredentials,
            HeaderNames.AccessControlExposeHeaders
          )
          response.body shouldBe Right("") // since the server logic shouldn't be run when handling a preflight request
        }
    },
    testServer(
      endpoint.post.in("path"),
      "CORS with specific allowed origin, method, headers, allowed credentials and max age; preflight request with matching origin, method and headers",
      _.corsInterceptor(
        CORSInterceptor.customOrThrow[F](
          CORSConfig.default
            .allowOrigin(Origin.Host("https", "example.com"))
            .allowMethods(Method.POST)
            .allowHeaders("X-Foo", "X-Bar")
            .allowCredentials
            .maxAge(42.seconds)
        )
      )
    )(noop) { (backend, baseUri) =>
      preflightRequest(baseUri)
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.NoContent
          response.headers should contain allOf (
            Header.accessControlAllowOrigin("https://example.com"),
            Header.accessControlAllowMethods(Method.POST),
            Header.accessControlAllowHeaders("X-Foo", "X-Bar"),
            Header.accessControlAllowCredentials(true),
            Header.accessControlMaxAge(42),
            Header.vary(HeaderNames.Origin, HeaderNames.AccessControlRequestMethod, HeaderNames.AccessControlRequestHeaders)
          )
          response.headers.map(_.name) should contain noneOf (HeaderNames.AccessControlMaxAge, HeaderNames.AccessControlAllowCredentials)
        }
    },
    testServer(
      endpoint.post.in("path"),
      "CORS with multiple allowed origins, method, headers, allowed credentials and max age; preflight request with matching origin, method and headers",
      _.corsInterceptor(
        CORSInterceptor.customOrThrow[F](
          CORSConfig.default
            .allowMatchingOrigins(Set("https://example1.com", "https://example2.com"))
            .allowMethods(Method.POST)
            .allowHeaders("X-Foo", "X-Bar")
            .allowCredentials
            .maxAge(42.seconds)
        )
      )
    )(noop) { (backend, baseUri) =>
      preflightRequest(baseUri, "example2.com")
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.NoContent
          response.headers should contain allOf (
            Header.accessControlAllowOrigin("https://example2.com"),
            Header.accessControlAllowMethods(Method.POST),
            Header.accessControlAllowHeaders("X-Foo", "X-Bar"),
            Header.accessControlAllowCredentials(true),
            Header.accessControlMaxAge(42),
            Header.vary(HeaderNames.Origin, HeaderNames.AccessControlRequestMethod, HeaderNames.AccessControlRequestHeaders)
          )
          response.headers.map(_.name) should contain noneOf (HeaderNames.AccessControlMaxAge, HeaderNames.AccessControlAllowCredentials)
        }
    },
    testServer(
      endpoint.post.in("path"),
      "CORS with specific allowed origin; preflight request with unsupported origin",
      _.corsInterceptor(CORSInterceptor.customOrThrow[F](CORSConfig.default.allowOrigin(Origin.Host("https", "example.com"))))
    )(noop) { (backend, baseUri) =>
      preflightRequest(baseUri, "unsupported.com")
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.NoContent
          response.headers.map(_.name) should not contain (HeaderNames.AccessControlAllowOrigin)
        }
    },
    testServer(
      endpoint.post.in("path"),
      "CORS with multiple allowed origins; preflight request with unsupported origin",
      _.corsInterceptor(
        CORSInterceptor.customOrThrow[F](CORSConfig.default.allowMatchingOrigins(Set("https://example1.com", "https://example2.com")))
      )
    )(noop) { (backend, baseUri) =>
      preflightRequest(baseUri, "unsupported.com")
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.NoContent
          response.headers.map(_.name) should not contain (HeaderNames.AccessControlAllowOrigin)
        }
    },
    testServer(
      endpoint.post.in("path"),
      "CORS with specific allowed method; preflight request with unsupported method",
      _.corsInterceptor(CORSInterceptor.customOrThrow[F](CORSConfig.default.allowMethods(Method.PUT)))
    )(noop) { (backend, baseUri) =>
      preflightRequest(baseUri)
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.NoContent
          response.headers.map(_.name) should not contain (HeaderNames.AccessControlAllowMethods)
        }
    },
    testServer(
      endpoint.post.in("path"),
      "CORS with specific allowed headers; preflight request with unsupported header",
      _.corsInterceptor(CORSInterceptor.customOrThrow[F](CORSConfig.default.allowHeaders("X-Bar")))
    )(noop) { (backend, baseUri) =>
      preflightRequest(baseUri)
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.NoContent
          response.headers.map(_.name) should not contain (HeaderNames.AccessControlAllowHeaders)
        }
    },
    testServer(
      endpoint.post.in("path"),
      "CORS with reflected allowed headers; preflight request",
      _.corsInterceptor(CORSInterceptor.customOrThrow[F](CORSConfig.default.reflectHeaders))
    )(noop) { (backend, baseUri) =>
      preflightRequest(baseUri)
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.NoContent
          response.headers should contain(Header.accessControlAllowHeaders("X-Foo"))
        }
    },
    testServer(
      endpoint.post.in("path"),
      "CORS with custom response code for preflight requests; valid preflight request",
      _.corsInterceptor(CORSInterceptor.customOrThrow[F](CORSConfig.default.preflightResponseStatusCode(StatusCode.Ok)))
    )(noop) { (backend, baseUri) =>
      preflightRequest(baseUri)
        .send(backend)
        .map(_.code shouldBe StatusCode.Ok)
    },
    testServer(
      endpoint.options.in("path"),
      "CORS with default config; preflight request without Origin (non-CORS)",
      _.corsInterceptor(CORSInterceptor.default[F])
    )(noop) { (backend, baseUri) =>
      basicRequest
        .options(uri"$baseUri/path")
        .headers(
          Header.accessControlRequestMethod(Method.POST),
          Header.accessControlRequestHeaders("X-Foo")
        )
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.Ok
          response.headers.map(_.name) should contain noneOf (
            HeaderNames.AccessControlAllowOrigin,
            HeaderNames.AccessControlAllowMethods,
            HeaderNames.AccessControlAllowHeaders
          )
        }
    }
  )

  val requestTests = List(
    testServer(
      endpoint.get.in("path").out(stringBody),
      "CORS with default config; valid CORS request",
      _.corsInterceptor(CORSInterceptor.default[F])
    )(fixedStringBody) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri/path")
        .header(Header.origin(Origin.Host("https", "example.com")))
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.Ok
          response.headers should contain(Header.accessControlAllowOrigin("*"))
          response.headers.map(_.name) should not contain (HeaderNames.AccessControlExposeHeaders)
          response.body shouldBe Right(Body)
        }
    },
    testServer(
      endpoint.get.in("path").out(stringBody),
      "CORS with custom allowed origin, allowed credentials and exposed headers; valid CORS request",
      _.corsInterceptor(
        CORSInterceptor.customOrThrow[F](
          CORSConfig.default.allowOrigin(Origin.Host("https", "example.com")).allowCredentials.exposeHeaders("X-Bar", "X-Baz")
        )
      )
    )(fixedStringBody) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri/path")
        .header(Header.origin(Origin.Host("https", "example.com")))
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.Ok
          response.headers should contain allOf (
            Header.accessControlAllowOrigin("https://example.com"),
            Header.accessControlExposeHeaders("X-Bar", "X-Baz"),
            Header.accessControlAllowCredentials(true),
            Header.vary(HeaderNames.Origin)
          )
          response.body shouldBe Right(Body)
        }
    },
    testServer(
      endpoint.get.in("path").out(stringBody),
      "CORS with all headers exposed; valid CORS request",
      _.corsInterceptor(CORSInterceptor.customOrThrow[F](CORSConfig.default.exposeAllHeaders))
    )(fixedStringBody) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri/path")
        .header(Header.origin(Origin.Host("https", "example.com")))
        .send(backend)
        .map { response =>
          response.code shouldBe StatusCode.Ok
          response.headers should contain(Header.accessControlExposeHeaders("*"))
          response.body shouldBe Right(Body)
        }
    }
  )
}

object ServerCORSTests {
  private val Body = "chrząszcz brzmi w trzcinie"

  private def noop[F[_]: MonadError]: Any => F[Either[Unit, Unit]] = _ => pureResult(().asRight[Unit])
  private def fixedStringBody[F[_]: MonadError]: Any => F[Either[Unit, String]] = _ => pureResult(Body.asRight[Unit])

  private def preflightRequest(baseUri: Uri, originHost: String = "example.com") = basicRequest
    .options(uri"$baseUri/path")
    .headers(
      Header.origin(Origin.Host("https", originHost)),
      Header.accessControlRequestMethod(Method.POST),
      Header.accessControlRequestHeaders("X-Foo")
    )
}
