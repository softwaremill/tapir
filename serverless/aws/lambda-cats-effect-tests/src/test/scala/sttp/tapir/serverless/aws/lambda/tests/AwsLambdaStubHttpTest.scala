package sttp.tapir.serverless.aws.lambda.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.{ServerBasicTests, ServerMetricsTest, TestServerInterpreter}
import sttp.tapir.serverless.aws.lambda._
import sttp.tapir.serverless.aws.lambda.tests.AwsLambdaCreateServerStubTest.catsMonadIO
import sttp.tapir.tests.{Port, Test, TestSuite}

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
  private val testServerInterpreter = new TestServerInterpreter[IO, Any, AwsServerOptions[IO], Route[IO]] {

    override def route(es: List[ServerEndpoint[Any, IO]], interceptors: Interceptors): Route[IO] = {
      val serverOptions: AwsServerOptions[IO] =
        interceptors(AwsCatsEffectServerOptions.customiseInterceptors[IO]).options.copy(encodeResponseBody = false)
      AwsCatsEffectServerInterpreter(serverOptions).toRoute(es)
    }

    override def server(routes: NonEmptyList[Route[IO]]): Resource[IO, Port] = ???
  }
}
