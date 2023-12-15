package sttp.tapir.examples2.openapi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import sttp.tapir._
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, Promise}

/** Preliminary steps (!!! DO NOT USE ON PRODUCTION :) !!!):
  *   1. Start keycloak
  *      {{{docker run -p 8080:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:17.0.1 start-dev}}}
  *
  * 2. Based on page: [[https://www.keycloak.org/getting-started/getting-started-docker]]
  *
  *   - create realm `myrealm`
  *   - create client `myclient` with:
  *     - `Access Type` == confidential => after that tab with `Credentials` should be visible with Secret
  *     - `Valid Redirect URIs` == *
  *     - `Web Origins` == *
  *   - create user 'myuser' and add password which is permanent not temporary
  *
  * 3. Check if you can connect by using [[https://www.keycloak.org/app/]] ---
  *
  * Go to: [[http://localhost:3333/docs]] And try authorize by using `Authorize` by providing details of clients and user
  */
object SwaggerUIOAuth2AkkaServer extends App with RouteConcatenation {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  def authLogic(token: String): Future[Either[Int, String]] = Future.successful(Right(token))

  val secureEndpoint: PartialServerEndpoint[String, String, Unit, Int, Unit, Any, Future] =
    endpoint
      .securityIn(
        auth.oauth2.authorizationCode(
          authorizationUrl = Some("http://localhost:8080/realms/myrealm/protocol/openid-connect/auth"),
          tokenUrl = Some("http://localhost:8080/realms/myrealm/protocol/openid-connect/token")
        )
      )
      .errorOut(plainBody[Int])
      .serverSecurityLogic(authLogic)

  def countCharacters(s: String): Future[Either[Int, Int]] =
    Future.successful(Right[Int, Int](s.length))

  val countCharactersEndpoint =
    secureEndpoint
      .in("length")
      .in(query[String]("word"))
      .out(plainBody[Int])
      .serverLogic(_ => word => countCharacters(word))

  val countCharactersRoute: Route =
    AkkaHttpServerInterpreter().toRoute(countCharactersEndpoint)

  val endpoints: List[AnyEndpoint] = List(countCharactersEndpoint).map(_.endpoint)

  val swaggerEndpoints = SwaggerInterpreter()
    .fromEndpoints[Future](endpoints, "My App", "1.0")

  val swaggerRoute: Route = AkkaHttpServerInterpreter().toRoute(swaggerEndpoints)

  val routes = countCharactersRoute ~ swaggerRoute

  val binding: Future[Http.ServerBinding] = Http().newServerAt("localhost", 3333).bindFlow(routes)

  val promise = Promise[Unit]()
  Await.result(promise.future, 100.minutes)
}
