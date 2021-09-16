package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import sttp.tapir._
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import sttp.client3._
import sttp.tapir.server.interceptor.ValuedEndpointOutput
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler

object CustomErrorsOnDecodeFailureAkkaServer extends App {
  // corresponds to: GET /?amount=...
  val amountEndpoint: Endpoint[Int, String, Unit, Any] = endpoint.get.in(query[Int]("amount")).errorOut(stringBody)

  // by default, decoding errors will be returned as a 400 response with body e.g. "Invalid value for: query parameter amount"
  // the defaults are defined in ServerDefaults
  // this can be customised by setting the appropriate option in the server options, passed implicitly to toRoute
  implicit val customServerOptions: AkkaHttpServerOptions = AkkaHttpServerOptions.customInterceptors
    .decodeFailureHandler(ctx => {
      ctx.failingInput match {
        // when defining how a decode failure should be handled, we need to describe the output to be used, and
        // a value for this output
        case EndpointInput.Query(_, _, _) => Some(ValuedEndpointOutput(stringBody, "Incorrect format!!!"))
        // in other cases, using the default behavior
        case _ => DefaultDecodeFailureHandler.handler(ctx)
      }
    })
    .options

  val amountRoute: Route = AkkaHttpServerInterpreter().toRoute(amountEndpoint)(_ => Future.successful(Right(())))

  // starting the server
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(amountRoute).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    // correct request, parameter parses as an int, no errors
    val result1: Either[String, String] = basicRequest.get(uri"http://localhost:8080/?amount=10").send(backend).body
    println("Got result: " + result1)
    assert(result1 == Right(""))

    // incorrect request, parameter does not parse, error
    val result2: Either[String, String] = basicRequest.get(uri"http://localhost:8080/?amount=xyz").send(backend).body
    println("Got result: " + result2)
    assert(result2 == Left("Incorrect format!!!"))
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
