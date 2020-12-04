package sttp.tapir.server.tests

import cats.effect.IO
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.model._
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.tests.Test
import cats.implicits._
import sttp.model.Uri.QuerySegment
import sttp.tapir.model.UsernamePassword

class ServerAuthenticationTests[F[_], S, ROUTE](backend: SttpBackend[IO, Any], serverTests: ServerTests[F, S, ROUTE])(implicit
    m: MonadError[F]
) extends Matchers {
  import serverTests._

  private val base = endpoint.post.in("secret" / path[Long]("id")).in(query[String]("q"))

  private val basic = base.in(auth.basic[UsernamePassword])
  private val bearer = base.in(auth.bearer[String])
  private val apiKeyInQuery = base.in(auth.apiKey(query[String]("token")))
  private val apiKeyInHeader = base.in(auth.apiKey(header[String]("x-api-key")))

  private val result = m.unit(().asRight[Unit])

  private def validRequest(uri: Uri): Request[Either[String, String], Any] =
    basicRequest.post(uri.addPath("secret", "1234").querySegment(QuerySegment.KeyValue("q", "x")))
  private def invalidRequest(uri: Uri): Request[Either[String, String], Any] = basicRequest.post(uri.addPath("secret", "1234"))

  private val endpoints = {
    def putSecretInQuery(uri: Uri): Identity[Uri] = uri.querySegment(QuerySegment.KeyValue("token", "supersecret"))
    List(
      ("basic", basic, (r: Request[_, Any]) => r.header("Authorization", "Basic dXNlcjpzZWNyZXQ=")),
      ("bearer", bearer, (r: Request[_, Any]) => r.header("Authorization", "Bearer kajsdhf[")),
      (
        "apiKey in query param",
        apiKeyInQuery,
        (r: RequestT[Identity, _, Any]) => r.copy(uri = putSecretInQuery(r.uri))
      ),
      ("apiKey in header", apiKeyInHeader, (r: Request[_, Any]) => r.header("x-api-key", "secret api key"))
    )
  }

  def tests(): List[Test] = missingAuthTests ++ correctAuthTests ++ badRequestWithCorrectAuthTests

  private def missingAuthTests = endpoints.map { case (authType, endpoint, _) =>
    testServer(endpoint, s"missing $authType")(_ => result) { baseUri =>
      validRequest(baseUri).send(backend).map(_.code shouldBe StatusCode.Unauthorized)
    }
  }

  private def correctAuthTests = endpoints.map { case (authType, endpoint, auth) =>
    testServer(endpoint, s"correct $authType")(_ => result) { baseUri =>
      auth(validRequest(baseUri))
        .send(backend)
        .map(_.code shouldBe StatusCode.Ok)
    }
  }

  private def badRequestWithCorrectAuthTests = endpoints.map { case (authType, endpoint, auth) =>
    testServer(endpoint, s"invalid request $authType")(_ => result) { baseUri =>
      auth(invalidRequest(baseUri)).send(backend).map(_.code shouldBe StatusCode.BadRequest)
    }
  }
}
