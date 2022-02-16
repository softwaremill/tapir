package sttp.tapir.examples

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.model.StatusCode
import sttp.monad.FutureMonad
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions
import sttp.tapir.server.interceptor.exception.ExceptionContext
import sttp.tapir.server.interceptor.{CustomInterceptors, ValuedEndpointOutput}
import sttp.tapir.server.stub.TapirStubInterpreter

import scala.concurrent.Future

class AkkaServerStubInterpreterExample extends AsyncFlatSpec with Matchers {

  it should "use custom exception handler" in {
    val server = TapirStubInterpreter[Future, Any, AkkaHttpServerOptions](UsersApi.options.serverLog(None), new FutureMonad())
      .forServerEndpoint(UsersApi.greetUser)
      .throwException(new RuntimeException("error"))
      .backend()

    sttp.client3.basicRequest
      .get(uri"http://test.com/api/users/greet")
      .send(server)
      .map(_.body shouldBe Left("failed due to error"))
  }

  it should "run greet users logic" in {
    val server = TapirStubInterpreter[Future, Any, AkkaHttpServerOptions](UsersApi.options.serverLog(None), new FutureMonad())
      .forServerEndpoint(UsersApi.greetUser)
      .runLogic()
      .backend()

    val response = sttp.client3.basicRequest
      .get(uri"http://test.com/api/users/greet")
      .header("Authorization", "Bearer secret-password")
      .send(server)

    // then
    response.map(_.body shouldBe Right("hello user123"))
  }
}

object UsersApi {

  val greetUser: Full[String, String, Unit, String, String, Any, Future] = endpoint.get
    .in("api" / "users" / "greet")
    .securityIn(auth.bearer[String]())
    .out(stringBody)
    .errorOut(stringBody)
    .serverSecurityLogic(token =>
      Future.successful {
        (if (token == "secret-password") Right("user123") else Left("unauthorized")): Either[String, String]
      }
    )
    .serverLogic(user => _ => Future.successful(Right(s"hello $user")))

  val options: CustomInterceptors[Future, AkkaHttpServerOptions] = AkkaHttpServerOptions.customInterceptors
    .exceptionHandler((ctx: ExceptionContext) =>
      Option(ValuedEndpointOutput(stringBody.and(statusCode), (s"failed due to ${ctx.e.getMessage}", StatusCode.InternalServerError)))
    )
}
