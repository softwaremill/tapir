package sttp.tapir.examples

import io.circe.generic.auto._
import sttp.client3.{TryHttpURLConnectionBackend, UriContext}
import sttp.tapir._
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.mockserver._

/** @note
  *   - run the following docker command to setup mock-server locally
  * {{{
  *   docker run -d --name tapir-mock-server --rm -p 1080:1080 mockserver/mockserver
  * }}}
  */
object MockServerExample extends App {
  val backend = TryHttpURLConnectionBackend()
  val mockServerClient = SttpMockServerClient(baseUri = uri"http://localhost:1080", backend)

  case class SampleIn(name: String, age: Int)

  case class SampleOut(greeting: String)

  private def testEndpoint[I, E, O](e: PublicEndpoint[I, E, O, Any])(in: I, out: O): Unit = {
    def sep(s: String): Unit = println(s * 20)

    sep("")
    println(s"Testing ${e.showDetail}")
    sep("")

    val expectation = mockServerClient
      .whenInputMatches(e)((), in)
      .thenSuccess(out)
      .get

    println(s"Got expectation $expectation")
    sep("")

    val result = SttpClientInterpreter()
      .toRequest(e, baseUri = Some(uri"http://localhost:1080"))
      .apply(in)
      .send(backend)
      .get

    println(s"Got result $result")
    sep("")

    val verifyResult = mockServerClient.verifyRequest(e, times = VerificationTimes.atLeastOnce)((), in).get

    println(s"Got verification result: $verifyResult")
    sep("-")
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

  testEndpoint(samplePlainEndpoint)(
    in = "Hello, world!",
    out = "Hello to you!"
  )

  testEndpoint(sampleJsonEndpoint)(
    in = "request-id-123" -> SampleIn("John", 23),
    out = SampleOut("Hello, John!")
  )

  val clearResult = mockServerClient.clear.get

  println(s"Got clearing result: $clearResult")
}
