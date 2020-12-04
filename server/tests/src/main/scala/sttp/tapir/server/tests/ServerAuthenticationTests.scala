package sttp.tapir.server.tests

import cats.effect.IO
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.{AuthChallenge, AuthenticationFailureHandler, ServerDefaults}
import sttp.tapir.tests.{Test}
import cats.implicits._

class ServerAuthenticationTests[F[_], S, ROUTE](backend: SttpBackend[IO, Any], serverTests: ServerTests[F, S, ROUTE])(implicit
    m: MonadError[F]
) extends Matchers {
  import serverTests._
  import AuthenticationFailureHandler._

  private val authedEndpoint: Endpoint[String, Unit, Unit, Any] = endpoint.in(header[String]("Authorization"))
  private val realm = "access to the secret place"

  private val challengeBasic =
    ServerDefaults.decodeFailureHandlerWithAuthentication(handleMissingAuthorizationHeader(AuthChallenge.basic(realm)))
  private val challengeBearer =
    ServerDefaults.decodeFailureHandlerWithAuthentication(handleMissingAuthorizationHeader(AuthChallenge.bearer(realm)))
  private val noChallenge = ServerDefaults.decodeFailureHandlerWithAuthentication(handleMissingAuthorizationHeader(AuthChallenge.none))

  private val result = m.unit(().asRight[Unit])

  private val wwwAuth = List(
    ("unauthorized with basic challenge", challengeBasic, Some("""Basic realm="access to the secret place"""")),
    ("unauthorized with bearer challenge", challengeBearer, Some("""Bearer realm="access to the secret place"""")),
    ("unauthorized without challenge", noChallenge, None)
  ).map { case (suffix, failureHandler, wwwAuth) =>
    testServer(authedEndpoint, suffix, Some(failureHandler))((_: String) => result) { baseUri =>
      basicRequest.get(baseUri).send(backend).map { r =>
        r.code shouldBe StatusCode.Unauthorized
        r.header("WWW-Authenticate") shouldBe wwwAuth
      }
    }
  }

  def tests(): List[Test] = wwwAuth

}
