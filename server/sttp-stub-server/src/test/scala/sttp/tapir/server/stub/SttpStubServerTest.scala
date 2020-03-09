package sttp.tapir.server.stub

import cats.effect.IO
import org.scalatest.{FlatSpec, Matchers}
import sttp.tapir._
import sttp.tapir.json.circe._
import cats.syntax.either._
import io.circe.generic.auto._
import sttp.client.impl.cats.implicits._
import sttp.client._
import sttp.model.StatusCode
import sttp.tapir.client.sttp._
import sttp.tapir.server.{ServerDefaults, ServerEndpoint}

class SttpStubServerTest extends FlatSpec with Matchers {

  behavior of "SttpStubServer"

  it should "stub a simple endpoint with custom logic" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest")
      .get
      .out(jsonBody[ResponseWrapper])

    val endpointWithStubLogic: ServerEndpoint[Unit, Unit, ResponseWrapper, Nothing, IO] = endpoint.serverLogic { _ =>
      IO(ResponseWrapper(44.414).asRight[Unit])
    }

    implicit val backend: SttpBackend[IO, Nothing, NothingT] = List(endpointWithStubLogic).toBackendStub
    val req = endpoint.toSttpRequestUnsafe(uri"http://test.com").apply(())

    // when
    val response = req.send().unsafeRunSync()

    // then
    response shouldBe Response(ResponseWrapper(44.414), ServerDefaults.StatusCodes.success)
  }

  it should "stub an endpoint with error response" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest2")
      .get
      .out(jsonBody[ResponseWrapper])
      .errorOut(statusCode and jsonBody[TestError])

    val endpointWithStubLogic: ServerEndpoint[Unit, (StatusCode, TestError), ResponseWrapper, Nothing, IO] = endpoint.serverLogic { _ =>
      IO((StatusCode.BadRequest, ExactError("Aaargh!"): TestError).asLeft[ResponseWrapper])
    }

    implicit val backend = List(endpointWithStubLogic).toBackendStub
    val req = endpoint.toSttpRequestUnsafe(uri"http://test.com").apply(())

    // when
    val response = req.send().unsafeRunSync()

    // then
    response.body shouldBe ((StatusCode.BadRequest, ExactError("Aaargh!")))
  }
}

final case class ResponseWrapper(response: Double)

sealed trait TestError
case class ExactError(msg: String) extends TestError
