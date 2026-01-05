package sttp.tapir.serverless.aws.cdk.tests

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.amazonaws.services.lambda.runtime.Context
import io.circe.generic.auto._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._

import java.io.{InputStream, OutputStream}

/** Used by [[AwsCdkAppTemplate]] for integration tests
  */
object CdkTestLambdaHandler extends LambdaHandler[IO, AwsRequestV1](AwsCatsEffectServerOptions.noEncoding[IO]) {
  override protected def getAllEndpoints: List[ServerEndpoint[Any, IO]] = allEndpoints.toList

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    process(input, output).unsafeRunSync()
  }
}
