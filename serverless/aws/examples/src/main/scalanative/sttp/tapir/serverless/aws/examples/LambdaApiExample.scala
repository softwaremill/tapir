package sttp.tapir.serverless.aws.examples

import cats.effect.IO
import cats.syntax.all._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda.runtime._

object LambdaRuntime extends AwsLambdaIORuntime {
  val helloEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("api" / "hello")
    .out(stringBody)
    .serverLogic { _ => IO.pure(s"Hello!".asRight[Unit]) }

  override val endpoints = Seq(helloEndpoint)
}
