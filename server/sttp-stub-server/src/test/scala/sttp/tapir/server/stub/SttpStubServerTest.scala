package sttp.tapir.server.stub

import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.client3.monad._
import sttp.client3.testing.SttpBackendStub
import sttp.model.{Header, MediaType, StatusCode}
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.client.sttp._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{EndpointInterceptor, RequestHandler, RequestInterceptor, Responder}

class SttpStubServerTest extends AnyFlatSpec with Matchers {

  behavior of "SttpStubServer"
  implicit val idMonad: MonadError[Identity] = IdMonad

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

    response shouldBe Response(Left(ResponseWrapper(1.0)), StatusCode.BadRequest, "", List(Header.contentType(MediaType.ApplicationJson)))
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

  it should "stub server endpoint" in {
    // given
    val endpoint: ServerEndpoint[Any, Identity] = sttp.tapir.endpoint
      .in("api" / "hello")
      .out(stringBody)
      .get
      .serverLogic { _ => idMonad.unit(Right("hello")) }

    // when
    val backend: SttpBackendStub[Identity, Any] = SttpBackendStub(idMonad)
      .whenRequestMatchesEndpointThenLogic(endpoint)
      .whenAnyRequest
      .thenRespondServerError()

    // then
    sttp.client3.basicRequest.get(uri"http://abc.xyz/api/hello").send(backend).body shouldBe Right("hello")
    sttp.client3.basicRequest.get(uri"http://abc.xyz/api/unknown").send(backend).code shouldBe StatusCode.InternalServerError
  }

  it should "work with request bodies when interpreting endpoints" in {
    // given
    val endpoint: ServerEndpoint[Any, Identity] = sttp.tapir.endpoint
      .in("mirror")
      .in(stringBody)
      .out(stringBody)
      .post
      .serverLogic { in => idMonad.unit(Right(in)) }

    // when
    val backend: SttpBackendStub[Identity, Any] = SttpBackendStub(idMonad)
      .whenRequestMatchesEndpointThenLogic(endpoint)

    // then
    sttp.client3.basicRequest.post(uri"/mirror").body("hello").send(backend).body shouldBe Right("hello")
  }

  it should "stub server endpoint with interceptors" in {
    // given
    val endpoint: ServerEndpoint[Any, Identity] = sttp.tapir.endpoint
      .in("api" / "hello")
      .out(stringBody)
      .get
      .serverLogic { _ => idMonad.unit(Right("hello")) }

    var x = 0

    val interceptor: RequestInterceptor[Identity] = {
      new RequestInterceptor[Identity] {
        override def apply[B](
            responder: Responder[Identity, B],
            requestHandler: EndpointInterceptor[Identity] => RequestHandler[Identity, B]
        ): RequestHandler[Identity, B] = RequestHandler.from { (request, _: MonadError[Identity]) =>
          x = 10
          requestHandler(EndpointInterceptor.noop).apply(request)
        }
      }
    }

    // when
    val backend: SttpBackendStub[Identity, Any] = SttpBackendStub(idMonad)
      .whenRequestMatchesEndpointThenLogic(endpoint, List(interceptor))
      .whenAnyRequest
      .thenRespondServerError()

    // then
    sttp.client3.basicRequest.get(uri"http://abc.xyz/api/hello").send(backend).body shouldBe Right("hello")
    x shouldBe 10
  }
}

final case class ResponseWrapper(response: Double)
