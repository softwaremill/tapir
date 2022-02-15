package sttp.tapir.server.stub

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

class TapirStubInterpreterTest extends AnyFlatSpec with Matchers {

  import ProductsApi._

  val sttpBackendStub: SttpBackendStub[Identity, Nothing] = SttpBackendStub.apply(IdMonad)

  case class ServerOptions(interceptors: List[Interceptor[Identity]])
  object ServerOptions {
    def customInterceptors: CustomInterceptors[Identity, ServerOptions] =
      CustomInterceptors(createOptions = (ci: CustomInterceptors[Identity, ServerOptions]) => ServerOptions(ci.interceptors))
  }

  val options: CustomInterceptors[Identity, ServerOptions] = ServerOptions.customInterceptors

  behavior of "TapirStubInterpreter"

  it should "stub endpoint logic with success response" in {
    // given
    val server = TapirStubInterpreter(options, sttpBackendStub)
      .forEndpoint(getProduct)
      .returnSuccess("computer")
      .backend()

    // when
    val response = sttp.client3.basicRequest.get(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Right("computer")
  }

  it should "stub endpoint logic with error response" in {
    // given
    val server = TapirStubInterpreter[Identity, Nothing, ServerOptions](options, sttpBackendStub)
      .forEndpoint(getProduct)
      .returnError("failed")
      .backend()

    // when
    val response = sttp.client3.basicRequest.get(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Left("failed")
  }

  it should "run defined endpoint server and auth logic" in {
    // given
    val server = TapirStubInterpreter[Identity, Nothing, ServerOptions](options, sttpBackendStub)
      .forServerEndpointRunLogic(
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
    val server = TapirStubInterpreter[Identity, Nothing, ServerOptions](options, sttpBackendStub)
      .forServerEndpoint(
        createProduct
          .serverSecurityLogic { _ => IdMonad.unit(Left("unauthorized"): Either[String, String]) }
          .serverLogic { user => _ => IdMonad.unit(Right(s"Created by $user")) }
      )
      .returnSuccess("created", runAuthLogic = false)
      .backend()

    // when
    val response = sttp.client3.basicRequest.post(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Right("created")
  }

  it should "stub server endpoint logic with error response, skip auth" in {
    // given
    val server = TapirStubInterpreter[Identity, Nothing, ServerOptions](options, sttpBackendStub)
      .forServerEndpoint(
        createProduct
          .serverSecurityLogic { _ => IdMonad.unit(Left("unauthorized"): Either[String, String]) }
          .serverLogic { user => _ => IdMonad.unit(Right(s"created by $user")) }
      )
      .returnError("failed", runAuthLogic = false)
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

    val server = TapirStubInterpreter(opts, sttpBackendStub)
      .forEndpoint(getProduct.in(query[Int]("id").validate(Validator.min(10))))
      .returnSuccess("computer")
      .forEndpoint(createProduct)
      .throwException(new RuntimeException)
      .backend()

    // when fails to decode then uses decode handler
    sttp.client3.basicRequest.get(uri"http://test.com/api/products?id=5").send(server).body shouldBe Left("failed to decode")

    // when throws exception then uses exception handler
    sttp.client3.basicRequest.post(uri"http://test.com/api/products").send(server).body shouldBe Right("failed due to exception")

    // when no matching endpoint then uses reject handler
    sttp.client3.basicRequest.get(uri"http://test.com/iamlost").send(server).code shouldBe StatusCode.NotAcceptable
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
}
