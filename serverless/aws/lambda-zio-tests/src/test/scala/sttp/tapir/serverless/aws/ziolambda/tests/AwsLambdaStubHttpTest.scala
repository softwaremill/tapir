package sttp.tapir.serverless.aws.ziolambda.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.{ServerBasicTests, ServerMetricsTest, TestServerInterpreter}
import sttp.tapir.serverless.aws.ziolambda.{AwsZioServerInterpreter, AwsZioServerOptions}
import sttp.tapir.serverless.aws.lambda.{AwsServerOptions, Route}
import sttp.tapir.tests._
import sttp.tapir.ztapir.RIOMonadError
import zio.Task

import scala.concurrent.duration._

class AwsLambdaStubHttpTest extends TestSuite {
  override def tests: Resource[IO, List[Test]] = Resource.eval(
    IO.pure {
      import AwsLambdaStubHttpTest.m

      val createTestServer = new AwsLambdaCreateServerStubTest
      new ServerBasicTests(createTestServer, AwsLambdaStubHttpTest.testServerInterpreter, maxContentLength = false).tests() ++
        new ServerMetricsTest(createTestServer, AwsLambdaStubHttpTest.testServerInterpreter, supportsMetricsDecodeFailureCallbacks = false)
          .tests()
    }
  )
}

object AwsLambdaStubHttpTest {
  implicit val m: RIOMonadError[Any] = new RIOMonadError[Any]

  private val testServerInterpreter = new TestServerInterpreter[Task, Any, AwsServerOptions[Task], Route[Task]] {

    override def route(es: List[ServerEndpoint[Any, Task]], interceptors: Interceptors): Route[Task] = {
      val serverOptions: AwsServerOptions[Task] =
        interceptors(AwsZioServerOptions.customiseInterceptors[Any]).options.copy(encodeResponseBody = false)
      AwsZioServerInterpreter(serverOptions).toRoute(es)
    }

    override def server(
        routes: NonEmptyList[Route[Task]],
        gracefulShutdownTimeout: Option[FiniteDuration]
    ): Resource[IO, Port] = throw new UnsupportedOperationException

  }
}
