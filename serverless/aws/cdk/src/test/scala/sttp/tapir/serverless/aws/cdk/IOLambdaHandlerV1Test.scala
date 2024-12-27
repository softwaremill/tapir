package sttp.tapir.serverless.aws.cdk

import cats.effect.IO
import com.amazonaws.services.lambda.runtime.api.client.api._
import io.circe.generic.auto._
import io.circe.parser.decode
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.serverless.aws.cdk.test.IOLambdaHandlerV1
import sttp.tapir.serverless.aws.lambda.{AwsCatsEffectServerOptions, AwsResponse}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class IOLambdaHandlerV1Test extends AnyFunSuite with Matchers {

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
      |    "multiValueHeaders": {
      |        "Accept": [
      |            "*/*"
      |        ]
      |    },
      |    "queryStringParameters": {
      |        "name": "Julie"
      |    },
      |    "multiValueQueryStringParameters": null,
      |    "pathParameters": null,
      |    "stageVariables": null,
      |    "requestContext": {
      |        "resourceId": "aeagdv",
      |        "resourcePath": "/hello",
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

  private val context = new LambdaContext(
    128,
    10,
    "requestId",
    "logGroupName",
    "logStreamName",
    "functionName",
    new LambdaCognitoIdentity("identityId", "PoolId"),
    "1.0",
    "1",
    new LambdaClientContext()
  )

  test("lambda handler without encoding") {
    val output = new ByteArrayOutputStream()

    val handler = new IOLambdaHandlerV1()
    handler.handleRequest(new ByteArrayInputStream(input.getBytes), output, context)

    val expected = AwsResponse(
      isBase64Encoded = false,
      200,
      Map("Content-Length" -> "9", "Content-Type" -> "text/plain; charset=UTF-8"),
      "Hi! Julie"
    )

    decode[AwsResponse](output.toString()) shouldBe Right(expected)
  }

  test("lambda handler with default encoding") {
    val output = new ByteArrayOutputStream()

    val handler = new IOLambdaHandlerV1(AwsCatsEffectServerOptions.default[IO])
    handler.handleRequest(new ByteArrayInputStream(input.getBytes), output, context)

    val expected = AwsResponse(
      isBase64Encoded = true,
      200,
      Map("Content-Length" -> "9", "Content-Type" -> "text/plain; charset=UTF-8"),
      "SGkhIEp1bGll"
    )

    decode[AwsResponse](output.toString()) shouldBe Right(expected)
  }
}
