package sttp.tapir.server.stub

import io.circe.generic.auto._
import org.scalatest.{FlatSpec, Matchers}
import sttp.client._
import sttp.client.monad._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint

class SttpStubServerTest extends FlatSpec with Matchers {

  behavior of "SttpStubServer"
  implicit val idMonad: MonadError[Identity] = IdMonad

  it should "stub a simple endpoint with custom logic" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest")
      .get
      .out(jsonBody[ResponseWrapper])

    val endpointWithStubLogic: ServerEndpoint[Unit, Unit, ResponseWrapper, Nothing, Identity] = endpoint.serverLogic[Identity] { _ =>
      Right(ResponseWrapper(44.414))
    }

    // when
    val response = new StubbedEndpoint(endpointWithStubLogic).testUsing(())

    // then
    response shouldBe Some(Right(ResponseWrapper(44.414)))
  }

  it should "stub an endpoint with error response" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest2")
      .get
      .out(jsonBody[ResponseWrapper])
      .errorOut(statusCode and jsonBody[TestError])

    val endpointWithStubLogic: ServerEndpoint[Unit, (StatusCode, TestError), ResponseWrapper, Nothing, Identity] =
      endpoint.serverLogic[Identity] { _ => Left((StatusCode.BadRequest, ExactError("Aaargh!"))) }

    // when
    val response = new StubbedEndpoint(endpointWithStubLogic).testUsing(())

    // then
    response shouldBe Some(Left(((StatusCode.BadRequest, ExactError("Aaargh!")))))
  }

  it should "stub an endpoint with failed validation" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest3")
      .in(
        query[Int]("amount")
          .validate(Validator.min(0))
      )
      .post
      .out(jsonBody[ResponseWrapper])

    val endpointWithStubLogic: ServerEndpoint[Int, Unit, ResponseWrapper, Nothing, Identity] =
      endpoint.serverLogic[Identity] { _ => Right(ResponseWrapper(0.3)) }

    // when
    val response = new StubbedEndpoint(endpointWithStubLogic).testUsing(-1)

    // then
    response shouldBe None
  }

  it should "stub an endpoint with passing validation" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest4")
      .in(
        query[Int]("amount")
          .validate(Validator.min(0))
      )
      .post
      .out(jsonBody[ResponseWrapper])

    val endpointWithStubLogic: ServerEndpoint[Int, Unit, ResponseWrapper, Nothing, Identity] =
      endpoint.serverLogic[Identity] { in => Right(ResponseWrapper(0.33 * in)) }

    // when
    val response = new StubbedEndpoint(endpointWithStubLogic).testUsing(15)

    // then
    response shouldBe Some(Right(ResponseWrapper(4.95)))
  }
}

final case class ResponseWrapper(response: Double)

sealed trait TestError
case class ExactError(msg: String) extends TestError
