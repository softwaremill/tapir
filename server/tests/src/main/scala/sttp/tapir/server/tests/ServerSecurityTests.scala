package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.model.Uri.QuerySegment
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.model.{StatusCode, _}
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.tests.Security.{
  in_security_apikey_header_in_amount_out_string,
  in_security_apikey_header_out_string,
  in_security_apikey_query_out_string,
  in_security_basic_out_string,
  in_security_bearer_out_string
}
import sttp.tapir.tests.Test

class ServerSecurityTests[F[_], S, ROUTE](createServerTest: CreateServerTest[F, S, ROUTE])(implicit m: MonadError[F]) extends Matchers {
  import createServerTest._
  private val Realm = "realm"

  private val base = endpoint.post.securityIn("secret" / path[Long]("id")).in(query[String]("q"))

  private val basic = base.securityIn(auth.basic[UsernamePassword](WWWAuthenticateChallenge.basic(Realm)))
  private val bearer = base.securityIn(auth.bearer[String](WWWAuthenticateChallenge.bearer(Realm)))
  private val apiKeyInQuery = base.securityIn(auth.apiKey(query[String]("token"), WWWAuthenticateChallenge("ApiKey").realm(Realm)))
  private val apiKeyInHeader = base.securityIn(auth.apiKey(header[String]("x-api-key"), WWWAuthenticateChallenge("ApiKey").realm(Realm)))

  private val result = m.unit(().asRight[Unit])

  private def validRequest(uri: Uri): Request[Either[String, String], Any] =
    basicRequest.post(uri.addPath("secret", "1234").addQuerySegment(QuerySegment.KeyValue("q", "x")))
  private def invalidRequest(uri: Uri): Request[Either[String, String], Any] = basicRequest.post(uri.addPath("secret", "1234"))

  private val endpoints = {
    def putSecretInQuery(uri: Uri): Identity[Uri] = uri.addQuerySegment(QuerySegment.KeyValue("token", "supersecret"))
    List(
      ("basic", basic, (r: Request[_, Any]) => r.header("Authorization", "Basic dXNlcjpzZWNyZXQ=")),
      ("bearer", bearer, (r: Request[_, Any]) => r.header("Authorization", "Bearer kajsdhf[")),
      ("lower case bearer", bearer, (r: Request[_, Any]) => r.header("Authorization", "bearer kajsdhf[")),
      (
        "apiKey in query param",
        apiKeyInQuery,
        (r: RequestT[Identity, _, Any]) => r.copy(uri = putSecretInQuery(r.uri))
      ),
      ("apiKey in header", apiKeyInHeader, (r: Request[_, Any]) => r.header("x-api-key", "secret api key"))
    )
  }

  def tests(): List[Test] = List(
    testServerLogic(
      in_security_apikey_header_out_string
        .serverSecurityLogic((s: String) => pureResult(s.asRight[Unit]))
        .serverLogic(s => _ => pureResult(s.asRight[Unit]))
    ) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri/auth").header("X-Api-Key", "1234").send(backend).map(_.body shouldBe "1234")
    },
    testServerLogic(
      in_security_apikey_header_in_amount_out_string
        .serverSecurityLogic((s: String) => pureResult(s.asRight[Unit]))
        .serverLogic(s => a => pureResult(s"$s amount=$a".asRight[Unit]))
    ) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri/auth?amount=61").header("X-Api-Key", "1234").send(backend).map(_.body shouldBe "1234 amount=61")
    },
    testServerLogic(
      in_security_apikey_query_out_string
        .serverSecurityLogic((s: String) => pureResult(s.asRight[Unit]))
        .serverLogic(s => _ => pureResult(s.asRight[Unit]))
    ) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri/auth?api-key=1234").send(backend).map(_.body shouldBe "1234")
    },
    testServerLogic(
      in_security_basic_out_string
        .serverSecurityLogic((up: UsernamePassword) => pureResult(up.toString.asRight[Unit]))
        .serverLogic(s => _ => pureResult(s.asRight[Unit]))
    ) { (backend, baseUri) =>
      basicStringRequest
        .get(uri"$baseUri/auth")
        .auth
        .basic("teddy", "bear")
        .send(backend)
        .map(_.body shouldBe "UsernamePassword(teddy,Some(bear))")
    },
    testServerLogic(
      in_security_bearer_out_string
        .serverSecurityLogic((s: String) => pureResult(s.asRight[Unit]))
        .serverLogic(s => _ => pureResult(s.asRight[Unit]))
    ) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri/auth").auth.bearer("1234").send(backend).map(_.body shouldBe "1234")
    }
  ) ++
    correctAuthTests ++
    missingAuthTests ++
    missingAuthWithEndpointHidingTests ++
    badRequestWithCorrectAuthTests ++
    badRequestWithCorrectAuthAndEndpointHidingTests

  private def missingAuthTests = endpoints.map { case (authType, endpoint, _) =>
    testServerLogic(endpoint.serverSecurityLogic(_ => result).serverLogic(_ => _ => result), s"missing $authType") { (backend, baseUri) =>
      validRequest(baseUri).send(backend).map { r =>
        r.code shouldBe StatusCode.Unauthorized
        r.header("WWW-Authenticate") shouldBe Some(expectedChallenge(authType))
      }
    }
  }

  private def missingAuthWithEndpointHidingTests = endpoints.map { case (authType, endpoint, _) =>
    testServerLogic(
      endpoint.serverSecurityLogic(_ => result).serverLogic(_ => _ => result),
      s"missing $authType with endpoint hiding",
      Some(DefaultDecodeFailureHandler.handlerHideUnauthorized)
    ) { (backend, baseUri) =>
      validRequest(baseUri).send(backend).map { r =>
        r.code shouldBe StatusCode.NotFound
        r.header("WWW-Authenticate") shouldBe None
      }
    }
  }

  private def expectedChallenge(authType: String) = authType match {
    case "basic"                                      => s"""Basic realm="$Realm""""
    case "bearer" | "lower case bearer"               => s"""Bearer realm="$Realm""""
    case "apiKey in query param" | "apiKey in header" => s"""ApiKey realm="$Realm""""
  }

  private def correctAuthTests = endpoints.map { case (authType, endpoint, auth) =>
    testServerLogic(endpoint.serverSecurityLogic(_ => result).serverLogic(_ => _ => result), s"correct $authType") { (backend, baseUri) =>
      auth(validRequest(baseUri))
        .send(backend)
        .map(_.code shouldBe StatusCode.Ok)
    }
  }

  private def badRequestWithCorrectAuthTests = endpoints.map { case (authType, endpoint, auth) =>
    testServerLogic(endpoint.serverSecurityLogic(_ => result).serverLogic(_ => _ => result), s"invalid request $authType") {
      (backend, baseUri) =>
        auth(invalidRequest(baseUri)).send(backend).map(_.code shouldBe StatusCode.BadRequest)
    }
  }

  private def badRequestWithCorrectAuthAndEndpointHidingTests = endpoints.map { case (authType, endpoint, auth) =>
    testServerLogic(
      endpoint.serverSecurityLogic(_ => result).serverLogic(_ => _ => result),
      s"invalid request $authType with endpoint hiding",
      Some(DefaultDecodeFailureHandler.handlerHideUnauthorized)
    ) { (backend, baseUri) =>
      auth(invalidRequest(baseUri)).send(backend).map(_.code shouldBe StatusCode.NotFound)
    }
  }
}
