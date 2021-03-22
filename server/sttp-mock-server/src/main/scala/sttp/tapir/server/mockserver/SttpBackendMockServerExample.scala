package sttp.tapir.server.mockserver

import sttp.tapir._
import sttp.client3.TryHttpURLConnectionBackend
import sttp.client3.UriContext
import sttp.model.StatusCode
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._

object SttpBackendMockServerExample extends App {
  val backend = TryHttpURLConnectionBackend()

  case class SampleIn(name: String, age: Int)

  case class SampleOut(greeting: String)

  val sampleEndpoint = endpoint
    .post
    .in("api" / "v1" / "sample")
    .in(header[String]("X-RequestId"))
    .in(jsonBody[SampleIn])
    .errorOut(stringBody)
    .out(jsonBody[SampleOut])

  val mockServerClient = new SttpMockServerClient(baseUri = uri"http://localhost:1080")(backend)

  val sampleIn = "request-id-123" -> SampleIn("John", 23)
  val sampleOut = SampleOut("Hello, John!")

  val expectation = mockServerClient.createExpectation(
    sampleEndpoint
  )(
    sampleIn,
    Right(sampleOut),
    StatusCode.Ok
  )

  println(s"Got expectation $expectation")

  val result = SttpClientInterpreter
    .toRequest(sampleEndpoint, baseUri = Some(uri"http://localhost:1080"))
    .apply(sampleIn)
    .send(backend)

  println(s"Got result $result")
}
