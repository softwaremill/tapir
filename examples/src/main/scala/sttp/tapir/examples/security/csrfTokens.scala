// {cat=Security; effects=Future; server=Netty}: Securing endpoint with CSRF tokens example

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.33
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.33
//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC3

package sttp.tapir.examples.security

import ox.{supervised, useInScope}
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.{HeaderNames, StatusCode}
import sttp.model.headers.{CookieValueWithMeta, WWWAuthenticateChallenge}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.model.*
import sttp.tapir.generic.auto.*
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.netty.sync.NettySyncServer

import java.util.UUID
import scala.util.matching.Regex

case class User(name: String)

case class LoggedInUser(user: User, csrfToken: UUID)

// Simple in-memory users database. Do not keep passwords in clear text in real projects!
object Users:
  private val usersToPassword = Map(
    User("adam") -> "Scala Rulez!",
    User("pawel") -> "Long live tAPIr!",
    User("tomek") -> "Developers, developers, developers!"
  )

  def checkPassword(user: User, password: String): Boolean =
    usersToPassword.get(user).contains(password)

object SessionManager:
  private var sessions = Map.empty[UUID, LoggedInUser]

  def createSession(user: User): UUID =
    val sessionId = UUID.randomUUID()
    val csrfToken = UUID.randomUUID()
    sessions = sessions + (sessionId -> LoggedInUser(user, csrfToken))
    sessionId

  def getLoggedInUser(sessionId: UUID): Option[LoggedInUser] = sessions.get(sessionId)

@main def csrfTokens(): Unit =
  val SessionCookie = "SESSIONID"

  val loginEndpoint: Full[UsernamePassword, User, Unit, Unit, (String, Option[CookieValueWithMeta]), Any, Identity] =
    endpoint.get
      .securityIn("login")
      .securityIn(auth.basic[UsernamePassword]())
      .errorOut(statusCode(StatusCode.Unauthorized))
      .out(stringBody)
      .out(setCookieOpt(SessionCookie))
      .serverSecurityLogic[User, Identity] {
        case UsernamePassword(username, Some(pass)) if Users.checkPassword(User(username), pass) => Right(User(username))
        case _                                                                                   => Left(())
      }
      .serverLogic(user =>
        _ =>
          val sessionId = SessionManager.createSession(user)
          Right((s"Hello, ${user.name}!", CookieValueWithMeta.safeApply(sessionId.toString).toOption))
      )

  val secureEndpoint: PartialServerEndpoint[String, LoggedInUser, Unit, Unit, Unit, Any, Identity] =
    endpoint
      .securityIn(auth.apiKey(cookie[String](SessionCookie), WWWAuthenticateChallenge("cookie")))
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogic { cookie =>
        SessionManager.getLoggedInUser(UUID.fromString(cookie)) match {
          case Some(user) => Right(user)
          case None       => Left(())
        }
      }

  val changePasswordFormEndpoint: Full[String, LoggedInUser, Unit, Unit, String, Any, Identity] =
    secureEndpoint.get
      .in("changePasswordForm")
      .out(stringBody)
      .serverLogic(protectedUser => _ => Right(changePasswordFormHtml(protectedUser.csrfToken)))

  case class ChangePasswordForm(csrfToken: String, newPassword: String)

  val changePasswordEndpoint: Full[String, LoggedInUser, ChangePasswordForm, Unit, String, Any, Identity] =
    secureEndpoint.post
      .in(formBody[ChangePasswordForm])
      .out(stringBody)
      .serverLogic { protectedUser => changePasswordForm =>
        if changePasswordForm.csrfToken == protectedUser.csrfToken.toString then Right("Password changed!")
        else Left(())
      }

  supervised {
    // starting the server
    val binding = useInScope(
      NettySyncServer()
        .addEndpoint(loginEndpoint)
        .addEndpoint(changePasswordFormEndpoint)
        .addEndpoint(changePasswordEndpoint)
        .start()
    )(_.stop())
    println(s"Server started on http://${binding.hostName}:${binding.port}/hello?name=...!")

    // testing
    val backend: SyncBackend = HttpClientSyncBackend()

    // login to create session
    val loginResponse = basicRequest
      .get(uri"http://localhost:8080/login")
      .header(HeaderNames.Authorization, "Basic cGF3ZWw6TG9uZyBsaXZlIHRBUElyIQ==")
      .send(backend)
    assert(loginResponse.code == StatusCode.Ok)

    val sessionId = loginResponse.cookies.collectFirst { case Right(cookie) if cookie.name == SessionCookie => cookie.value }.get
    println("Successfully logged in. Got session ID: " + sessionId)

    // open page with action that attacker wants to perform. Notice the CSRF token.
    val changePasswordPage = basicRequest
      .get(uri"http://localhost:8080/changePasswordForm")
      .cookie(SessionCookie, sessionId)
      .response(asStringAlways)
      .send(backend)
    val changePasswordFormBody = changePasswordPage.body
    println("Got change password form. Notice the CSRF token: " + changePasswordFormBody)
    val regex: Regex = """<input type="hidden" name="csrfToken" value="(.*)">""".r
    val maybeMatch = regex.findFirstMatchIn(changePasswordFormBody)
    val csrfTokenValue = maybeMatch match
      case Some(csrfTokenValue) => csrfTokenValue.group(1)
      case None                 => assert(false, "No CSRF token found in form body")

    // do the action with wrong session ID
    println("Trying to perform action with wrong session ID")
    val changePasswordWrongSessionId = basicRequest
      .post(uri"http://localhost:8080/changePassword")
      .cookie(SessionCookie, UUID.randomUUID().toString)
      .body(s"""newPassword="MySecretPassword"&csrfToken="$csrfTokenValue"""")
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
      .body(s"newPassword=MySecretPassword&csrfToken=$csrfTokenValue")
      .send(backend)
    assert(changePassword.code == StatusCode.Ok)
    println("Success!")

    ()
  }

def changePasswordFormHtml(csrfToken: UUID) =
  s"""
    |<form action="/changePassword" method="post">
    |  <input type="text" name="newPassword">
    |  <input type="hidden" name="csrfToken" value="$csrfToken">
    |  <input type="submit" value="Submit">
    |</form>
    |""".stripMargin
