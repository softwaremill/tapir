package sttp.tapir.serverless.aws.lambda.tests

import cats.effect.{IO, Resource}
import sttp.tapir.server.tests.ServerBasicTests
import sttp.tapir.serverless.aws.lambda.Route
import sttp.tapir.serverless.aws.lambda.tests.LambdaStubTestServer._
import sttp.tapir.tests.{Test, TestSuite}

class AwsLambdaStubHttpTest extends TestSuite {
  override def tests: Resource[IO, List[Test]] = Resource.eval {
    IO.pure {
      val interpreter = new AwsLambdaTestServerInterpreter
      val createTestServer = new LambdaStubTestServer
      new ServerBasicTests[IO, Route[IO], String](createTestServer, interpreter)(catsMonadIO).tests()
    }
  }
}
