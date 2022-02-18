package sttp.tapir.server.stub

import org.scalatest.Assertions
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.monad.IdMonad
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Identity, _}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.interceptor.exception.ExceptionContext
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor, ValuedEndpointOutput}
import sttp.tapir.tests.Streaming.in_stream_out_stream

import scala.concurrent.Future
import scala.util.Failure

class TapirStubInterpreterTest extends AnyFlatSpec with Matchers {

  import ProductsApi._

  case class ServerOptions(interceptors: List[Interceptor[Identity]])
  object ServerOptions {
    def customInterceptors: CustomInterceptors[Identity, ServerOptions] =
      CustomInterceptors(createOptions = (ci: CustomInterceptors[Identity, ServerOptions]) => ServerOptions(ci.interceptors))
  }

  val options: CustomInterceptors[Identity, ServerOptions] = ServerOptions.customInterceptors

  behavior of "TapirStubInterpreter"

  it should "stub endpoint logic with success response" in {
    // given
    val server = TapirStubInterpreter(options, SttpBackendStub(IdMonad))
      .whenEndpoint(getProduct)
      .respond("computer")
      .backend()

    // when
    val response = sttp.client3.basicRequest.get(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Right("computer")
  }

  it should "stub endpoint logic with error response" in {
    // given
    val server = TapirStubInterpreter[Identity, Nothing, ServerOptions](options, SttpBackendStub(IdMonad))
      .whenEndpoint(getProduct)
      .respondError("failed")
      .backend()

    // when
    val response = sttp.client3.basicRequest.get(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Left("failed")
  }

  it should "run defined endpoint server and auth logic" in {
    // given
    val server = TapirStubInterpreter[Identity, Nothing, ServerOptions](options, SttpBackendStub(IdMonad))
      .whenServerEndpointRunLogic(
        createProduct
          .serverSecurityLogic { _ => IdMonad.unit(Right("user1"): Either[String, String]) }
          .serverLogic { user => _ => IdMonad.unit(Right(s"created by $user")) }
      )
      .backend()

    // when
    val response = sttp.client3.basicRequest.post(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Right("created by user1")
  }

  it should "stub endpoint server logic with success response, skip auth" in {
    // given
    val server = TapirStubInterpreter[Identity, Nothing, ServerOptions](options, SttpBackendStub(IdMonad))
      .whenServerEndpoint(
        createProduct
          .serverSecurityLogic { _ => IdMonad.unit(Left("unauthorized"): Either[String, String]) }
          .serverLogic { user => _ => IdMonad.unit(Right(s"Created by $user")) }
      )
      .respond("created", runSecurityLogic = false)
      .backend()

    // when
    val response = sttp.client3.basicRequest.post(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Right("created")
  }

  it should "stub server endpoint logic with error response, skip auth" in {
    // given
    val server = TapirStubInterpreter[Identity, Nothing, ServerOptions](options, SttpBackendStub(IdMonad))
      .whenServerEndpoint(
        createProduct
          .serverSecurityLogic { _ => IdMonad.unit(Left("unauthorized"): Either[String, String]) }
          .serverLogic { user => _ => IdMonad.unit(Right(s"created by $user")) }
      )
      .respondError("failed", runSecurityLogic = false)
      .backend()

    // when
    val response = sttp.client3.basicRequest.post(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Left("failed")
  }

  it should "use interceptors from server options" in {
    // given
    val opts = options
      .decodeFailureHandler(DefaultDecodeFailureHandler.default.copy(failureMessage = _ => "failed to decode"))
      .exceptionHandler((_: ExceptionContext) => Some(ValuedEndpointOutput(stringBody, "failed due to exception")))
      .rejectInterceptor(new RejectInterceptor[Identity](_ => Some(StatusCode.NotAcceptable)))

    val server = TapirStubInterpreter(opts, SttpBackendStub(IdMonad))
      .whenEndpoint(getProduct.in(query[Int]("id").validate(Validator.min(10))))
      .respond("computer")
      .whenEndpoint(createProduct)
      .throwException(new RuntimeException)
      .backend()

    // when fails to decode then uses decode handler
    sttp.client3.basicRequest.get(uri"http://test.com/api/products?id=5").send(server).body shouldBe Left("failed to decode")

    // when throws exception then uses exception handler
    sttp.client3.basicRequest.post(uri"http://test.com/api/products").send(server).body shouldBe Right("failed due to exception")

    // when no matching endpoint then uses reject handler
    sttp.client3.basicRequest.get(uri"http://test.com/iamlost").send(server).code shouldBe StatusCode.NotAcceptable
  }

  it should "throw exception when user sends raw body to stream input" in {
    val server = TapirStubInterpreter(List.empty, SttpBackendStub(IdMonad))
      .whenEndpoint(ProductsApi.inProductStream)
      .respond(())
      .backend()

    val ex = the[IllegalArgumentException] thrownBy sttp.client3.basicRequest
      .post(uri"http://test.com/api/products/stream")
      .body("abc")
      .send(server)

    ex.getMessage shouldBe "Raw body provided while endpoint accepts stream body"
  }

  it should "throw exception when user sends stream body to raw input" in {
    val server: SttpBackend[Identity, AnyStreams] = TapirStubInterpreter(List.empty, SttpBackendStub(IdMonad))
      .whenEndpoint(ProductsApi.getProduct.in(stringBody))
      .respond("computer")
      .backend()

    val ex = the[IllegalArgumentException] thrownBy sttp.client3.basicRequest
      .get(uri"http://test.com/api/products")
      .streamBody(AnyStreams)(List("a", "b", "c"))
      .send(server)

    ex.getMessage shouldBe "Stream body provided while endpoint accepts raw body type"
  }
}

object ProductsApi {

  val getProduct: Endpoint[Unit, Unit, String, String, Any] = endpoint.get
    .in("api" / "products")
    .out(stringBody)
    .errorOut(stringBody)

  val createProduct: Endpoint[Unit, Unit, String, String, Any] = endpoint.post
    .in("api" / "products")
    .out(stringBody)
    .errorOut(stringBody)

  val inProductStream: Endpoint[Unit, Any, Unit, Unit, AnyStreams] = endpoint.post
    .in("api" / "products" / "stream")
    .in(streamTextBody(AnyStreams)(CodecFormat.TextPlain()))
}
