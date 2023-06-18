package sttp.tapir.serverless.aws.lambda.zio.tests

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe.generic.auto._
import sttp.tapir.serverless.aws.lambda.AwsRequest
import sttp.tapir.serverless.aws.lambda.zio.ZioLambdaHandler
import sttp.tapir.ztapir.RIOMonadError
import zio.{Runtime, Unsafe}

import java.io.{InputStream, OutputStream}

class ZioLambdaHandlerImpl extends RequestStreamHandler {
  private implicit val m = new RIOMonadError[Any]
  private val handler = ZioLambdaHandler.default[Any](allEndpoints.toList)

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val runtime = Runtime.default
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(handler.process[AwsRequest](input, output)).getOrThrowFiberFailure()
    }
  }
}
