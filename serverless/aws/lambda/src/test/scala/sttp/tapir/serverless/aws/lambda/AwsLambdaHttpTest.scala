package sttp.tapir.serverless.aws.lambda

import cats.effect.{IO, Resource}
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.tests.{CreateServerTest, ServerBasicTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class AwsLambdaHttpTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: CatsMonadError[IO] = new CatsMonadError[IO]
    val interpreter = new AwsLambdaHttpTestServerInterpreter
    val createServerTest = new CreateServerTest(interpreter)
    val tests = new ServerBasicTests(backend, createServerTest, interpreter).tests()

    val handler =
  }
}
