// {cat=Security; effects=Future; server=Pekko HTTP}: Securing endpoint with CSRF tokens example

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.12
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.12
//> using dep com.softwaremill.sttp.client3::core:3.10.2

package sttp.tapir.examples.security

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.client3.*
import sttp.model.{HeaderNames, StatusCode}
import sttp.model.headers.{Cookie, CookieValueWithMeta, WWWAuthenticateChallenge}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.model.*
import sttp.tapir.generic.auto.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.pekkohttp.*

import java.util.UUID
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.util.matching.Regex

case class User(name: String)

case class ProtectedUser(user: User, csrfToken: UUID)

// Simple in-memory users database. Do not keep passwords in clear text in real projects!
object Users {
  private val usersToPassword = Map(
    User("adam") -> "Scala Rulez!",
    User("pawel") -> "Long live tAPIr!",
    User("tomek") -> "Developers, developers, developers!"
  )

  def checkPassword(user: User, password: String): Boolean = {
    usersToPassword.get(user).contains(password)
  }
}

object SessionManager {
  private var sessions = Map.empty[UUID, ProtectedUser]

  def createSession(user: User): UUID = {
    val sessionId = UUID.randomUUID()
    val crsfToken = UUID.randomUUID()
    sessions = sessions + (sessionId -> ProtectedUser(user, crsfToken))
    sessionId
  }

  def getLoggedInUser(sessionId: UUID): Option[ProtectedUser] = sessions.get(sessionId)
}

@main def csrfTokens(): Unit =
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  val SessionCookie = "SESSIONID"

  val loginEndpoint: Full[UsernamePassword, User, Unit, Unit, (String, Option[CookieValueWithMeta]), Any, Future] =
    endpoint
      .get
      .securityIn("login")
      .securityIn(auth.basic[UsernamePassword]())
      .errorOut(statusCode(StatusCode.Unauthorized))
      .out(stringBody)
      .out(setCookieOpt(SessionCookie))
      .serverSecurityLogic {
        case UsernamePassword(username, Some(pass)) if Users.checkPassword(User(username), pass) =>
          Future.successful(Right(User(username)))
        case _ => Future.successful(Left(()))
      }
      .serverLogic(user => _ =>
        val sessionId = SessionManager.createSession(user)
        Future.successful(Right((s"Hello, ${user.name}!", CookieValueWithMeta.safeApply(sessionId.toString).toOption)))
      )

  val secureEndpoint =
    endpoint
      .securityIn(auth.apiKey(cookie[String](SessionCookie), WWWAuthenticateChallenge("cookie")))
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogic { cookie =>
        SessionManager.getLoggedInUser(UUID.fromString(cookie)) match {
          case Some(user) => Future.successful(Right(user))
          case None       => Future.successful(Left(()))
        }
      }

  val changePasswordFormEndpoint =
    secureEndpoint
      .get
      .in("changePasswordForm")
      .out(stringBody)
      .serverLogic(protectedUser => _ => Future.successful(Right(changePasswordFormHtml(protectedUser.csrfToken))))

  case class ChangePasswordForm(csrfToken: String, newPassword: String)

  val changePasswordEndpoint =
    secureEndpoint
      .post
      .in(formBody[ChangePasswordForm])
      .out(stringBody)
      .serverLogic { protectedUser =>
        changePasswordForm =>
          if changePasswordForm.csrfToken == protectedUser.csrfToken.toString then
            Future.successful(Right("Password changed!"))
          else
            Future.successful(Left(()))
      }

  val routes: Route =
    PekkoHttpServerInterpreter().toRoute(List(
      loginEndpoint,
      changePasswordFormEndpoint,
      changePasswordEndpoint
    ))

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(routes).map { binding =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    // login to create session
    val loginResponse = basicRequest.get(uri"http://localhost:8080/login").header(HeaderNames.Authorization, "Basic cGF3ZWw6TG9uZyBsaXZlIHRBUElyIQ==").send(backend)
    assert(loginResponse.code == StatusCode.Ok)

    val sessionId = loginResponse.cookies.collectFirst { case Right(cookie) if cookie.name == SessionCookie => cookie.value }.get
    println("Successfully logged in. Got session ID: " + sessionId)

    // open page with action that attacker wants to perform. Notice the CSRF token.
    val changePasswordPage = basicRequest.get(uri"http://localhost:8080/changePasswordForm").cookie(SessionCookie, sessionId).send(backend)
    val changePasswordFormBody = changePasswordPage.body.getOrElse(throw new RuntimeException("Body is Left"))
    println("Got change password form. Notice the CSRF token: " + changePasswordFormBody)
    val regex: Regex = """<input type="hidden" name="csrfToken" value="(.*)">""".r
    val maybeMatch = regex.findFirstMatchIn(changePasswordFormBody)
    val crsfTokenValue = maybeMatch match
      case Some(crsfTokenValue) => crsfTokenValue.group(1)
      case None => assert(false, "No CSRF token found in form body")

    // do the action with wrong session ID
    println("Trying to perform action with wrong session ID")
    val changePasswordWrongSessionId = basicRequest.post(uri"http://localhost:8080/changePassword")
      .cookie(SessionCookie, UUID.randomUUID().toString)
      .body(s"""newPassword="MySecretPassword"&csrfToken="$crsfTokenValue"""")
      .send(backend)
    assert(changePasswordWrongSessionId.code == StatusCode.Unauthorized)
    println("Failed as expected")

    // do the action with wrong CSRF token
    println("Trying to perform action with wrong CSRF token")
    val changePasswordWrongToken =
      basicRequest
        .post(uri"http://localhost:8080/changePassword")
        .cookie(SessionCookie, sessionId)
        .body(s"""newPassword="MySecretPassword"&csrfToken="${UUID.randomUUID().toString}"""")
        .send(backend)
    assert(changePasswordWrongToken.code == StatusCode.Unauthorized)
    println("Failed as expected")

    // do action with proper token
    println("Trying to perform action with good session ID and CSRF token")
    val changePassword = basicRequest
      .post(uri"http://localhost:8080/changePassword")
      .cookie(SessionCookie, sessionId)
      .body(s"newPassword=MySecretPassword&csrfToken=$crsfTokenValue")
      .send(backend)
    assert(changePassword.code == StatusCode.Ok)
    println("Success!")

    binding
  }

  val _ = Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)

def changePasswordFormHtml(csrfToken: UUID) =
  s"""
    |<form action="/changePassword" method="post">
    |  <input type="text" name="newPassword">
    |  <input type="hidden" name="csrfToken" value="$csrfToken">
    |  <input type="submit" value="Submit">
    |</form>
    |""".stripMargin