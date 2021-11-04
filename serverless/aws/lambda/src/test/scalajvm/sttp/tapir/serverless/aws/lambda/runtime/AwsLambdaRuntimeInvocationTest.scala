package sttp.tapir.serverless.aws.lambda.runtime

import cats.effect.{IO, Resource}
import cats.syntax.all._
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.model.{Header, StatusCode}
import sttp.tapir._
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.serverless.aws.lambda.runtime.AwsLambdaRuntimeInvocationTest._
import sttp.tapir.serverless.aws.lambda.{AwsCatsEffectServerInterpreter, AwsCatsEffectServerOptions, AwsServerOptions}

import scala.collection.immutable.Seq

class AwsLambdaRuntimeInvocationTest extends AnyFunSuite with Matchers {

  val nextInvocationUri = uri"http://aws/2018-06-01/runtime/invocation/next"

  test("should process event") {
    // given
    var hello = ""

    val route = AwsCatsEffectServerInterpreter(options).toRoute(testEp.serverLogic { _ =>
      hello = "hello"
      IO.pure(().asRight[Unit])
    })

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == nextInvocationUri)
      .thenRespond(Response(awsRequest, StatusCode.Ok, "Ok", Seq(Header("lambda-runtime-aws-request-id", "43214"))))
      .whenAnyRequest
      .thenRespondOk()

    // when
    val result = AwsLambdaRuntimeInvocation.handleNext(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    hello shouldBe "hello"
    result shouldBe Right(())
  }

  test("should handle error while fetching event") {
    // given
    val route = AwsCatsEffectServerInterpreter(options).toRoute(testEp)(_ => IO(().asRight[Unit]))

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == nextInvocationUri)
      .thenRespondF(_ => throw new RuntimeException)

    // when
    val result = AwsLambdaRuntimeInvocation.handleNext(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    result.isLeft shouldBe true
  }

  test("should handle decode failure") {
    // given
    val route = AwsCatsEffectServerInterpreter(options).toRoute(testEp)(_ => IO(().asRight[Unit]))

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == nextInvocationUri)
      .thenRespond(Response("???", StatusCode.Ok, "Ok", Seq(Header("lambda-runtime-aws-request-id", "43214"))))
      .whenAnyRequest
      .thenRespondOk()

    // when
    val result = AwsLambdaRuntimeInvocation.handleNext(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    result.isLeft shouldBe true
  }

  test("should handle missing lambda-runtime-aws-request-id header") {
    // given
    val route = AwsCatsEffectServerInterpreter(options).toRoute(testEp)(_ => IO(().asRight[Unit]))

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == nextInvocationUri)
      .thenRespond(Response(awsRequest, StatusCode.Ok))

    // when
    val result = AwsLambdaRuntimeInvocation.handleNext(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    result.isLeft shouldBe true
  }

  test("should handle error from server logic") {
    // given
    val route = AwsCatsEffectServerInterpreter(options).toRoute(testEp)(_ => throw new RuntimeException)

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == nextInvocationUri)
      .thenRespond(Response(awsRequest, StatusCode.Ok, "Ok", Seq(Header("lambda-runtime-aws-request-id", "43214"))))
      .whenAnyRequest
      .thenRespondOk()

    // when
    val result = AwsLambdaRuntimeInvocation.handleNext(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    result shouldBe Right(())
  }

  test("should handle error when sending response to lambda") {
    // given
    val route = AwsCatsEffectServerInterpreter(options).toRoute(testEp)(_ => IO(().asRight[Unit]))

    val backend = SttpBackendStub(monadError)
      .whenRequestMatches(_.uri == nextInvocationUri)
      .thenRespond(Response(awsRequest, StatusCode.Ok, "Ok", Seq(Header("lambda-runtime-aws-request-id", "43214"))))
      .whenAnyRequest
      .thenRespondF(_ => throw new RuntimeException)

    // when
    val result = AwsLambdaRuntimeInvocation.handleNext(route, "aws", Resource.eval(IO.pure(backend))).unsafeRunSync()

    // then
    result.isLeft shouldBe true
  }
}

object AwsLambdaRuntimeInvocationTest {
  val options: AwsServerOptions[IO] = AwsCatsEffectServerOptions.default[IO]

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
