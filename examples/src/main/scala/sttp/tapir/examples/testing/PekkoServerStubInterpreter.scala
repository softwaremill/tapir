// {cat=Testing; effects=Future; server=Pekko HTTP}: Test endpoints using the TapirStubInterpreter

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.22
//> using dep com.softwaremill.sttp.tapir::tapir-sttp-stub4-server:1.11.29
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.29
//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC4
//> using dep org.scalatest::scalatest:3.2.19

package sttp.tapir.examples.testing

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.*
import sttp.client4.testing.BackendStub
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.pekkohttp.PekkoHttpServerOptions
import sttp.tapir.server.interceptor.exception.ExceptionHandler
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.stub4.TapirStubInterpreter

import scala.concurrent.{ExecutionContext, Future}

class PekkoServerStubInterpreter extends AsyncFlatSpec with Matchers:

  it should "use custom exception handler" in {
    val stubBackend: Backend[Future] = TapirStubInterpreter(PekkoUsersApi.options, BackendStub.asynchronousFuture)
      .whenServerEndpoint(PekkoUsersApi.greetUser)
      .thenThrowException(new RuntimeException("error"))
      .backend()

    basicRequest
      .get(uri"http://test.com/api/users/greet")
      .send(stubBackend)
      .map(_.body shouldBe Left("failed due to error"))
  }

  it should "run greet users logic" in {
    val stubBackend: Backend[Future] = TapirStubInterpreter(PekkoUsersApi.options, BackendStub.asynchronousFuture)
      .whenServerEndpoint(PekkoUsersApi.greetUser)
      .thenRunLogic()
      .backend()

    val response = basicRequest
      .get(uri"http://test.com/api/users/greet")
      .header("Authorization", "Bearer password")
      .send(stubBackend)

    // then
    response.map(_.body shouldBe Right("hello user123"))
  }

object PekkoUsersApi:

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

  val exceptionHandler = ExceptionHandler.pure[Future](ctx =>
    Option(ValuedEndpointOutput(stringBody.and(statusCode), (s"failed due to ${ctx.e.getMessage}", StatusCode.InternalServerError)))
  )
  def options(implicit ec: ExecutionContext): CustomiseInterceptors[Future, PekkoHttpServerOptions] =
    PekkoHttpServerOptions.customiseInterceptors.exceptionHandler(exceptionHandler)
