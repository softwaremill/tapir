package sttp.tapir.serverless.aws.examples

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import com.amazonaws.services.lambda.runtime.Context
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._

import java.io.{InputStream, OutputStream}

object LambdaApiV1Example extends LambdaHandler[IO, AwsRequestV1](AwsCatsEffectServerOptions.default[IO]) {

  val helloEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("api" / "hello")
    .out(stringBody)
    .serverLogic { _ => IO.pure(s"Hello!".asRight[Unit]) }

  override protected def getAllEndpoints: List[ServerEndpoint[Any, IO]] = List(helloEndpoint)

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    process(input, output).unsafeRunSync()
  }
}
