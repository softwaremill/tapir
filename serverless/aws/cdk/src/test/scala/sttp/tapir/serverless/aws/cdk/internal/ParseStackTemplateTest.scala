package sttp.tapir.serverless.aws.cdk.internal

import cats.effect._
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir._

import scala.concurrent.Future
import scala.io.Source
import scala.language.implicitConversions

class ParseStackTemplateTest extends AsyncFlatSpec with Matchers {

  implicit def ioToFuture[T](io: IO[T]): Future[T] = io.unsafeToFuture()

  private val template = Resource
    .fromAutoCloseable[IO, Source](IO.blocking(Source.fromInputStream(getClass.getResourceAsStream("/app-template/lib/stack-template.ts"))))
    .use(s => IO(s.getLines().mkString(System.lineSeparator())))

  private val stackFile = StackFile(
    apiName = "API",
    lambdaName = "TapirHandler",
    runtime = "lambda.Runtime.JAVA_11",
    jarPath = "../serverless/aws/cdk/target/jvm-2.13/tapir-aws-cdk.jar",
    handler = "sttp.tapir.serverless.aws.cdk.test.IOLambdaHandlerV1::handleRequest",
    timeout = 20,
    memorySize = 2048
  )

  it should "replace all variables with proper values" in {
    // given
    val requests: Seq[Request] = Seq(
      Request.fromEndpoint(endpoint.get.in("hello")),
      Request.fromEndpoint(endpoint.get.in("hello" / path[String]("id")))
    ).flatten

    val expectedContent =
      """import * as cdk from 'aws-cdk-lib';
        |import * as lambda from 'aws-cdk-lib/aws-lambda';
        |import * as apigw from 'aws-cdk-lib/aws-apigateway';
        |
        |export class TapirCdkStack extends cdk.Stack {
        |  constructor(scope: cdk.App, id: string, props?: cdk.StackProps) {
        |    super(scope, id, props);
        |
        |    const lambdaJar = new lambda.Function(this, 'TapirHandler', {
        |      runtime: lambda.Runtime.JAVA_11,
        |      code: lambda.Code.fromAsset('../serverless/aws/cdk/target/jvm-2.13/tapir-aws-cdk.jar'),
        |      handler: 'sttp.tapir.serverless.aws.cdk.test.IOLambdaHandlerV1::handleRequest',
        |      timeout: cdk.Duration.seconds(20),
        |      memorySize: 2048
        |    });
        |
        |    const api = new apigw.LambdaRestApi(this, 'API', {
        |      handler: lambdaJar,
        |      proxy: false
        |    });
        |
        |    // GET /hello
        |    const rootHello = api.root.addResource('hello');
        |    rootHello.addMethod('GET');
        |
        |    // GET /hello/{id}
        |    const rootHelloIdParam = rootHello.addResource('{id}');
        |    rootHelloIdParam.addMethod('GET');
        |  }
        |}""".stripMargin

    // expect
    template.flatMap { content =>
      ParseStackTemplate
        .apply[IO](content, stackFile, requests)
        .map(parsedTemplate => parsedTemplate shouldBe expectedContent)
    }
  }
}
