package sttp.tapir.serverless.aws.cdk.core

import cats.effect.IO
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import sttp.tapir.serverless.aws.cdk.TestEndpoints

import scala.io.Source

class ParserTest extends AnyFunSuite with Matchers {

  test("basic scenario") {

    val path = "/app-template/lib/stack-template.ts"
    val values = new StackFile(
      "API",
      "TapirHandler",
      "lambda.Runtime.JAVA_11",
      "/Users/ayeo/www/tapir/serverless/aws/cdk/target/jvm-2.13/tapir-aws-cdk.jar",
      "sttp.tapir.serverless.aws.cdk.IOLambdaHandlerV1::handleRequest",
      20,
      2048
    )

    val parser = new Parser[IO]
    val actual = parser.parse(path, values, TestEndpoints.all[IO]).getOrElse(IO.unit).unsafeRunSync()
    val expected = load("/stack.ts")

    assert(expected == actual)
  }

  private def load(path: String) =
    Source.fromInputStream(getClass.getResourceAsStream(path)).getLines().mkString("\n")
}
