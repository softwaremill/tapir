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

/**
  * As for this moment, CDK v2 does not provide high level typescript classes for generating stack for Api Gateway v2 with Lambda, this
  * is why we need to use Api Gateway v1, and translate it's request to v2 by hand.
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
) {
  def toV2: AwsRequest =
    AwsRequest(
      path,
      queryStringParameters match {
        case None => ""
        case Some(x) => x.map { case (key: String, value: String) => s"$key=$value" }.mkString("&")
      },
      headers,
      AwsRequestContext(
        Some(requestContext.domainName),
        AwsHttp(
          requestContext.httpMethod,
          requestContext.resourcePath,
          requestContext.protocol,
          requestContext.identity.sourceIp,
          requestContext.identity.userAgent
        )
      ),
      body,
      isBase64Encoded
    )
}

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
