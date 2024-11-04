package sttp.tapir.server.stub

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.client3.monad.IdMonad
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.interceptor.exception.ExceptionHandler
import sttp.tapir.server.interceptor.reject.RejectHandler
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.generic.auto._
import sttp.tapir.tests.TestUtil.{readFromFile, writeToFile}
import sttp.model.Part
import sttp.tapir.TapirFile
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class TapirStubInterpreterTest extends AnyFlatSpec with Matchers {

  import ProductsApi._

  case class ServerOptions(interceptors: List[Interceptor[Identity]])
  object ServerOptions {
    def customiseInterceptors: CustomiseInterceptors[Identity, ServerOptions] =
      CustomiseInterceptors(createOptions = (ci: CustomiseInterceptors[Identity, ServerOptions]) => ServerOptions(ci.interceptors))
  }

  val options: CustomiseInterceptors[Identity, ServerOptions] = ServerOptions.customiseInterceptors

  behavior of "TapirStubInterpreter"

  it should "stub endpoint logic with success response" in {
    // given
    val server = TapirStubInterpreter(options, SttpBackendStub(IdMonad))
      .whenEndpoint(getProduct)
      .thenRespond("computer")
      .backend()

    // when
    val response = sttp.client3.basicRequest.get(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Right("computer")
  }

  it should "stub endpoint logic with trailing slash with success response" in {
    // given
    val server = TapirStubInterpreter(options, SttpBackendStub(IdMonad))
      .whenEndpoint(getProduct)
      .thenRespond("computer")
      .backend()

    // when
    val response = sttp.client3.basicRequest.get(uri"http://test.com/api/products/").send(server)

    // then
    response.body shouldBe Right("computer")
  }

  it should "stub endpoint logic with error response" in {
    // given
    val server = TapirStubInterpreter[Identity, Nothing, ServerOptions](options, SttpBackendStub(IdMonad))
      .whenEndpoint(getProduct)
      .thenRespondError("failed")
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
      .thenRespond("created", runSecurityLogic = false)
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
      .thenRespondError("failed", runSecurityLogic = false)
      .backend()

    // when
    val response = sttp.client3.basicRequest.post(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Left("failed")
  }

  it should "use interceptors from server options" in {
    val exceptionHandler =
      ExceptionHandler.pure[Identity](_ => Some(ValuedEndpointOutput(stringBody, "failed due to exception")))

    val rejectHandler =
      RejectHandler.pure[Identity](_ => Some(ValuedEndpointOutput(statusCode, StatusCode.NotAcceptable)))

    val decodeFailureHandler =
      DefaultDecodeFailureHandler[Identity].copy[Identity](failureMessage = _ => "failed to decode")

    // given
    val opts = options
      .decodeFailureHandler(decodeFailureHandler)
      .exceptionHandler(exceptionHandler)
      .rejectHandler(rejectHandler)

    val server = TapirStubInterpreter(opts, SttpBackendStub(IdMonad))
      .whenEndpoint(getProduct.in(query[Int]("id").validate(Validator.min(10))))
      .thenRespond("computer")
      .whenEndpoint(createProduct)
      .thenThrowException(new RuntimeException)
      .backend()

    // when fails to decode then uses decode handler
    sttp.client3.basicRequest.get(uri"http://test.com/api/products?id=5").send(server).body shouldBe Left("failed to decode")

    // when throws exception then uses exception handler
    sttp.client3.basicRequest.post(uri"http://test.com/api/products").send(server).body shouldBe Right("failed due to exception")

    // when no matching endpoint then uses reject handler
    sttp.client3.basicRequest.delete(uri"http://test.com/api/products").send(server).code shouldBe StatusCode.NotAcceptable
  }

  it should "throw exception when user sends raw body to stream input" in {
    val server = TapirStubInterpreter(List.empty, SttpBackendStub(IdMonad))
      .whenEndpoint(ProductsApi.inProductStream)
      .thenRespond(())
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
      .thenRespond("computer")
      .backend()

    val ex = the[IllegalArgumentException] thrownBy sttp.client3.basicRequest
      .get(uri"http://test.com/api/products")
      .streamBody(AnyStreams)(List("a", "b", "c"))
      .send(server)

    ex.getMessage shouldBe "Stream body provided while endpoint accepts raw body type"
  }

  it should "allow overriding response parsing description created by sttp client interpreter to test exception handling" in {
    // given
    val se = endpoint.get.in("test").serverLogicSuccess[Identity](_ => throw new RuntimeException("Error"))
    val server = TapirStubInterpreter(SttpBackendStub(IdMonad))
      .whenServerEndpoint(se)
      .thenRunLogic()
      .backend()

    // when
    val request = SttpClientInterpreter()
      .toRequest(se.endpoint, None)
      .apply(())
      .response(asString)
    val response = request.send(server)

    // then
    response.body shouldBe Left("Internal server error")
    response.code shouldBe StatusCode.InternalServerError
  }

  it should "handle multipart body" in {
    // given
    val e =
      endpoint.post
        .in("api" / "multipart")
        .in(multipartBody)
        .out(stringBody)

    val server = TapirStubInterpreter(SttpBackendStub(IdMonad))
      .whenEndpoint(e)
      .thenRespond("success")
      .backend()

    // when
    val response = sttp.client3.basicRequest
      .post(uri"http://test.com/api/multipart")
      .multipartBody(
        multipart("name", "abc"),
        multipartFile("file", writeToFile("file_content"))
      )
      .send(server)

    // then
    response.body shouldBe Right("success")
  }

  it should "correctly process a multipart body" in {
    // given
    val e =
      endpoint.post
        .in("api" / "multipart")
        .in(multipartBody)
        .out(stringBody)

    val server = TapirStubInterpreter(SttpBackendStub.synchronous)
      .whenServerEndpointRunLogic(e.serverLogic((multipartData) => {
        val partOpt = multipartData.find(_.name == "name")
        val fileOpt = multipartData.find(_.name == "file")

        (partOpt, fileOpt) match {
          case (Some(part), Some(filePart)) =>
            val partData = new String(part.body)
            val fileData = new String(filePart.body)
            IdMonad.unit(Right("name: " + partData + " file: " + fileData))

          case (Some(_), None) =>
            IdMonad.unit(Right("File part not found"))

          case (None, Some(_)) =>
            IdMonad.unit(Right("Part not found"))

          case (None, None) =>
            IdMonad.unit(Right("Both parts not found"))
        }
      }))
      .backend()

    // when
    val response = sttp.client3.basicRequest
      .post(uri"http://test.com/api/multipart")
      .multipartBody(
        multipart("name", "abc"),
        multipartFile("file", writeToFile("file_content"))
      )
      .send(server)

    // then
    response.body shouldBe Right("name: abc file: file_content")
  }

  it should "correctly handle derived multipart body" in {
    // given
    val e =
      endpoint.post
        .in("api" / "multipart")
        .in(multipartBody[MultipartData])
        .out(stringBody)

    val server = TapirStubInterpreter(SttpBackendStub(IdMonad))
      .whenServerEndpointRunLogic(e.serverLogic(multipartData => {
        val fileContent = Await.result(readFromFile(multipartData.file.body), 3.seconds)
        IdMonad.unit(Right("name: " + multipartData.name + " year: " + multipartData.year + " file: " + fileContent))
      }))
      .backend()

    // when
    val response = sttp.client3.basicRequest
      .post(uri"http://test.com/api/multipart")
      .multipartBody(
        multipart("name", "abc"),
        multipart("year", "2024"),
        multipartFile("file", writeToFile("file_content"))
      )
      .send(server)

    // then
    response.body shouldBe Right("name: abc year: 2024 file: file_content")
  }

  it should "throw exception when bytearray body provided while endpoint accepts fileBody" in {
    // given
    val e =
      endpoint.post
        .in("api" / "multipart")
        .in(multipartBody[MultipartData])
        .out(stringBody)

    val server = TapirStubInterpreter(SttpBackendStub(IdMonad))
      .whenEndpoint(e)
      .thenRespond("success")
      .backend()

    // when
    val response = the[IllegalArgumentException] thrownBy sttp.client3.basicRequest
      .post(uri"http://test.com/api/multipart")
      .multipartBody(
        multipart("name", "abc"),
        multipart("year", "2024"),
        multipart("file", "file_content".getBytes())
      )
      .send(server)

    // then
    response.getMessage shouldBe "ByteArray part provided while expecting a File part"
  }
}

case class MultipartData(name: String, year: Int, file: Part[TapirFile])

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
