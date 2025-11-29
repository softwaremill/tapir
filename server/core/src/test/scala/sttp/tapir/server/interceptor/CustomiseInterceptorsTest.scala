package sttp.tapir.server.interceptor

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model._
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.TestUtil._
import sttp.tapir.server.interceptor.log.{ExceptionContext, ServerLog}
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.model.{ServerResponse, ValuedEndpointOutput}

import scala.collection.mutable.ListBuffer
import sttp.tapir.server.interceptor.reject.DefaultRejectHandler

class CustomiseInterceptorsTest extends AnyFlatSpec with Matchers {
  implicit val idMonad: MonadError[Identity] = IdentityMonad

  // Test request that successfully matches an endpoint
  val successRequest: ServerRequest = createTestRequest(List("test"), List(("x", "1")))

  // Test request that doesn't match any endpoint
  val notFoundRequest: ServerRequest = createTestRequest(List("notfound"))

  // Stub ServerLog implementation that captures log calls
  class StubServerLog extends ServerLog[Identity] {
    override type TOKEN = Unit

    val receivedRequests: ListBuffer[ServerRequest] = ListBuffer.empty
    val handledRequests: ListBuffer[(DecodeSuccessContext[Identity, ?, ?, ?], ServerResponse[?])] = ListBuffer.empty
    val handledRequestsByInterceptor: ListBuffer[(ServerRequest, ServerResponse[?])] = ListBuffer.empty
    val exceptions: ListBuffer[(ExceptionContext[?, ?], Throwable)] = ListBuffer.empty

    override def requestToken: TOKEN = ()

    override def requestReceived(request: ServerRequest, token: TOKEN): Identity[Unit] = {
      receivedRequests.append(request)
      ()
    }

    override def decodeFailureNotHandled(ctx: DecodeFailureContext, token: TOKEN): Identity[Unit] = {
      ()
    }

    override def decodeFailureHandled(ctx: DecodeFailureContext, response: ServerResponse[?], token: TOKEN): Identity[Unit] = {
      ()
    }

    override def securityFailureHandled(
        ctx: SecurityFailureContext[Identity, ?],
        response: ServerResponse[?],
        token: TOKEN
    ): Identity[Unit] = {
      ()
    }

    override def requestHandled(ctx: DecodeSuccessContext[Identity, ?, ?, ?], response: ServerResponse[?], token: TOKEN): Identity[Unit] = {
      handledRequests.append((ctx, response))
      ()
    }

    override def requestHandledByInterceptor(request: ServerRequest, response: ServerResponse[?], token: TOKEN): Identity[Unit] = {
      handledRequestsByInterceptor.append((request, response))
      ()
    }

    override def exception(ctx: ExceptionContext[?, ?], ex: Throwable, token: TOKEN): Identity[Unit] = {
      exceptions.append((ctx, ex))
      ()
    }
  }

  "CustomiseInterceptors.interceptors" should "log info message when request is completed successfully" in {
    // given
    val stubLog = new StubServerLog()
    val customise = CustomiseInterceptors[Identity, List[Interceptor[Identity]]](
      createOptions = _.interceptors
    ).serverLog(stubLog)

    val testEndpoint = endpoint.get
      .in("test")
      .in(query[String]("x"))
      .out(stringBody)
      .serverLogic[Identity](x => Right(s"Hello $x"))

    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(testEndpoint),
      TestRequestBody,
      StringToResponseBody,
      customise.interceptors,
      _ => ()
    )

    // when
    val _ = interpreter.apply(successRequest)

