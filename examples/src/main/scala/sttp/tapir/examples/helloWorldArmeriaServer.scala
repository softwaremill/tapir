// {cat=Hello, World!; effects=Future; server=Armeria}: Exposing an endpoint using the Armeria server

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-armeria-server:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples

import com.linecorp.armeria.server.Server
import sttp.capabilities.armeria.ArmeriaStreams
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend, UriContext, asStringAlways, basicRequest}
import sttp.tapir.server.armeria.{ArmeriaFutureServerInterpreter, TapirService}
import sttp.tapir.*

import scala.concurrent.Future

@main def helloWorldArmeriaServer(): Unit =

  // the endpoint: single fixed path input ("hello"), single query parameter
  // corresponds to: GET /hello?name=...
  val helloWorld: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // converting an endpoint to a TapirService (providing server-side logic); extension method comes from imported packages
  val helloWorldService: TapirService[ArmeriaStreams, Future] =
    ArmeriaFutureServerInterpreter().toService(helloWorld.serverLogicSuccess(name => Future.successful(s"Hello, $name!")))

  // starting the server
  val server: Server = Server
    .builder()
    .http(8080)
    .service(helloWorldService)
    .build()

  server.start().join()
  // testing
  val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  val result: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/hello?name=Frodo").send(backend).body
  println("Got result: " + result)

  assert(result == "Hello, Frodo!")
  server.stop().join()
