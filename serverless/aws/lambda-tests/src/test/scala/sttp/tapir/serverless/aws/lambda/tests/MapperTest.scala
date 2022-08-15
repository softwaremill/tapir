package sttp.tapir.serverless.aws.lambda.tests

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.serverless.aws.lambda._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser.decode

class MapperTest extends AnyFunSuite with Matchers {

  val input: String =
    """
      |{
      |    "resource": "/health",
      |    "path": "/health",
      |    "httpMethod": "GET",
      |    "headers": {
      |        "Accept": "*/*",
      |        "Accept-Encoding": "gzip, deflate, br"
      |    },
      |    "multiValueHeaders": {
      |        "Accept": [
      |            "*/*"
      |        ]
      |    },
      |    "queryStringParameters": null,
      |    "multiValueQueryStringParameters": null,
      |    "pathParameters": null,
      |    "stageVariables": null,
      |    "requestContext": {
      |        "resourceId": "aeagdv",
      |        "resourcePath": "/health",
      |        "httpMethod": "GET",
      |        "extendedRequestId": "WsQDiHplFiAFoyg=",
      |        "requestTime": "11/Aug/2022:09:07:47 +0000",
      |        "path": "/prod/health",
      |        "accountId": "680288336209",
      |        "protocol": "HTTP/1.1",
      |        "stage": "prod",
      |        "domainPrefix": "lsvgv5d1sb",
      |        "requestTimeEpoch": "1660208867209",
      |        "requestId": "83cd618f-b24c-43b4-aa1a-b93e5dc1c2ef",
      |        "identity": {
      |            "cognitoIdentityPoolId": null,
      |            "accountId": null,
      |            "cognitoIdentityId": null,
      |            "caller": null,
      |            "sourceIp": "91.193.208.138",
      |            "principalOrgId": null,
      |            "accessKey": null,
      |            "cognitoAuthenticationType": null,
      |            "cognitoAuthenticationProvider": null,
      |            "userArn": null,
      |            "userAgent": "PostmanRuntime/7.29.2",
      |            "user": null
      |        },
      |        "domainName": "ayeo.pl",
      |        "apiId": "lsvgv5d1sb"
      |    },
      |    "body": null,
      |    "isBase64Encoded": false
      |}
      |""".stripMargin

  test("test mapping request") {
    val v1: Either[Error, AwsRequestV1] = decode[AwsRequestV1](input)
    val v2: Either[Error, AwsRequest] = v1.map(_.toV2)

    val expected = AwsRequest(
      "/health",
      "",
      Map("Accept" -> "*/*", "Accept-Encoding" -> "gzip, deflate, br"),
      AwsRequestContext(
        Some("ayeo.pl"),
        AwsHttp("GET", "/health", "HTTP/1.1", "91.193.208.138", "PostmanRuntime/7.29.2")
      ),
      None,
      isBase64Encoded = false
    )
    assert(Right(expected).equals(v2))
  }

  test("test mapping request2") {

    val input: String =
      """
        |{
        |    "resource": "/hello",
        |    "path": "/hello",
        |    "httpMethod": "GET",
        |    "headers": {
        |        "Accept": "*/*",
        |        "Accept-Encoding": "gzip, deflate, br"
        |    },
        |    "queryStringParameters": {
        |        "name": "Julie",
        |        "age": "53"
        |    },
        |    "multiValueQueryStringParameters": null,
        |    "pathParameters": null,
        |    "stageVariables": null,
        |    "requestContext": {
        |        "resourceId": "aeagdv",
        |        "resourcePath": "/hello",
        |        "httpMethod": "GET",
        |        "protocol": "HTTP/1.1",
        |        "stage": "prod",
        |        "domainPrefix": "lsvgv5d1sb",
        |        "identity": {
        |            "sourceIp": "91.193.208.138",
        |            "userAgent": "PostmanRuntime/7.29.2",
        |            "user": null
        |        },
        |        "domainName": "ayeo.pl",
        |        "apiId": "lsvgv5d1sb"
        |    },
        |    "body": null,
        |    "isBase64Encoded": false
        |}
        |""".stripMargin

    val v1: Either[Error, AwsRequestV1] = decode[AwsRequestV1](input)
    val v2: Either[Error, AwsRequest] = v1.map(_.toV2)

    val expected = AwsRequest(
      "/hello",
      "name=Julie&age=53",
      Map("Accept" -> "*/*", "Accept-Encoding" -> "gzip, deflate, br"),
      AwsRequestContext(
        Some("ayeo.pl"),
        AwsHttp("GET", "/hello", "HTTP/1.1", "91.193.208.138", "PostmanRuntime/7.29.2")
      ),
      None,
      isBase64Encoded = false
    )
    assert(Right(expected).equals(v2))
  }
}