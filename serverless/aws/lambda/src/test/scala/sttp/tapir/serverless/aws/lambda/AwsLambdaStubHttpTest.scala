package sttp.tapir.serverless.aws.lambda

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.{ServerBasicTests, ServerMetricsTest, TestServerInterpreter}
import sttp.tapir.serverless.aws.lambda.AwsLambdaCreateServerStubTest.catsMonadIO
import sttp.tapir.tests.{Port, Test, TestSuite}

import scala.reflect.ClassTag

class AwsLambdaStubHttpTest extends TestSuite {
  override def tests: Resource[IO, List[Test]] = Resource.eval(
    IO.pure {
      val createTestServer = new AwsLambdaCreateServerStubTest
      new ServerBasicTests(createTestServer, AwsLambdaStubHttpTest.testServerInterpreter)(catsMonadIO).tests() ++
        new ServerMetricsTest(createTestServer).tests()
    }
  )
}

object AwsLambdaStubHttpTest {
  private val testServerInterpreter = new TestServerInterpreter[IO, Any, Route[IO]] {
    override def route[I, E, O](
        e: ServerEndpoint[I, E, O, Any, IO],
        decodeFailureHandler: Option[DecodeFailureHandler],
        metricsInterceptor: Option[MetricsRequestInterceptor[IO]]
    ): Route[IO] = {
      val serverOptions: AwsServerOptions[IO] = AwsServerOptions
        .customInterceptors[IO]
        .metricsInterceptor(metricsInterceptor)
        .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
        .options
        .copy(encodeResponseBody = false)

      AwsServerInterpreter(serverOptions).toRoute(e)
    }

    override def route[I, E, O](es: List[ServerEndpoint[I, E, O, Any, IO]]): Route[IO] =
      AwsServerInterpreter[IO]().toRoute(es)

    override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => IO[O])(implicit
        eClassTag: ClassTag[E]
    ): Route[IO] = {
      val options: AwsServerOptions[IO] = AwsServerOptions.default[IO].copy(encodeResponseBody = false)
      AwsServerInterpreter(options).toRouteRecoverErrors(e)(fn)
    }
    override def server(routes: NonEmptyList[Route[IO]]): Resource[IO, Port] = ???
  }
}
