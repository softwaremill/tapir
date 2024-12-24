package sttp.tapir.serverless.aws.lambda.tests

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.amazonaws.services.lambda.runtime.Context
import io.circe.generic.auto._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda.{AwsRequest, LambdaHandler}
import java.io.{InputStream, OutputStream}

class IOLambdaHandlerV2(options: AwsServerOptions[IO]) extends LambdaHandler[IO, AwsRequest](options) {

  override protected def getAllEndpoints: List[ServerEndpoint[Any, IO]] = allEndpoints.toList

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    process(input, output).unsafeRunSync()
}
