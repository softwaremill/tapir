package sttp.tapir.serverless.aws.cdk.core

import cats.effect.IO
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxEitherId
import sttp.tapir.server.ServerEndpoint
import sttp.tapir._
import sttp.tapir.serverless.aws.cdk.test.TestEndpoints

import scala.io.Source

class ParserTest extends AnyFunSuite with Matchers {

  val path = "/app-template/lib/stack-template.ts"
  val values = StackFile(
    "API",
    "TapirHandler",
    "lambda.Runtime.JAVA_11",
    "..serverless/aws/cdk/target/jvm-2.13/tapir-aws-cdk.jar",
    "sttp.tapir.serverless.aws.cdk.test.IOLambdaHandlerV1::handleRequest",
    20,
    2048
  )
  test("basic scenario") {
    val parser = new Parser[IO]
    val actual = parser.parse(path, values, TestEndpoints.all[IO]).getOrElse(IO.unit).unsafeRunSync()
    val expected = load("/stack.ts")

    assert(expected == actual)
  }

  test("valid and invalid endpoints together") {
    val endpoints: Set[ServerEndpoint[Any, IO]] = Set(
      endpoint.trace.in("a").out(stringBody).serverLogic[IO](_ => IO.pure("trace".asRight[Unit])),
      endpoint.get.in("b").out(stringBody).serverLogic[IO](_ => IO.pure("get".asRight[Unit]))
    )
    val parser = new Parser[IO]
    val actual = parser.parse(path, values, endpoints)

    assert(actual.isRight)
  }

  test("invalid endpoint") {

    val endpoints: Set[ServerEndpoint[Any, IO]] = Set(
      endpoint.trace.in("a").out(stringBody).serverLogic[IO](_ => IO.pure("trace".asRight[Unit]))
    )
    val parser = new Parser[IO]
    val actual = parser.parse(path, values, endpoints)

    assert(actual.isLeft)
  }

  private def load(path: String) =
    Source.fromInputStream(getClass.getResourceAsStream(path)).getLines().mkString("\n")
}
