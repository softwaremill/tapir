package sttp.tapir.serverless.aws.cdk

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.amazonaws.services.lambda.runtime.Context
import io.circe.generic.auto._
import sttp.tapir.serverless.aws.lambda.AwsRequestV1

import java.io.{InputStream, OutputStream}

class IOLambdaHandlerV1 extends LambdaHandler[IO, AwsRequestV1] {
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    process(input, output).unsafeRunSync()
}
