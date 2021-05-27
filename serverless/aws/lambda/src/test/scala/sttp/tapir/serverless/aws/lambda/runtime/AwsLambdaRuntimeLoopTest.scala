package sttp.tapir.serverless.aws.lambda.runtime

import cats.effect.{ContextShift, IO, Resource}
import cats.syntax.all._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.model.{Header, StatusCode}
import sttp.tapir._
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.serverless.aws.lambda.runtime.AwsLambdaRuntimeLoopTest._
import sttp.tapir.serverless.aws.lambda.{AwsCatsEffectServerInterpreter, AwsServerOptions}

import scala.concurrent.ExecutionContext.Implicits.global

class AwsLambdaRuntimeLoopTest extends AnyFunSuite with Matchers {

  test("should process event") {
    // given
    var hello = ""

    val route = AwsCatsEffectServerInterpreter.toRoute(testEp.serverLogic { _ =>
      hello = "hello"
      IO.pure(().asRight[Unit])
    })

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == uri"http://aws/2018-06-01/runtime/invocation/next")
      .thenRespondF(IO.pure(Response(awsRequest, StatusCode.Ok, "Ok", Seq(Header("lambda-runtime-aws-request-id", "43214")))))
      .whenAnyRequest
      .thenRespondOk()

    // when
    val result = AwsLambdaRuntimeLoop(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    hello shouldBe "hello"
    result shouldBe Right(())
  }

  test("should handle error while fetching event") {
    // given
    val route = AwsCatsEffectServerInterpreter.toRoute(testEp)(_ => IO(().asRight[Unit]))

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == uri"http://aws/2018-06-01/runtime/invocation/next")
      .thenRespondF(_ => throw new RuntimeException)

    val loop = AwsLambdaRuntimeLoop(route, "aws", Resource.eval(IO.pure(backend)))

    // when
    val result = AwsLambdaRuntimeLoop(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    result.isLeft shouldBe true
  }

  test("should handle decode failure") {
    // given
    val route = AwsCatsEffectServerInterpreter.toRoute(testEp)(_ => IO(().asRight[Unit]))

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == uri"http://aws/2018-06-01/runtime/invocation/next")
      .thenRespondF(IO.pure(Response("???", StatusCode.Ok, "Ok", Seq(Header("lambda-runtime-aws-request-id", "43214")))))
      .whenAnyRequest
      .thenRespondOk()

    // when
    val result = AwsLambdaRuntimeLoop(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    result.isLeft shouldBe true
  }

  test("should handle missing lambda-runtime-aws-request-id header") {
    // given
    val route = AwsCatsEffectServerInterpreter.toRoute(testEp)(_ => IO(().asRight[Unit]))

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == uri"http://aws/2018-06-01/runtime/invocation/next")
      .thenRespondF(IO.pure(Response(awsRequest, StatusCode.Ok)))

    // when
    val result = AwsLambdaRuntimeLoop(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    result.isLeft shouldBe true
  }

  test("should handle error from server logic") {
    // given
    val route = AwsCatsEffectServerInterpreter.toRoute(testEp)(_ => throw new RuntimeException)

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == uri"http://aws/2018-06-01/runtime/invocation/next")
      .thenRespondF(IO.pure(Response(awsRequest, StatusCode.Ok, "Ok", Seq(Header("lambda-runtime-aws-request-id", "43214")))))
      .whenAnyRequest
      .thenRespondOk()

    // when
    val result = AwsLambdaRuntimeLoop(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    result shouldBe Right(())
  }

  test("should handle error when sending response to lambda") {
    // given
    val route = AwsCatsEffectServerInterpreter.toRoute(testEp)(_ => IO(().asRight[Unit]))

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == uri"http://aws/2018-06-01/runtime/invocation/next")
      .thenRespondF(IO.pure(Response(awsRequest, StatusCode.Ok, "Ok", Seq(Header("lambda-runtime-aws-request-id", "43214")))))
      .whenAnyRequest
      .thenRespondF(_ => throw new RuntimeException)

    // when
    val result = AwsLambdaRuntimeLoop(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    result.isLeft shouldBe true
  }
}

object AwsLambdaRuntimeLoopTest {
  implicit val contextShift: ContextShift[IO] = IO.contextShift(global)
  implicit val options: AwsServerOptions[IO] = AwsServerOptions.customInterceptors()

  val awsRequest: String =
    """
      |{
      |    "version": "2.0",
      |    "routeKey": "GET /api/hello",
      |    "rawPath": "/api/hello",
      |    "rawQueryString": "",
      |    "headers": {},
      |    "requestContext": {
      |        "http": {
      |            "method": "GET",
      |            "path": "/api/hello",
      |            "protocol": "HTTP/1.1",
      |            "sourceIp": "188.146.66.23",
      |            "userAgent": "Chrome"
      |        }
      |    },
      |    "isBase64Encoded": false
      |}
      |""".stripMargin

  val testEp: Endpoint[Unit, Unit, Unit, Any] = endpoint.get.in("api" / "hello")

  val monadError: CatsMonadError[IO] = new CatsMonadError[IO]
}
