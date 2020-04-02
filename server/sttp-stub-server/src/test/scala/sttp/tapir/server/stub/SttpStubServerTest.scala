package sttp.tapir.server.stub

import io.circe.generic.auto._
import org.scalatest.{FlatSpec, Matchers}
import sttp.client._
import sttp.client.monad._
import sttp.client.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe._

class SttpStubServerTest extends FlatSpec with Matchers {

  behavior of "SttpStubServer"
  implicit val idMonad: MonadError[Identity] = IdMonad

  it should "combine tapir endpoint with sttp stub" in {
    import sttp.tapir.client.sttp._
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest4")
      .in(query[Int]("amount"))
      .post
      .out(jsonBody[ResponseWrapper])

    implicit val backend = SttpBackendStub
      .apply(idMonad)
      .whenRequestMatches(endpoint)
      .thenSuccess(ResponseWrapper(1.0))
    val response = endpoint.toSttpRequestUnsafe(uri"http://test.com").apply(11).send()

    response shouldBe Response.ok(ResponseWrapper(1.0))
  }

  it should "combine tapir endpoint with sttp stub - multiple inputs" in {
    import sttp.tapir.client.sttp._
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / path[String]("id") and query[Int]("amount"))
      .post
      .out(jsonBody[ResponseWrapper])

    implicit val backend = SttpBackendStub
      .apply(idMonad)
      .whenRequestMatches(endpoint)
      .thenSuccess(ResponseWrapper(1.0))
    val response = endpoint.toSttpRequestUnsafe(uri"http://test.com").apply("id1" -> 11).send()

    response shouldBe Response.ok(ResponseWrapper(1.0))
  }

  it should "match with inputs" in {
    import sttp.tapir.client.sttp._
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest4")
      .in(query[Int]("amount"))
      .post
      .out(jsonBody[ResponseWrapper])

    implicit val backend = SttpBackendStub
      .apply(idMonad)
      .whenInputMatches(endpoint) { amount => amount > 0 }
      .thenSuccess(ResponseWrapper(1.0))
      .whenInputMatches(endpoint) { amount => amount <= 0 }
      .generic
      .thenRespondServerError()

    val response1 = endpoint.toSttpRequestUnsafe(uri"http://test.com").apply(11).send()
    val response2 = endpoint.toSttpRequestUnsafe(uri"http://test.com").apply(-1).send()

    response1 shouldBe Response.ok(ResponseWrapper(1.0))
    response2 shouldBe Response.apply(Left(()), StatusCode.InternalServerError, "Internal server error")
  }

  it should "match with decode failure" in {
    import sttp.tapir.client.sttp._
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest4")
      .in(
        query[Int]("amount")
          .validate(Validator.min(0))
      )
      .post
      .out(jsonBody[ResponseWrapper])

    implicit val backend = SttpBackendStub
      .apply(idMonad)
      .whenDecodingInputFailure(endpoint)
      .generic
      .thenRespondWithCode(StatusCode.BadRequest)
    val response = endpoint.toSttpRequestUnsafe(uri"http://test.com").apply(-1).send()

    response shouldBe Response(Left(()), StatusCode.BadRequest)
  }
}

final case class ResponseWrapper(response: Double)
