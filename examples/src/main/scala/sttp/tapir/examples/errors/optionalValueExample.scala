// {cat=Error handling; effects=Future; server=Pekko HTTP; JSON=circe}: Optional returned from the server logic, resulting in 404 if None

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.45
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.45
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.45
//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC3

package sttp.tapir.examples.errors

import io.circe.generic.auto.*
import io.circe.parser.parse
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*

@main def optionalValueExample(): Unit =

  case class Beer(name: String, volumeInLiters: Double)

  val bartenderEndpoint = endpoint.get
    .in("beer" / query[Int]("age"))
    // Optional value from serverLogic, responding with 404 "Not Found" when logic returns None
    .out(
      oneOf(
        oneOfVariantExactMatcher(StatusCode.NotFound, jsonBody[Option[Beer]])(None),
        oneOfVariantValueMatcher(StatusCode.Ok, jsonBody[Option[Beer]]) { case Some(_) =>
          true
        }
      )
    )

  //

  val bartenderServerEndpoint = bartenderEndpoint.serverLogic {
    case a if a < 18 => Future.successful(Right(None))
    case _           => Future.successful(Right(Some(Beer("IPA", 0.5))))
  }

  given actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher
  val routes = PekkoHttpServerInterpreter().toRoute(bartenderServerEndpoint)

  val serverBinding = Http().newServerAt("localhost", 8080).bindFlow(routes).map { binding =>
    val backend: SyncBackend = HttpClientSyncBackend()

    val response1 = basicRequest.get(uri"http://localhost:8080/beer?age=15").send(backend)
    assert(response1.code == StatusCode.NotFound)

    val response2 = basicRequest.get(uri"http://localhost:8080/beer?age=21").send(backend)
    println("Got result: " + response2.body)
    val beerEither = response2.body.flatMap(parse).flatMap(_.as[Beer])
    assert(beerEither == Right(Beer("IPA", 0.5)))

    binding
  }

  val _ = Await.result(serverBinding, 1.minute)
