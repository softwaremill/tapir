// {cat=Error handling; effects=Future; server=Pekko HTTP}: Customising errors that are reported on decode failures (e.g. invalid or missing query parameter)

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.8
//> using dep org.apache.pekko::pekko-http:1.0.1
//> using dep org.apache.pekko::pekko-stream:1.0.3
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples.errors

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.pekkohttp.{PekkoHttpServerInterpreter, PekkoHttpServerOptions}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import sttp.client3.*
import sttp.monad.FutureMonad
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}

@main def customErrorsOnDecodeFailurePekkoServer(): Unit =
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  implicit val m: FutureMonad = new FutureMonad()

  // corresponds to: GET /?amount=...
  val amountEndpoint: PublicEndpoint[Int, String, Unit, Any] = endpoint.get.in(query[Int]("amount")).errorOut(stringBody)

  // by default, decoding errors will be returned as a 400 response with body e.g. "Invalid value for: query parameter amount"
  // the defaults are defined in ServerDefaults
  // this can be customised by setting the appropriate option in the server options, passed to ServerInterpreter
  val customServerOptions: PekkoHttpServerOptions = PekkoHttpServerOptions.customiseInterceptors
    .decodeFailureHandler(
      DecodeFailureHandler(ctx => {
        ctx.failingInput match {
          // when defining how a decode failure should be handled, we need to describe the output to be used, and
          // a value for this output
          case EndpointInput.Query(_, _, _, _) =>
            Future.successful(Some(server.model.ValuedEndpointOutput(stringBody, "Incorrect format!!!")))
          // in other cases, using the default behavior
          case _ => DefaultDecodeFailureHandler[Future](ctx)
        }
      })
    )
    .options

  val amountRoute: Route =
    PekkoHttpServerInterpreter(customServerOptions).toRoute(amountEndpoint.serverLogicSuccess(_ => Future.successful(())))

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(amountRoute).map { binding =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    // correct request, parameter parses as an int, no errors
    val result1: Either[String, String] = basicRequest.get(uri"http://localhost:8080/?amount=10").send(backend).body
    println("Got result: " + result1)
    assert(result1 == Right(""))

    // incorrect request, parameter does not parse, error
    val result2: Either[String, String] = basicRequest.get(uri"http://localhost:8080/?amount=xyz").send(backend).body
    println("Got result: " + result2)
    assert(result2 == Right("Incorrect format!!!"))

    binding
  }

  val _ = Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)
