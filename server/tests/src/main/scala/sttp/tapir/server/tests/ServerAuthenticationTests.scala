package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.model.Uri.QuerySegment
import sttp.model.{StatusCode, _}
import sttp.monad.MonadError
import sttp.tapir.EndpointInput.WWWAuthenticate
import sttp.tapir._
import sttp.tapir.model.UsernamePassword
import sttp.tapir.tests.Authentication.{
  in_auth_apikey_header_out_string,
  in_auth_apikey_query_out_string,
  in_auth_basic_out_string,
  in_auth_bearer_out_string
}
import sttp.tapir.tests.Test

class ServerAuthenticationTests[F[_], S, ROUTE](createServerTest: CreateServerTest[F, S, ROUTE])(implicit m: MonadError[F])
    extends Matchers {
  import createServerTest._
  private val Realm = "realm"

  private val base = endpoint.post.in("secret" / path[Long]("id")).in(query[String]("q"))

  private val basic = base.in(auth.basic[UsernamePassword](WWWAuthenticate.basic(Realm)))
  private val bearer = base.in(auth.bearer[String](WWWAuthenticate.bearer(Realm)))
  private val apiKeyInQuery = base.in(auth.apiKey(query[String]("token"), WWWAuthenticate.apiKey(Realm)))
  private val apiKeyInHeader = base.in(auth.apiKey(header[String]("x-api-key"), WWWAuthenticate.apiKey(Realm)))

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
    // auth
    testServer(in_auth_apikey_header_out_string)((s: String) => pureResult(s.asRight[Unit])) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri/auth").header("X-Api-Key", "1234").send(backend).map(_.body shouldBe "1234")
    },
    testServer(in_auth_apikey_query_out_string)((s: String) => pureResult(s.asRight[Unit])) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri/auth?api-key=1234").send(backend).map(_.body shouldBe "1234")
    },
    testServer(in_auth_basic_out_string)((up: UsernamePassword) => pureResult(up.toString.asRight[Unit])) { (backend, baseUri) =>
      basicStringRequest
        .get(uri"$baseUri/auth")
        .auth
        .basic("teddy", "bear")
        .send(backend)
        .map(_.body shouldBe "UsernamePassword(teddy,Some(bear))")
    },
    testServer(in_auth_bearer_out_string)((s: String) => pureResult(s.asRight[Unit])) { (backend, baseUri) =>
      basicStringRequest.get(uri"$baseUri/auth").auth.bearer("1234").send(backend).map(_.body shouldBe "1234")
    }
  ) ++ missingAuthTests ++ correctAuthTests ++ badRequestWithCorrectAuthTests

  private def missingAuthTests = endpoints.map { case (authType, endpoint, _) =>
    testServer(endpoint, s"missing $authType")(_ => result) { (backend, baseUri) =>
      validRequest(baseUri).send(backend).map { r =>
        r.code shouldBe StatusCode.Unauthorized
        r.header("WWW-Authenticate") shouldBe Some(expectedChallenge(authType))
      }
    }
  }

  private def expectedChallenge(authType: String) = authType match {
    case "basic"                                      => s"""Basic realm="$Realm""""
    case "bearer" | "lower case bearer"               => s"""Bearer realm="$Realm""""
    case "apiKey in query param" | "apiKey in header" => s"""ApiKey realm="$Realm""""
  }

  private def correctAuthTests = endpoints.map { case (authType, endpoint, auth) =>
    testServer(endpoint, s"correct $authType")(_ => result) { (backend, baseUri) =>
      auth(validRequest(baseUri))
        .send(backend)
        .map(_.code shouldBe StatusCode.Ok)
    }
  }

  private def badRequestWithCorrectAuthTests = endpoints.map { case (authType, endpoint, auth) =>
    testServer(endpoint, s"invalid request $authType")(_ => result) { (backend, baseUri) =>
      auth(invalidRequest(baseUri)).send(backend).map(_.code shouldBe StatusCode.BadRequest)
    }
  }
}
