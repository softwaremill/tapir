package sttp.tapir.server.stub

import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.model.{Header, MediaType, StatusCode}
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.client.sttp._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

class SttpStubServerTest extends AnyFlatSpec with Matchers {

  behavior of "SttpStubServer"
  implicit val idMonad: MonadError[Identity] = IdentityMonad

  it should "combine tapir endpoint with sttp stub" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest4")
      .in(query[Int]("amount"))
      .post
      .out(jsonBody[ResponseWrapper])

    val backend = SttpBackendStub
      .apply(idMonad)
      .whenRequestMatchesEndpoint(endpoint)
      .thenSuccess(ResponseWrapper(1.0))
    val response: Identity[Response[Either[Unit, ResponseWrapper]]] =
      SttpClientInterpreter().toRequestThrowDecodeFailures(endpoint, Some(uri"http://test.com")).apply(11).send(backend)

    response.body shouldBe Right(ResponseWrapper(1.0))
  }

  it should "combine tapir endpoint with sttp stub - errors" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest4")
      .in(query[Int]("amount"))
      .post
      .errorOut(jsonBody[ResponseWrapper])

    val backend = SttpBackendStub
      .apply(idMonad)
      .whenRequestMatchesEndpoint(endpoint)
      .thenError(ResponseWrapper(1.0), StatusCode.BadRequest)
    val response: Identity[Response[Either[ResponseWrapper, Unit]]] =
      SttpClientInterpreter().toRequestThrowDecodeFailures(endpoint, Some(uri"http://test.com")).apply(11).send(backend)

    response shouldBe Response(
      Left(ResponseWrapper(1.0)),
      StatusCode.BadRequest,
      "Bad Request",
      List(Header.contentType(MediaType.ApplicationJson))
    )
  }

  it should "combine tapir endpoint with sttp stub - multiple inputs" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / path[String]("id") and query[Int]("amount"))
      .post
      .out(jsonBody[ResponseWrapper])

    val backend = SttpBackendStub
      .apply(idMonad)
      .whenRequestMatchesEndpoint(endpoint)
      .thenSuccess(ResponseWrapper(1.0))
    val response: Identity[Response[Either[Unit, ResponseWrapper]]] =
      SttpClientInterpreter().toRequestThrowDecodeFailures(endpoint, Some(uri"http://test.com")).apply("id1" -> 11).send(backend)

    response.body shouldBe Right(ResponseWrapper(1.0))
  }

  it should "combine tapir endpoint with sttp stub - header output" in {
    // given
    val endpoint = sttp.tapir.endpoint.post
      .out(header[String]("X"))

    val backend = SttpBackendStub
      .apply(idMonad)
      .whenRequestMatchesEndpoint(endpoint)
      .thenSuccess("x")
    val response: Identity[Response[Either[Unit, String]]] =
      SttpClientInterpreter().toRequestThrowDecodeFailures(endpoint, Some(uri"http://test.com")).apply(()).send(backend)

    response.body shouldBe Right("x")
    response.header("X") shouldBe Some("x")
  }

  it should "match with inputs" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest4")
      .in(query[Int]("amount"))
      .post
      .out(jsonBody[ResponseWrapper])

    val backend: SttpBackendStub[Identity, Any] = SttpBackendStub
      .apply(idMonad)
      .whenInputMatches(endpoint) { amount => amount > 0 }
      .thenSuccess(ResponseWrapper(1.0))
      .whenInputMatches(endpoint) { amount => amount <= 0 }
      .generic
      .thenRespondServerError()

    val response1: Identity[Response[Either[Unit, ResponseWrapper]]] =
      SttpClientInterpreter().toRequestThrowDecodeFailures(endpoint, Some(uri"http://test.com")).apply(11).send(backend)
    val response2: Identity[Response[Either[Unit, ResponseWrapper]]] =
      SttpClientInterpreter().toRequestThrowDecodeFailures(endpoint, Some(uri"http://test.com")).apply(-1).send(backend)

    response1.body shouldBe Right(ResponseWrapper(1.0))
    response2.body shouldBe Left(())
    response2.code shouldBe StatusCode.InternalServerError
    response2.statusText shouldBe "Internal server error"
  }

  it should "match on body inputs" in {
    // given
    val endpoint = sttp.tapir.endpoint.post.in(plainBody[Int]).out(plainBody[Int])

    val backend: SttpBackendStub[Identity, Any] = SttpBackendStub
      .apply(idMonad)
      .whenInputMatches(endpoint) { body => body > 2 }
      .thenSuccess(42)
      .whenAnyRequest
      .thenRespondServerError()

    val response1 = SttpClientInterpreter().toRequestThrowDecodeFailures(endpoint, Some(uri"http://test.com")).apply(10).send(backend)
    response1.body shouldBe Right(42)

    val response2 = SttpClientInterpreter().toRequestThrowDecodeFailures(endpoint, Some(uri"http://test.com")).apply(1).send(backend)
    response2.body shouldBe Left(())
    response2.code shouldBe StatusCode.InternalServerError
  }

  it should "match with decode failure" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest4")
      .in(
        query[Int]("amount")
          .validate(Validator.min(0))
      )
      .post
      .out(jsonBody[ResponseWrapper])

    val backend: SttpBackendStub[Identity, Any] = SttpBackendStub
      .apply(idMonad)
      .whenDecodingInputFailure(endpoint)
      .generic
      .thenRespondWithCode(StatusCode.BadRequest)
    val response: Identity[Response[Either[Unit, ResponseWrapper]]] =
      SttpClientInterpreter().toRequestThrowDecodeFailures(endpoint, Some(uri"http://test.com")).apply(-1).send(backend)

    response shouldBe Response(Left(()), StatusCode.BadRequest)
  }

  trait TestStreams extends Streams[TestStreams] {
    override type BinaryStream = Vector[Int]
    override type Pipe[X, Y] = Nothing
  }
  object TestStreams extends TestStreams

  it should "handle endpoints with stream input" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "stream")
      .in(streamTextBody(TestStreams)(CodecFormat.TextPlain()))
      .out(stringBody)

    val backend = SttpBackendStub[Identity, TestStreams](idMonad)
      .whenRequestMatchesEndpoint(endpoint)
      .thenSuccess("abc")

    // when
    val response = SttpClientInterpreter()
      .toRequestThrowDecodeFailures(endpoint, Some(uri"http://test.com"))
      .apply(Vector(1, 2, 3))
      .send(backend)

    // then
    response.body shouldBe Right("abc")
  }

  it should "handle endpoints with stream output" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "stream")
      .out(streamTextBody(TestStreams)(CodecFormat.TextPlain()))

    val backend = SttpBackendStub[Identity, TestStreams](idMonad)
      .whenRequestMatchesEndpoint(endpoint)
      .thenSuccess(Vector(1, 2, 3))

    // when
    val response = sttp.client3.basicRequest
      .get(uri"http://test.com/api/stream")
      .response(asStreamAlwaysUnsafe(TestStreams))
      .send(backend)

    // then
    response.body shouldBe Vector(1, 2, 3)
  }
}

final case class ResponseWrapper(response: Double)
