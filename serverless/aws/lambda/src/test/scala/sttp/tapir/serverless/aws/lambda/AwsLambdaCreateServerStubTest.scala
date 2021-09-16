package sttp.tapir.serverless.aws.lambda

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.Assertion
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{ByteArrayBody, ByteBufferBody, InputStreamBody, NoBody, Request, Response, StringBody, SttpBackend, _}
import sttp.model.{Header, StatusCode, Uri}
import sttp.tapir.Endpoint
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.CreateServerTest
import sttp.tapir.serverless.aws.lambda.AwsLambdaCreateServerStubTest._
import sttp.tapir.tests.Test

class AwsLambdaCreateServerStubTest extends CreateServerTest[IO, Any, Route[IO]] {

  override def testServer[I, E, O](
      e: Endpoint[I, E, O, Any],
      testNameSuffix: String,
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[IO]]
  )(fn: I => IO[Either[E, O]])(runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]): Test = {
    val serverOptions: AwsServerOptions[IO] = AwsServerOptions
      .customInterceptors[IO]
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
      .options
      .copy(encodeResponseBody = false)
    val se: ServerEndpoint[I, E, O, Any, IO] = e.serverLogic(fn)
    val route: Route[IO] = AwsCatsEffectServerInterpreter(serverOptions).toRoute(se)
    val name = e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix)
    Test(name)(runTest(stubBackend(route), uri"http://localhost:3000").unsafeToFuture())
  }

  override def testServerLogic[I, E, O](e: ServerEndpoint[I, E, O, Any, IO], testNameSuffix: String)(
      runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test = {
    val serverOptions: AwsServerOptions[IO] = AwsServerOptions.default[IO].copy(encodeResponseBody = false)
    val route: Route[IO] = AwsCatsEffectServerInterpreter(serverOptions).toRoute(e)
    val name = e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix)
    Test(name)(runTest(stubBackend(route), uri"http://localhost:3000").unsafeToFuture())
  }

  override def testServer(name: String, rs: => NonEmptyList[Route[IO]])(
      runTest: (SttpBackend[IO, Fs2Streams[IO] with WebSockets], Uri) => IO[Assertion]
  ): Test = {
    val backend = SttpBackendStub[IO, Fs2Streams[IO] with WebSockets](catsMonadIO).whenAnyRequest
      .thenRespondF { request =>
        val responses: NonEmptyList[Response[String]] = rs.map { route =>
          route(sttpToAwsRequest(request)).map(awsToSttpResponse).unsafeRunSync()
        }
        IO.pure(responses.find(_.code != StatusCode.NotFound).getOrElse(Response("", StatusCode.NotFound)))
      }
    Test(name)(runTest(backend, uri"http://localhost:3000").unsafeToFuture())
  }

  private def stubBackend(route: Route[IO]): SttpBackend[IO, Fs2Streams[IO] with WebSockets] =
    SttpBackendStub[IO, Fs2Streams[IO] with WebSockets](catsMonadIO).whenAnyRequest.thenRespondF { request =>
      route(sttpToAwsRequest(request)).map(awsToSttpResponse)
    }
}

object AwsLambdaCreateServerStubTest {
  implicit val catsMonadIO: CatsMonadError[IO] = new CatsMonadError[IO]

  def sttpToAwsRequest(request: Request[_, _]): AwsRequest = {
    AwsRequest(
      rawPath = request.uri.pathSegments.toString,
      rawQueryString = request.uri.params.toMultiSeq.foldLeft("") { case (q, (name, values)) =>
        s"${if (q == "") "" else s"$q&"}${if (values.isEmpty) name else values.map(v => s"$name=$v").mkString("&")}"
      },
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
        case NoBody                => ""
        case StringBody(b, _, _)   => new String(b)
        case ByteArrayBody(b, _)   => new String(b)
        case ByteBufferBody(b, _)  => new String(b.array())
        case InputStreamBody(b, _) => new String(b.readAllBytes())
        case _                     => throw new UnsupportedOperationException
      }),
      isBase64Encoded = false
    )
  }

  def awsToSttpResponse(response: AwsResponse): Response[String] =
    client3.Response(
      new String(response.body),
      new StatusCode(response.statusCode),
      "",
      response.headers
        .map { case (n, v) => v.split(",").map(Header(n, _)) }
        .flatten
        .toSeq
        .asInstanceOf[scala.collection.immutable.Seq[Header]]
    )
}
