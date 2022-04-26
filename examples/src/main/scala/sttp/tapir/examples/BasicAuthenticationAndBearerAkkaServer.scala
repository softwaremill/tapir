package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import sttp.client3._
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.model.{Method, StatusCode}
import sttp.tapir._
import sttp.tapir.model._
import sttp.tapir.server.akkahttp._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object BasicAuthenticationAndBearerAkkaServer extends App with RouteConcatenation {
  implicit val ex: ExecutionContext = ExecutionContext.global

  private val baseEndpointDefinition = endpoint
    .securityIn("secret" / "user")
    .securityIn(path[String]("userId"))
    .securityIn(auth.bearer[Option[String]]())
    .securityIn(auth.basic[Option[UsernamePassword]](WWWAuthenticateChallenge.basic("example")))
    .serverSecurityLogic(credentials => authorize(credentials))

  println(baseEndpointDefinition)

  val secret = baseEndpointDefinition
    .out(stringBody)
    .method(Method.GET)
    .serverLogic(username => param => Future.successful(Right(s"Hello, $username!")))

  private def authorize(credentials: (String, Option[String], Option[UsernamePassword])): Future[Either[Unit, String]] = {
    Future.successful(Right(credentials._3.map(_.username).getOrElse(credentials._2.getOrElse("noauth")) + " (Id: " + credentials._1 + ")"))
  }

  val secretRoute: Route = concat(AkkaHttpServerInterpreter().toRoute(secret))

  implicit val actorSystem: ActorSystem = ActorSystem()

  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(secretRoute).map { _ =>
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    val result = basicRequest.get(uri"http://localhost:8080/secret/user/1").header("Authorization", "Basic dXNlcjpzZWNyZXQ=").send(backend)
    val result2 = basicRequest.get(uri"http://localhost:8080/secret/user/2").header("Authorization", "Bearer token").send(backend)

    println("Got result: " + result)
    assert(result.code == StatusCode.Ok)
    assert(result.body == Right("Hello, user (Id: 1)!"))

    println("Got result: " + result2)
    assert(result2.code == StatusCode.Ok)
    assert(result2.body == Right("Hello, token (Id: 2)!"))

  }

  Await.result(bindAndCheck.transformWith(r => actorSystem.terminate().transform(_ => r)), 1.minute)

}
