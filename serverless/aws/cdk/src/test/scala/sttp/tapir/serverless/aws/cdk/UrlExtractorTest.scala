package sttp.tapir.serverless.aws.cdk

import com.amazonaws.services.lambda.runtime.api.client.api._
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.tapir.serverless.aws.lambda.AwsResponse
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.collection.immutable.HashMap

class UrlExtractorTest extends AnyFunSuite with Matchers {

  // todo: test default method
  // todo: test invalid path (invalid chars)

  // fixme: use table tests
  test("basic test 1") {
    val e = endpoint.get.in("books")

    assert("books" == e.getPath.stringify)
    assert("GET" == e.getMethod)
  }

  test("basic test 2") {
    val e = endpoint.get.in("books" / path[Int]("id"))

    assert("books/{id}" == e.getPath.stringify)
    assert("GET" == e.getMethod)
  }

  test("basic test 3") { // fixme rename tests
    val e = endpoint.get.in("books" / path[Int] / path[Int])

    assert("books/{param0}/{param1}" == e.getPath.stringify)
    assert("GET" == e.getMethod)
  }

  val input2: String = //fixme: move to file?
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

  test("lambda handler") { //fixme move it out
    val output = new ByteArrayOutputStream()
    val context = new LambdaContext( //fixme mock it somehow
      1,
      1,
      "1",
      "1",
      "1",
      "1",
      new LambdaCognitoIdentity("1", "1"),
      "1",
      "1",
      new LambdaClientContext()
    )

    val handler = new IOLambdaHandlerV1
    handler.handleRequest(new ByteArrayInputStream(input2.getBytes), output, context)

    val expected = AwsResponse(
      isBase64Encoded = false,
      200, //fixme use status code VO
      HashMap("Content-Length" -> "9", "Content-Type" -> "text/plain; charset=UTF-8"),
      "Hi! Julie"
    )

    assert(expected.asJson.noSpaces == output.toString())
  }
}
