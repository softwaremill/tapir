package sttp.tapir.server.mockserver

import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import sttp.client3.TryHttpURLConnectionBackend
import sttp.model.StatusCode
import sttp.model.Uri.UriContext
import sttp.tapir.DecodeResult.Value
import sttp.tapir._
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.mockserver.fixture._

import java.util.UUID
import scala.util.Success

class SttpMockServerClientSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {
  behavior of "SttpMockServerClient"

  private val baseUri = uri"http://localhost:1080"

  private val backend = TryHttpURLConnectionBackend()

  private val mockServer = startClientAndServer(1080)

  private val mockServerClient = SttpMockServerClient(baseUri = baseUri, backend)

  override def afterEach(): Unit = mockServerClient.clear

  override def afterAll(): Unit = mockServer.stop()

  private val plainEndpoint = endpoint
    .in("api" / "v1" / "echo")
    .post
    .in(stringBody)
    .errorOut(stringBody)
    .out(stringBody)

  private val jsonEndpoint = endpoint
    .in("api" / "v1" / "person")
    .put
    .in(jsonBody[CreatePersonCommand])
    .errorOut(jsonBody[ApiError])
    .out(jsonBody[PersonView])

  private val orderEndpoint = endpoint
    .in("api" / "v1" / "order")
    .put
    .in(jsonBody[CreateOrderCommand])
    .errorOut(jsonBody[ApiError])
    .out(jsonBody[OrderCreatedEvent])

  it should "create plain text expectation correctly" in {
    val sampleIn = "Hello, world!"
    val sampleOut = "Hello to you!"

    val actual = for {
      _ <- mockServerClient
        .whenInputMatches(plainEndpoint)((), sampleIn)
        .thenSuccess(sampleOut)

      resp <- SttpClientInterpreter()
        .toRequest(plainEndpoint, baseUri = Some(baseUri))
        .apply(sampleIn)
        .send(backend)

      _ <- mockServerClient
        .verifyRequest(plainEndpoint, VerificationTimes.exactlyOnce)((), sampleIn)
    } yield resp.body

    actual shouldEqual Success(Value(Right(sampleOut)))
  }

  it should "create plain text error expectation correctly" in {
    val sampleIn = "Hello, world!"
    val sampleOut = "BOOOM!"

    val actual = for {
      _ <- mockServerClient
        .whenInputMatches(plainEndpoint)((), sampleIn)
        .thenError(sampleOut, statusCode = StatusCode.InternalServerError)

      resp <- SttpClientInterpreter()
        .toRequest(plainEndpoint, baseUri = Some(baseUri))
        .apply(sampleIn)
        .send(backend)

      _ <- mockServerClient
        .verifyRequest(plainEndpoint, VerificationTimes.exactlyOnce)((), sampleIn)
    } yield resp.body

    actual shouldEqual Success(Value(Left(sampleOut)))
  }

  it should "create json expectation correctly" in {
    val sampleIn = CreatePersonCommand("John", 23)
    val sampleOut = PersonView(uuid(), "John", 23)

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

  it should "create error json expectation correctly" in {
    val sampleIn = CreatePersonCommand(name = "John", age = -1)
    val sampleErrorOut = ApiError(code = 1, message = "Invalid age")

    val actual = for {
      _ <- mockServerClient
        .whenInputMatches(jsonEndpoint)((), sampleIn)
        .thenError(sampleErrorOut, statusCode = StatusCode.BadRequest)

      resp <- SttpClientInterpreter()
        .toRequest(jsonEndpoint, baseUri = Some(baseUri))
        .apply(sampleIn)
        .send(backend)

      _ <- mockServerClient
        .verifyRequest(jsonEndpoint, VerificationTimes.exactlyOnce)((), sampleIn)
    } yield resp.body

    actual shouldEqual Success(Value(Left(sampleErrorOut)))
  }

  it should "match json body with drop null disabled" in {
    import TapirJsonCirceWithDropNullDisabled.jsonPrinter

    val sampleIn = CreateOrderCommand(name = "John", total = None)
    val sampleErrorOut = ApiError(code = 1, message = "Invalid order")

    val actual = for {
      _ <- mockServerClient
        .whenInputMatches(orderEndpoint)((), sampleIn)
        .thenError(sampleErrorOut, statusCode = StatusCode.BadRequest)

      resp <- SttpClientInterpreter()
        .toRequest(orderEndpoint, baseUri = Some(baseUri))
        .apply(sampleIn)
        .send(backend)

      _ <- mockServerClient
        .verifyRequest(orderEndpoint, VerificationTimes.exactlyOnce)((), sampleIn)
    } yield resp.body

    actual shouldEqual Success(Value(Left(sampleErrorOut)))
  }

  it should "match json body with drop null enabled" in {
    import TapirJsonCirceWithDropNullEnabled.jsonPrinter

    val sampleIn = CreateOrderCommand(name = "John", total = None)
    val sampleErrorOut = ApiError(code = 1, message = "Invalid order")

    val actual = for {
      _ <- mockServerClient
        .whenInputMatches(orderEndpoint)((), sampleIn)
        .thenError(sampleErrorOut, statusCode = StatusCode.BadRequest)

      resp <- SttpClientInterpreter()
        .toRequest(orderEndpoint, baseUri = Some(baseUri))
        .apply(sampleIn)
        .send(backend)

      _ <- mockServerClient
        .verifyRequest(orderEndpoint, VerificationTimes.exactlyOnce)((), sampleIn)
    } yield resp.body

    actual shouldEqual Success(Value(Left(sampleErrorOut)))
  }

  private def uuid(): String = UUID.randomUUID().toString
}
