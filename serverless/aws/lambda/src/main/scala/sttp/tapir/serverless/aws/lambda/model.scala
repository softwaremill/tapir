package sttp.tapir.serverless.aws.lambda

import com.amazonaws.services.lambda.runtime.Context

import java.io.{InputStream, OutputStream}

case class AwsRequest(
    rawPath: String,
    rawQueryString: String,
    headers: Map[String, String],
    requestContext: AwsRequestContext,
    body: Option[String],
    isBase64Encoded: Boolean
)
case class AwsRequestContext(domainName: Option[String], http: AwsHttp)
case class AwsHttp(method: String, path: String, protocol: String, sourceIp: String, userAgent: String)

case class AwsResponse(cookies: List[String], isBase64Encoded: Boolean, statusCode: Int, headers: Map[String, String], body: String)

case class LambdaRuntimeContext(input: InputStream, output: OutputStream, context: Context)
