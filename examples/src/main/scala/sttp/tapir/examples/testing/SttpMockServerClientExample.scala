// {cat=Testing; json=circe}: Test endpoints using the MockServer client

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.22
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.22
//> using dep com.softwaremill.sttp.tapir::sttp-mock-server:1.11.40
//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC4
//> using dep org.mock-server:mockserver-netty:5.15.0
//> using dep org.scalatest::scalatest:3.2.19

package sttp.tapir.examples.testing

import io.circe.generic.auto._
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import sttp.client4.*
import sttp.client4.wrappers.TryBackend
import sttp.tapir.DecodeResult.Value
import sttp.tapir.*
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.mockserver.*

import scala.util.Success
import sttp.client4.httpclient.HttpClientSyncBackend

class SttpMockServerClientExample extends AnyFlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach:
  behavior of "SttpMockServerClient"

  private val baseUri = uri"http://localhost:1080"

  private val backend = TryBackend(HttpClientSyncBackend())

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
