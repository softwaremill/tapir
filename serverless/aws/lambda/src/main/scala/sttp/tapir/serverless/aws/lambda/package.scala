package sttp.tapir.serverless.aws

package object lambda {
  private[lambda] type LambdaResponseBody = (String, Option[Long])
  type Route[F[_]] = AwsRequest => F[AwsResponse]

  // fixme: is this good idea to put toV2 as OPS?
  implicit final class AwsRequestOps(private val v1: AwsRequestV1) {
    def toV2: AwsRequest =
      AwsRequest(
        v1.path,
        v1.queryStringParameters match {
          case None    => ""
          case Some(x) => x.map { case (key: String, value: String) => s"$key=$value" }.mkString("&")
        },
        v1.headers,
        AwsRequestContext(
          Some(v1.requestContext.domainName),
          AwsHttp(
            v1.requestContext.httpMethod,
            v1.requestContext.resourcePath,
            v1.requestContext.protocol,
            v1.requestContext.identity.sourceIp,
            v1.requestContext.identity.userAgent
          )
        ),
        v1.body,
        v1.isBase64Encoded
      )
  }
}
