// {cat=Security; effects=cats-effect; server=http4s; json=circe}: Login using OAuth2, authorization code flow

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.client3::async-http-client-backend-cats:3.10.1
//> using dep org.http4s::http4s-blaze-server:0.23.16
//> using dep com.github.jwt-scala::jwt-circe:10.0.1

package sttp.tapir.examples.security

import cats.effect.*
import cats.syntax.all.*
import io.circe.generic.auto.*
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import sttp.client3.*
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.time.Instant
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext

object OAuth2GithubHttp4sServer extends IOApp:

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  // github application details
  val clientId = "<put your client id here>"
  val clientSecret = "<put your client secret>"

  // algorithm used for jwt encoding and decoding
  val jwtAlgo = JwtAlgorithm.HS256
  val jwtKey = "my secret key"

  type Limit = Int
  type AuthToken = String

  case class AccessDetails(token: String)

  val authorizationUrl = "https://github.com/login/oauth/authorize"
  val accessTokenUrl = "https://github.com/login/oauth/access_token"

  val authOAuth2 = auth.oauth2.authorizationCodeFlow(authorizationUrl, accessTokenUrl)

  // endpoint declarations
  val login: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get
      .in("login")
      .out(statusCode(StatusCode.PermanentRedirect))
      .out(header[String]("Location"))

  val loginGithub: PublicEndpoint[String, String, AccessDetails, Any] =
    endpoint.get
      .in("login" / "oauth2" / "github")
      .in(query[String]("code"))
      .out(jsonBody[AccessDetails])
      .errorOut(stringBody)

  val secretPlace: Endpoint[String, Unit, String, String, Any] =
    endpoint.get
      .securityIn("secret-place")
      .securityIn(authOAuth2)
      .out(stringBody)
      .errorOut(stringBody)

  // converting endpoints to routes

  // simply redirect to github auth service
  val loginRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(login.serverLogic(_ => IO(s"$authorizationUrl?client_id=$clientId".asRight[Unit])))

  // after successful authorization github redirects you here
  def loginGithubRoute(backend: SttpBackend[IO, Any]): HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      loginGithub.serverLogic(code =>
        basicRequest
          .response(asStringAlways)
          .post(uri"$accessTokenUrl?client_id=$clientId&client_secret=$clientSecret&code=$code")
          .header("Accept", "application/json")
          .send(backend)
          .map(resp => {
            // create jwt token, that client will use for authenticating to the app
            val now = Instant.now
            val claim =
              JwtClaim(expiration = Some(now.plusSeconds(15 * 60).getEpochSecond), issuedAt = Some(now.getEpochSecond), content = resp.body)
            AccessDetails(JwtCirce.encode(claim, jwtKey, jwtAlgo)).asRight[String]
          })
      )
    )

  // try to decode the provided jwt
  def authenticate(token: String): Either[String, String] = {
    JwtCirce
      .decodeAll(token, jwtKey, Seq(jwtAlgo))
      .toEither
      .leftMap(err => "Invalid token: " + err)
      .map(decoded => decoded._2.content)
  }

  // get user details from decoded jwt
  val secretPlaceRoute: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
    secretPlace
      .serverSecurityLogic(token => IO(authenticate(token)))
      .serverLogic(authDetails => _ => IO(("Your details: " + authDetails).asRight[String]))
  )

  val httpClient = AsyncHttpClientCatsBackend.resource[IO]()

  override def run(args: List[String]): IO[ExitCode] =
    // starting the server
    httpClient
      .use(backend =>
        BlazeServerBuilder[IO]
          .withExecutionContext(ec)
          .bindHttp(8080, "localhost")
          .withHttpApp(Router("/" -> (secretPlaceRoute <+> loginRoute <+> loginGithubRoute(backend))).orNotFound)
          .resource
          .use { _ =>
            IO {
              println("Go to: http://localhost:8080")
              println("Press any key to exit ...")
              scala.io.StdIn.readLine()
            }
          }
      )
      .as(ExitCode.Success)
