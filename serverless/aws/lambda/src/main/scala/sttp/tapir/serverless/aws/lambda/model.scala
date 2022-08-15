package sttp.tapir.serverless.aws.lambda

import sttp.model.StatusCode

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

case class AwsResponse(isBase64Encoded: Boolean, statusCode: Int, headers: Map[String, String], body: String)

object AwsResponse {
  def badRequest(body: String = ""): AwsResponse =
    AwsResponse(isBase64Encoded = false, StatusCode.BadRequest.code, Map.empty, body)
}

/** I could NOT managed to enforce CDK to use payload version 2.0 At time of this writing no valuable
  * documentation of aws/apigateway2 exists
  * @see https://docs.aws.amazon.com/cdk/api/v1/docs/@aws-cdk_aws-apigatewayv2.PayloadFormatVersion.html
  */
case class AwsRequestV1(
    resource: String,
    path: String,
    httpMethod: String,
    queryStringParameters: Option[Map[String, String]],
    headers: Map[String, String],
    body: Option[String],
    requestContext: RequestContext,
    isBase64Encoded: Boolean
)

case class RequestContext(
    resourceId: String,
    resourcePath: String,
    httpMethod: String,
    protocol: String,
    identity: Identity,
    domainName: String,
    apiId: String
)

case class Identity(sourceIp: String, userAgent: String)
