package sttp.tapir.examples.testing

import io.circe.generic.auto._
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import sttp.client3.{TryHttpURLConnectionBackend, UriContext}
import sttp.tapir.DecodeResult.Value
import sttp.tapir._
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.mockserver._

import scala.util.Success

class SttpMockServerClientExample extends AnyFlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {
  behavior of "SttpMockServerClient"

  private val baseUri = uri"http://localhost:1080"

  private val backend = TryHttpURLConnectionBackend()

  private val mockServer = startClientAndServer(1080)

  private val mockServerClient = SttpMockServerClient(baseUri = baseUri, backend)

  override def afterEach(): Unit = mockServerClient.clear

  override def afterAll(): Unit = mockServer.stop()

  case class SampleIn(name: String, age: Int)
  case class SampleOut(greeting: String)

  private val jsonEndpoint = endpoint
    .in("api" / "v1" / "person")
    .put
    .in(jsonBody[SampleIn])
    .out(jsonBody[SampleOut])

  it should "test json endpoint" in {
    val sampleIn = SampleIn("John", 23)
    val sampleOut = SampleOut("Hello, John!")

    val actual = for {
      _ <- mockServerClient
        .whenInputMatches(jsonEndpoint)((), sampleIn)
        .thenSuccess(sampleOut)

      resp <- SttpClientInterpreter()
        .toRequest(jsonEndpoint, baseUri = Some(baseUri))
        .apply(sampleIn)
        .send(backend)

      _ <- mockServerClient
        .verifyRequest(jsonEndpoint, VerificationTimes.exactlyOnce)((), sampleIn)
    } yield resp.body

    actual shouldEqual Success(Value(Right(sampleOut)))
  }
}
