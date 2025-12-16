package sttp.tapir.server.stub4

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.model.{Header, Part, StatusCode}
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.interceptor.exception.ExceptionHandler
import sttp.tapir.server.interceptor.reject.RejectHandler
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.generic.auto._
import sttp.tapir.tests.TestUtil.{readFromFile, writeToFile}
import sttp.tapir.TapirFile
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import sttp.client4.testing.BackendStub
import sttp.monad.IdentityMonad
import sttp.client4.testing.StreamBackendStub

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
    val server = TapirSyncStubInterpreter(options)
      .whenEndpoint(getProduct)
      .thenRespond("computer")
      .backend()

    // when
    val response = basicRequest.get(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Right("computer")
  }

  it should "stub endpoint logic with trailing slash with success response" in {
    // given
    val server = TapirSyncStubInterpreter(options)
      .whenEndpoint(getProduct)
      .thenRespond("computer")
      .backend()

    // when
    val response = basicRequest.get(uri"http://test.com/api/products/").send(server)

    // then
    response.body shouldBe Right("computer")
  }

  it should "stub endpoint logic with error response" in {
    // given
    val server = TapirSyncStubInterpreter(options)
      .whenEndpoint(getProduct)
      .thenRespondError("failed")
      .backend()

    // when
    val response = basicRequest.get(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Left("failed")
  }

  it should "run defined endpoint server and auth logic" in {
    // given
    val server = TapirSyncStubInterpreter(options)
      .whenServerEndpointRunLogic(
        createProduct
          .serverSecurityLogic { _ => IdentityMonad.unit(Right("user1"): Either[String, String]) }
          .serverLogic { user => _ => IdentityMonad.unit(Right(s"created by $user")) }
      )
      .backend()

    // when
    val response = basicRequest.post(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Right("created by user1")
  }

  it should "stub endpoint server logic with success response, skip auth" in {
    // given
    val server = TapirSyncStubInterpreter(options)
      .whenServerEndpoint(
        createProduct
          .serverSecurityLogic { _ => IdentityMonad.unit(Left("unauthorized"): Either[String, String]) }
          .serverLogic { user => _ => IdentityMonad.unit(Right(s"Created by $user")) }
      )
      .thenRespond("created", runSecurityLogic = false)
      .backend()

    // when
    val response = basicRequest.post(uri"http://test.com/api/products").send(server)

    // then
    response.body shouldBe Right("created")
  }

  it should "stub server endpoint logic with error response, skip auth" in {
    // given
    val server = TapirSyncStubInterpreter(options)
      .whenServerEndpoint(
        createProduct
          .serverSecurityLogic { _ => IdentityMonad.unit(Left("unauthorized"): Either[String, String]) }
          .serverLogic { user => _ => IdentityMonad.unit(Right(s"created by $user")) }
      )
      .thenRespondError("failed", runSecurityLogic = false)
      .backend()

    // when
    val response = basicRequest.post(uri"http://test.com/api/products").send(server)

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

    val server = TapirSyncStubInterpreter(opts)
      .whenEndpoint(getProduct.in(query[Int]("id").validate(Validator.min(10))))
      .thenRespond("computer")
      .whenEndpoint(createProduct)
      .thenThrowException(new RuntimeException)
      .backend()

    // when fails to decode then uses decode handler
    basicRequest.get(uri"http://test.com/api/products?id=5").send(server).body shouldBe Left("failed to decode")

    // when throws exception then uses exception handler
    basicRequest.post(uri"http://test.com/api/products").send(server).body shouldBe Right("failed due to exception")

    // when no matching endpoint then uses reject handler
    basicRequest.delete(uri"http://test.com/api/products").send(server).code shouldBe StatusCode.NotAcceptable
  }

  it should "throw exception when user sends raw body to stream input" in {
    val server = TapirSyncStubInterpreter(List.empty, BackendStub.synchronous)
      .whenEndpoint(ProductsApi.inProductStream)
      .thenRespond(())
      .backend()

    val ex = the[IllegalArgumentException] thrownBy basicRequest
      .post(uri"http://test.com/api/products/stream")
      .body("abc")
      .send(server)

    ex.getMessage shouldBe "Raw body provided while endpoint accepts stream body"
  }

  it should "throw exception when user sends stream body to raw input" in {
    val server: StreamBackend[Identity, AnyStreams] =
      TapirStreamStubInterpreter(StreamBackendStub.synchronous[AnyStreams])
        .whenEndpoint(ProductsApi.getProduct.in(stringBody))
        .thenRespond("computer")
        .backend()

    val ex = the[IllegalArgumentException] thrownBy basicRequest
      .get(uri"http://test.com/api/products")
      .streamBody(AnyStreams)(List("a", "b", "c"))
      .send(server)

    ex.getMessage shouldBe "Stream body provided while endpoint accepts raw body type"
  }

  it should "allow overriding response parsing description created by sttp client interpreter to test exception handling" in {
    // given
    val se = endpoint.get.in("test").serverLogicSuccess[Identity](_ => throw new RuntimeException("Error"))
    val server = TapirSyncStubInterpreter()
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

    val server = TapirSyncStubInterpreter()
      .whenEndpoint(e)
      .thenRespond("success")
      .backend()

    // when
    val response = basicRequest
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

    val server = TapirSyncStubInterpreter()
      .whenServerEndpointRunLogic(e.serverLogic[Identity]((multipartData) => {
        val partOpt = multipartData.find(_.name == "name")
        val fileOpt = multipartData.find(_.name == "file")

        IdentityMonad.unit {
          (partOpt, fileOpt) match {
            case (Some(part), Some(filePart)) =>
              val partData = new String(part.body)
              val fileData = new String(filePart.body)
              Right("name: " + partData + " file: " + fileData)

            case (Some(_), None) =>
              Right("File part not found")

            case (None, Some(_)) =>
              Right("Part not found")

            case (None, None) =>
              Right("Both parts not found")
          }
        }
      }))
      .backend()

    // when
    val response = basicRequest
      .post(uri"http://test.com/api/multipart")
      .multipartBody(
        multipart("name", "abc"),
        multipartFile("file", writeToFile("file_content"))
      )
      .send(server)

    // then
    response.body shouldBe Right("name: abc file: file_content")
  }

  it should "retrieve individual part headers and content-disposition parameters" in {
    // given
    val e = endpoint.post
      .in("api" / "multipart")
      .in(multipartBody)
      .out(stringBody)
      .errorOut(stringBody)

    val server = TapirSyncStubInterpreter()
      .whenServerEndpointRunLogic(e.serverLogic[Identity] { multipartData =>
        val maybePart = multipartData.find(_.name == "name")
        val maybeContentLength = maybePart.flatMap(_.contentLength)
        val dispositionParams = maybePart.map(_.dispositionParams).getOrElse(Map.empty)

        IdentityMonad.unit {
          (maybeContentLength, dispositionParams.get("param")) match {
            case (Some(contentLength), Some(value)) =>
              Right("contentLength: " + contentLength + " param: " + value)
            case (Some(_), None) =>
              Left("'param' disposition parameter not found")
            case (None, Some(_)) =>
              Left("Content-Length header not found")
            case (None, None) =>
              Left("Both Content-Length header and 'param' disposition parameter not found")
          }
        }
      })
      .backend()

    // when
    val response = basicRequest
      .post(uri"http://test.com/api/multipart")
      .multipartBody(
        multipart("name", "abc")
          .dispositionParam("param", "hello")
          .header(Header.contentLength(12))
      )
      .send(server)

    // then
    response.body shouldBe Right("contentLength: 12 param: hello")
  }

  it should "correctly handle derived multipart body" in {
    // given
    val e =
      endpoint.post
        .in("api" / "multipart")
        .in(multipartBody[MultipartData])
        .out(stringBody)

    val server = TapirSyncStubInterpreter()
      .whenServerEndpointRunLogic(e.serverLogic[Identity](multipartData => {
        val fileContent = Await.result(readFromFile(multipartData.file.body), 3.seconds)
        IdentityMonad.unit(Right("name: " + multipartData.name + " year: " + multipartData.year + " file: " + fileContent))
      }))
      .backend()

    // when
    val response = basicRequest
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

    val server = TapirSyncStubInterpreter()
      .whenEndpoint(e)
      .thenRespond("success")
      .backend()

    // when
    val response = the[IllegalArgumentException] thrownBy basicRequest
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
