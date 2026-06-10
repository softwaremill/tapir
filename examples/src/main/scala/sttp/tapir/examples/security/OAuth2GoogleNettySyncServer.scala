// {cat=Security; effects=Direct; server=Netty}: Login using OAuth2 with Google, authorization code flow
//
// This example shows three endpoints:
// 1. /login - redirects browser to Google's OAuth page
// 2. /login/oauth2/google - callback that exchanges OAuth code for a JWT token (returns JSON)
// 3. /secret-place - protected endpoint that validates the JWT from step 2
//
// After visiting /login in browser and completing OAuth, copy the returned token and use:
// curl http://localhost:8080/secret-place -H "Authorization: Bearer <token>"

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.13.2
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.13.2
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.13.2
//> using dep com.softwaremill.sttp.client4::core:4.0.13
//> using dep com.github.jwt-scala::jwt-circe:10.0.1
//> using dep ch.qos.logback:logback-classic:1.5.8

package sttp.tapir.examples.security

import io.circe.generic.auto.*
import io.circe.parser.*
import ox.{supervised, useInScope}
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.netty.sync.NettySyncServer

import java.time.Instant

@main def oAuth2GoogleNettySyncServer(): Unit =
  // Replace with your Google OAuth credentials from https://console.cloud.google.com/apis/credentials
  val clientId = "<put your client id here>"
  val clientSecret = "<put your client secret>"
  val callbackUrl = "http://localhost:8080/login/oauth2/google"

  // algorithm used for jwt encoding and decoding
  val jwtAlgo = JwtAlgorithm.HS256
  val jwtKey = "my secret key"

  case class AccessDetails(token: String)

  val authorizationUrl = "https://accounts.google.com/o/oauth2/v2/auth"
  val accessTokenUrl = "https://oauth2.googleapis.com/token"

  val authOAuth2 = auth.oauth2.authorizationCodeFlow(authorizationUrl, accessTokenUrl)

  // endpoint declarations
  val login: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get
      .in("login")
      .out(statusCode(StatusCode.PermanentRedirect))
      .out(header[String]("Location"))

  val loginGoogle: PublicEndpoint[String, Unit, AccessDetails, Any] =
    endpoint.get
      .in("login" / "oauth2" / "google")
      .in(query[String]("code"))
      .out(jsonBody[AccessDetails])

  val secretPlace: Endpoint[String, Unit, String, String, Any] =
    endpoint.get
      .securityIn("secret-place")
      .securityIn(authOAuth2)
      .out(stringBody)
      .errorOut(stringBody)

  // converting endpoints to server endpoints with logic

  // simply redirect to Google auth service
  def loginServerEndpoint(backend: SyncBackend) = login.handleSuccess(_ =>
    s"$authorizationUrl?client_id=$clientId&response_type=code&redirect_uri=$callbackUrl&scope=openid profile email"
  )

  // after successful authorization Google redirects you here
  def loginGoogleServerEndpoint(backend: SyncBackend) = loginGoogle.handleSuccess(code =>
    val response = basicRequest
      .response(asStringAlways)
      .post(uri"$accessTokenUrl")
      .header("Content-Type", "application/x-www-form-urlencoded")
      .body(
        Map(
          "grant_type" -> "authorization_code",
          "client_id" -> clientId,
          "client_secret" -> clientSecret,
          "code" -> code,
          "redirect_uri" -> callbackUrl
        )
      )
      .send(backend)

    // create jwt token, that client will use for authenticating to the app
    val now = Instant.now
    val claim = JwtClaim(
      expiration = Some(now.plusSeconds(15 * 60).getEpochSecond),
      issuedAt = Some(now.getEpochSecond),
      content = response.body
    )
    AccessDetails(JwtCirce.encode(claim, jwtKey, jwtAlgo))
  )

  // try to decode the provided jwt
  def authenticate(token: String): Either[String, String] =
    JwtCirce
      .decodeAll(token, jwtKey, Seq(jwtAlgo))
      .toEither match
      case Left(err)      => Left("Invalid token: " + err)
      case Right(decoded) => Right(decoded._2.content)

  // get user details from decoded jwt
  val secretPlaceServerEndpoint = secretPlace
    .handleSecurity(authenticate)
    .handle(authDetails => _ => Right(s"Your details: $authDetails"))

  supervised:
    val backend = useInScope(HttpClientSyncBackend())(_.close())

    val binding = useInScope(
      NettySyncServer()
        .host("localhost")
        .port(8080)
        .addEndpoints(List(loginServerEndpoint(backend), loginGoogleServerEndpoint(backend), secretPlaceServerEndpoint))
        .start()
    )(_.stop())

    println(s"Go to: http://${binding.hostName}:${binding.port}/login")
    println("Press ENTER to exit ...")
    scala.io.StdIn.readLine()
