package sttp.tapir.serverless.aws.lambda.js

import sttp.tapir.serverless.aws.lambda.AwsResponse

import scala.scalajs.js

class AwsJsResponse(
    val statusCode: Int,
    val body: String,
    val isBase64Encoded: Boolean,
    val headers: js.Dictionary[String] = js.Dictionary(),
    val cookies: js.Array[String] = js.Array()
) extends js.Object

object AwsJsResponse {
  def fromAwsResponse(r: AwsResponse): AwsJsResponse = {
    new AwsJsResponse(
      r.statusCode,
      r.body,
      r.isBase64Encoded,
      js.Dictionary(r.headers.toList: _*)
    )
  }
}
