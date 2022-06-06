package sttp.tapir.examples

import io.circe.generic.auto._
import org.mockserver.integration.ClientAndServer.startClientAndServer
import sttp.client3.{TryHttpURLConnectionBackend, UriContext}
import sttp.model.StatusCode
import sttp.tapir.DecodeResult.Value
import sttp.tapir._
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.mockserver._

import scala.util.Success

object SttpMockServerClientExample extends App {
  val backend = TryHttpURLConnectionBackend()
  val mockServer = startClientAndServer(1080)
  val mockServerClient = SttpMockServerClient(baseUri = uri"http://localhost:1080", backend)

  case class SampleIn(name: String, age: Int)

  case class SampleOut(greeting: String)

  private def testEndpoint[I, E, O](e: PublicEndpoint[I, E, O, Any])(in: I, out: O): Unit = {
    val resp = for {
      _ <- mockServerClient
        .whenInputMatches(e)((), in)
        .thenSuccess(out)

      resp <- SttpClientInterpreter()
        .toRequest(e, baseUri = Some(uri"http://localhost:1080"))
        .apply(in)
        .send(backend)

      _ <- mockServerClient
        .verifyRequest(e, VerificationTimes.exactlyOnce)((), in)
    } yield resp.body

    assert(resp == Success(Value(Right(out))))
  }

  val samplePlainEndpoint = endpoint.post
    .in("api" / "v1" / "plain")
    .in(stringBody)
    .out(stringBody)

  val sampleJsonEndpoint = endpoint.post
    .in("api" / "v1" / "json")
    .in(header[String]("X-RequestId"))
    .in(jsonBody[SampleIn])
    .errorOut(stringBody)
    .out(jsonBody[SampleOut])

  try {
    testEndpoint(samplePlainEndpoint)(
      in = "Hello, world!",
      out = "Hello to you!"
    )

    testEndpoint(sampleJsonEndpoint)(
      in = "request-id-123" -> SampleIn("John", 23),
      out = SampleOut("Hello, John!")
    )
  } finally {
    mockServerClient.clear
    mockServer.stop()
  }
}
