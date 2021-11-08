package sttp.tapir.serverless.aws.lambda.js

import sttp.tapir.serverless.aws.lambda.{AwsHttp, AwsRequest, AwsRequestContext}

import scala.scalajs.js

class AwsJsRequest(
    val rawPath: String,
    val rawQueryString: String,
    val headers: js.Dictionary[String],
    val requestContext: AwsJsRequestContext,
    val body: js.UndefOr[String],
    val isBase64Encoded: Boolean
) extends js.Object

object AwsJsRequest {
  def toAwsRequest(r: AwsJsRequest): AwsRequest = {
    AwsRequest(
      r.rawPath,
      r.rawQueryString,
      r.headers.toMap,
      AwsJsRequestContext.toAwsRequestContext(r.requestContext),
      r.body.toOption,
      r.isBase64Encoded
    )
  }
}

class AwsJsRequestContext(
    val domainName: js.UndefOr[String],
    val http: AwsJsHttp
) extends js.Object

object AwsJsRequestContext {
  def toAwsRequestContext(r: AwsJsRequestContext): AwsRequestContext = {
    AwsRequestContext(r.domainName.toOption, AwsJsHttp.toAwsHttp(r.http))
  }
}

class AwsJsHttp(
    val method: String,
    val path: String,
    val protocol: String,
    val sourceIp: String,
    val userAgent: String
) extends js.Object

object AwsJsHttp {
  def toAwsHttp(r: AwsJsHttp): AwsHttp = {
    AwsHttp(r.method, r.path, r.protocol, r.sourceIp, r.userAgent)
  }
}
