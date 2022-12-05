package sttp.tapir.serverless.aws

import scala.language.implicitConversions

package object lambda {

  trait Mapper[A] {
    def toV2(a: A): AwsRequest
  }

  implicit val lambdaRequestMapperV1: Mapper[AwsRequestV1] = (a: AwsRequestV1) => a.toV2

  implicit val lambdaRequestMapperV2: Mapper[AwsRequest] = (a: AwsRequest) => a


  private[lambda] type LambdaResponseBody = (String, Option[Long])
  type Route[F[_]] = AwsRequest => F[AwsResponse]

  implicit def toV2(v1: AwsRequestV1): AwsRequest = v1.toV2

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
