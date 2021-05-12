package sttp.tapir.serverless.aws.lambda.tests

import cats.data.NonEmptyList
import cats.effect.IO
import org.scalatest.{Assertion, Assertions}
import sttp.client3
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{ByteArrayBody, ByteBufferBody, InputStreamBody, NoBody, Request, Response, StringBody, SttpBackend, _}
import sttp.model.{Header, StatusCode, Uri}
import sttp.tapir.Endpoint
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.stub._
import sttp.tapir.server.tests.TestServer
import sttp.tapir.serverless.aws.lambda._
import sttp.tapir.serverless.aws.lambda.tests.LambdaStubTestServer._
import sttp.tapir.tests.Test

import java.util.Base64
import scala.util.Random

class LambdaStubTestServer extends TestServer[IO, Any, Route[IO], String] {

  override def testServer[I, E, O](
      e: Endpoint[I, E, O, Any],
      testNameSuffix: String,
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[IO, String]]
  )(fn: I => IO[Either[E, O]])(runTest: (SttpBackend[IO, Any], Uri) => IO[Assertion]): Test = {
    implicit val serverOptions: AwsServerOptions[IO] = AwsServerOptions.customInterceptors(
      metricsInterceptor = metricsInterceptor,
      decodeFailureHandler = decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler)
    )
    val se: ServerEndpoint[I, E, O, Any, IO] = e.serverLogic(fn)
    val route: Route[IO] = AwsServerInterpreter.toRoute(se)
    val backend: SttpBackendStub[IO, Any] =
      SttpBackendStub(catsMonadIO)
        .whenRequestMatchesEndpointThenInterpret(
          e,
          request => route(sttpToAwsRequest(request)).map(awsToSttpResponse)
        )
        .whenAnyRequest
        .thenRespondNotFound()

    val name = e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix)

    Test(name)(runTest(backend, uri"http://localhost:3000").unsafeRunSync())
  }

  override def testServerLogic[I, E, O](e: ServerEndpoint[I, E, O, Any, IO], testNameSuffix: String)(
      runTest: (SttpBackend[IO, Any], Uri) => IO[Assertion]
  ): Test = {
    implicit val serverOptions: AwsServerOptions[IO] = AwsServerOptions.customInterceptors()
    val route: Route[IO] = AwsServerInterpreter.toRoute(e)
    val backend =
      SttpBackendStub(catsMonadIO).whenRequestMatchesEndpointThenInterpret(
        e.endpoint,
        request => {
          val awsReq = sttpToAwsRequest(request)
          route(awsReq).map { awsRes =>
            println(awsReq)
            println(awsRes)
            val sttpRes = awsToSttpResponse(awsRes)
            println(sttpRes)
            sttpRes
          }
//          route(sttpToAwsRequest(request)).map(awsToSttpResponse)
        }
      )

    val name = e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix)

    Test(name)(() => runTest(backend, uri"http://localhost:3000").unsafeRunSync())
  }

  override def testServer(name: String, rs: => NonEmptyList[Route[IO]])(runTest: (SttpBackend[IO, Any], Uri) => IO[Assertion]): Test = {
    Test("fail " + Random.nextInt())(() => Assertions.fail())
  }
}

object LambdaStubTestServer {
  implicit val catsMonadIO: CatsMonadError[IO] = new CatsMonadError[IO]

  def sttpToAwsRequest(request: Request[_, _]): AwsRequest = {
    println("CALLING: " + request.uri.toJavaUri.toString)
    AwsRequest(
      rawPath = request.uri.pathSegments.toString,
      rawQueryString = request.uri.params.toMultiSeq.foldLeft("") { case (q, (name, values)) => s"$q$name=${values.mkString(",")}" },
      headers = request.headers.map(h => h.name -> h.value).toMap,
      requestContext = AwsRequestContext(
        domainName = Some("localhost:3000"),
        http = AwsHttp(
          request.method.method,
          request.uri.path.mkString("/"),
          "http",
          "127.0.0.1",
          "Internet Explorer"
        )
      ),
      Some(request.body match {
        case NoBody                     => ""
        case StringBody(b, encoding, _) => new String(b)
        case ByteArrayBody(b, _)        => new String(b)
        case ByteBufferBody(b, _)       => new String(b.array())
        case InputStreamBody(b, _)      => new String(b.readAllBytes())
        case _                          => throw new UnsupportedOperationException
      }),
      isBase64Encoded = false
    )
  }

  def awsToSttpResponse(response: AwsResponse): Response[String] =
    client3.Response(
      new String(Base64.getDecoder.decode(response.body)),
      new StatusCode(response.statusCode),
      "",
      response.headers.map { case (n, v) => Header(n, v) }.toSeq
    )
}