    // then
    stubLog.receivedRequests should have size 1
    stubLog.receivedRequests.head.uri shouldBe successRequest.uri
    stubLog.handledRequests should have size 1
    stubLog.handledRequests.head._2.code shouldBe StatusCode.Ok
  }

  it should "return 500 when request handling throws an exception" in {
    // given
    val stubLog = new StubServerLog()
    val customise = CustomiseInterceptors[Identity, List[Interceptor[Identity]]](
      createOptions = _.interceptors
    ).serverLog(stubLog)

    val testEndpoint = endpoint.get
      .in("test")
      .in(query[String]("x"))
      .out(stringBody)
      .serverLogic[Identity](_ => throw new RuntimeException("Test exception"))

    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(testEndpoint),
      TestRequestBody,
      StringToResponseBody,
      customise.interceptors,
      _ => ()
    )

    // when
    val response = interpreter.apply(successRequest)

    // then
    response match {
      case RequestResult.Response(serverResponse, _) =>
        serverResponse.code shouldBe StatusCode.InternalServerError
        serverResponse.body shouldBe Some("Internal server error")
      case _ => fail("Expected Response")
    }
    stubLog.exceptions should have size 1
    stubLog.exceptions.head._2.getMessage shouldBe "Test exception"
  }

  it should "log both exception and 500 response when server logic throws an exception" in {
    // given
    val stubLog = new StubServerLog()
    val customise = CustomiseInterceptors[Identity, List[Interceptor[Identity]]](
      createOptions = _.interceptors
    ).serverLog(stubLog)

    val testEndpoint = endpoint.get
      .in("test")
      .in(query[String]("x"))
      .out(stringBody)
      .serverLogic[Identity](_ => throw new RuntimeException("Test exception"))

    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(testEndpoint),
      TestRequestBody,
      StringToResponseBody,
      customise.interceptors,
      _ => ()
    )

    // when
    val _ = interpreter.apply(successRequest)

    // then
    // Verify the exception is logged
    stubLog.exceptions should have size 1
    stubLog.exceptions.head._2.getMessage shouldBe "Test exception"

    // Verify the 500 response is logged
    stubLog.handledRequests should have size 1
    stubLog.handledRequests.head._2.code shouldBe StatusCode.InternalServerError
  }

  it should "respect reject interceptor set to always return 404" in {
    // given
    val stubLog = new StubServerLog()
    val customise = CustomiseInterceptors[Identity, List[Interceptor[Identity]]](
      createOptions = _.interceptors
    )
      .serverLog(stubLog)
      .rejectHandler(DefaultRejectHandler.orNotFound[Identity])

    val testEndpoint = endpoint.get
      .in("test")
      .in(query[String]("x"))
      .out(stringBody)
      .serverLogic[Identity](x => Right(s"Hello $x"))

    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(testEndpoint),
      TestRequestBody,
      StringToResponseBody,
      customise.interceptors,
      _ => ()
    )

    // when
    val response = interpreter.apply(notFoundRequest)

    // then
    response match {
      case RequestResult.Response(serverResponse, _) =>
        serverResponse.code shouldBe StatusCode.NotFound
        serverResponse.body shouldBe Some("Not Found")
      case _ => fail("Expected Response")
    }
    stubLog.handledRequests shouldBe empty
  }

  it should "log response when request is rejected with RejectInterceptor" in {
    // given
    val stubLog = new StubServerLog()
    val customise = CustomiseInterceptors[Identity, List[Interceptor[Identity]]](
      createOptions = _.interceptors
    )
      .serverLog(stubLog)
      .rejectHandler(DefaultRejectHandler.orNotFound[Identity])

    val testEndpoint = endpoint.get
      .in("test")
      .in(query[String]("x"))
      .out(stringBody)
      .serverLogic[Identity](x => Right(s"Hello $x"))

    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(testEndpoint),
      TestRequestBody,
      StringToResponseBody,
      customise.interceptors,
      _ => ()
    )

    // when
    val response = interpreter.apply(notFoundRequest)

    // then
    response match {
      case RequestResult.Response(serverResponse, _) =>
        serverResponse.code shouldBe StatusCode.NotFound
        serverResponse.body shouldBe Some("Not Found")
      case _ => fail("Expected Response")
    }
    stubLog.receivedRequests should have size 1
    stubLog.receivedRequests.head.uri shouldBe notFoundRequest.uri
    stubLog.handledRequestsByInterceptor should have size 1
    stubLog.handledRequestsByInterceptor.head._2.code shouldBe StatusCode.NotFound
  }

  it should "apply interceptors in correct order" in {
    // given
    val callTrail: ListBuffer[String] = ListBuffer.empty
    val stubLog = new StubServerLog()

    val prependedInterceptor = new AddToTrailInterceptor(callTrail.append(_), "prepended")
    val appendedInterceptor = new AddToTrailInterceptor(callTrail.append(_), "appended")

    val customise = CustomiseInterceptors[Identity, List[Interceptor[Identity]]](
      createOptions = _.interceptors
    )
      .prependInterceptor(prependedInterceptor)
      .serverLog(stubLog)
      .appendInterceptor(appendedInterceptor)

    val testEndpoint = endpoint.get
      .in("test")
      .in(query[String]("x"))
      .out(stringBody)
      .serverLogic[Identity](x => Right(s"Hello $x"))

    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(testEndpoint),
      TestRequestBody,
      StringToResponseBody,
      customise.interceptors,
      _ => ()
    )

    // when
    val _ = interpreter.apply(successRequest)

    // then
    // Prepended interceptors are called first on request, appended are called last on request
    callTrail.toList shouldBe List("prepended success", "appended success")
  }

  it should "use separate ServerLogInterceptor when only serverLog is defined" in {
    // given
    val stubLog = new StubServerLog()
    val customise = CustomiseInterceptors[Identity, List[Interceptor[Identity]]](
      createOptions = _.interceptors
    )
      .serverLog(stubLog)
      .exceptionHandler(None)

    val testEndpoint = endpoint.get
      .in("test")
      .in(query[String]("x"))
      .out(stringBody)
      .serverLogic[Identity](x => Right(s"Hello $x"))

    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(testEndpoint),
      TestRequestBody,
      StringToResponseBody,
      customise.interceptors,
      _ => ()
    )

    // when
    val _ = interpreter.apply(successRequest)

    // then
    stubLog.receivedRequests should have size 1
    stubLog.handledRequests should have size 1
  }

  it should "use separate ExceptionInterceptor when only exceptionHandler is defined" in {
    // given
    val customise = CustomiseInterceptors[Identity, List[Interceptor[Identity]]](
      createOptions = _.interceptors
    )
      .serverLog(None)
      .exceptionHandler(
        sttp.tapir.server.interceptor.exception.ExceptionHandler.pure[Identity] { _ =>
          Some(ValuedEndpointOutput(statusCode.and(stringBody), (StatusCode.BadRequest, "Custom error")))
        }
      )

    val testEndpoint = endpoint.get
      .in("test")
      .in(query[String]("x"))
      .out(stringBody)
      .serverLogic[Identity](_ => throw new RuntimeException("Test exception"))

    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(testEndpoint),
      TestRequestBody,
      StringToResponseBody,
      customise.interceptors,
      _ => ()
    )

    // when
    val response = interpreter.apply(successRequest)

    // then
    response match {
      case RequestResult.Response(serverResponse, _) =>
        serverResponse.code shouldBe StatusCode.BadRequest
        serverResponse.body shouldBe Some("Custom error")
      case _ => fail("Expected Response")
    }
  }
}
