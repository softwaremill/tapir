package sttp.tapir.examples

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions
import sttp.tapir.server.interceptor.exception.ExceptionContext
import sttp.tapir.server.interceptor.{CustomInterceptors, ValuedEndpointOutput}
import sttp.tapir.server.stub.TapirStubInterpreter

import scala.concurrent.Future

class AkkaServerStubInterpreterExample extends AsyncFlatSpec with Matchers {

  it should "use custom exception handler" in {
    val stubBackend: SttpBackend[Future, Any] = TapirStubInterpreter(UsersApi.options, SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(UsersApi.greetUser)
      .throwException(new RuntimeException("error"))
      .backend()

    sttp.client3.basicRequest
      .get(uri"http://test.com/api/users/greet")
      .send(stubBackend)
      .map(_.body shouldBe Left("failed due to error"))
  }

  it should "run greet users logic" in {
    val stubBackend: SttpBackend[Future, Any] = TapirStubInterpreter(UsersApi.options, SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(UsersApi.greetUser)
      .runLogic()
      .backend()

    val response = sttp.client3.basicRequest
      .get(uri"http://test.com/api/users/greet")
      .header("Authorization", "Bearer password")
      .send(stubBackend)

    // then
    response.map(_.body shouldBe Right("hello user123"))
  }
}

object UsersApi {

  val greetUser: ServerEndpoint[Any, Future] = endpoint.get
    .in("api" / "users" / "greet")
    .securityIn(auth.bearer[String]())
    .out(stringBody)
    .errorOut(stringBody)
    .serverSecurityLogic(token =>
      Future.successful {
        if (token == "password") Right("user123") else Left("unauthorized")
      }
    )
    .serverLogic(user => _ => Future.successful(Right(s"hello $user")))

  val options: CustomInterceptors[Future, AkkaHttpServerOptions] = AkkaHttpServerOptions.customInterceptors
    .exceptionHandler((ctx: ExceptionContext) =>
      Option(ValuedEndpointOutput(stringBody.and(statusCode), (s"failed due to ${ctx.e.getMessage}", StatusCode.InternalServerError)))
    )
}
