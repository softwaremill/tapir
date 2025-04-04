package sttp.tapir.serverless.aws.ziolambda.tests

import _root_.zio.{Runtime, Task, Unsafe}
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.Assertion
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.impl.cats.CatsMonadError
import sttp.client4.testing.{ResponseStub, StubBody, WebSocketStreamBackendStub}
import sttp.client4._
import sttp.model.{Header, StatusCode, Uri}
import sttp.tapir.PublicEndpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.CreateServerTest
import sttp.tapir.serverless.aws.lambda._
import sttp.tapir.serverless.aws.ziolambda.{AwsZioServerInterpreter, AwsZioServerOptions}
import sttp.tapir.serverless.aws.ziolambda.tests.AwsLambdaCreateServerStubTest.{awsToSttpResponse, sttpToAwsRequest}
import sttp.tapir.tests._
import sttp.tapir.ztapir.RIOMonadError

import scala.concurrent.duration._

class AwsLambdaCreateServerStubTest extends CreateServerTest[Task, Any, AwsServerOptions[Task], Route[Task]] {

  private implicit val m: RIOMonadError[Any] = new RIOMonadError[Any]

  override def testServer[I, E, O](e: PublicEndpoint[I, E, O, Any], testNameSuffix: String, interceptors: Interceptors = identity)(
      fn: I => Task[Either[E, O]]
  )(runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]): Test = {
    val serverOptions: AwsServerOptions[Task] = interceptors(AwsZioServerOptions.customiseInterceptors[Any]).options
      .copy(encodeResponseBody = false)
    val se: ServerEndpoint[Any, Task] = e.serverLogic(fn)
    val interpreter = AwsZioServerInterpreter(serverOptions)
    val route: Route[Task] = interpreter.toRoute(se)
    val name = e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix)

    Test(name)(runTest(stubBackend(transformMonad(route)), uri"http://localhost:3002").unsafeToFuture())
  }

  def testServerWithStop(name: String, rs: => NonEmptyList[Route[Task]], gracefulShutdownTimeout: Option[FiniteDuration])(
      runTest: KillSwitch => (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = throw new UnsupportedOperationException

  override def testServerLogic(e: ServerEndpoint[Any, Task], testNameSuffix: String, interceptors: Interceptors = identity)(
      runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = {
    val serverOptions: AwsServerOptions[Task] = interceptors(AwsZioServerOptions.customiseInterceptors[Any]).options
      .copy(encodeResponseBody = false)
    val interpreter = AwsZioServerInterpreter(serverOptions)
    val route: Route[Task] = interpreter.toRoute(e)
    val name = e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix)
    Test(name)(runTest(stubBackend(transformMonad(route)), uri"http://localhost:3002").unsafeToFuture())
  }

  override def testServerLogicWithStop(
      e: ServerEndpoint[Any, Task],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity,
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  )(
      runTest: KillSwitch => (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = throw new java.lang.UnsupportedOperationException

  override def testServer(name: String, rs: => NonEmptyList[Route[Task]])(
      runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = {
    val backend = WebSocketStreamBackendStub[IO, Fs2Streams[IO]](AwsLambdaCreateServerStubTest.catsMonadIO).whenAnyRequest
      .thenRespondF { request =>
        val responses: NonEmptyList[Response[StubBody]] = rs.map { route =>
          transformMonad(route)(sttpToAwsRequest(request)).map(awsToSttpResponse).unsafeRunSync()
        }
        IO.pure(responses.find(_.code != StatusCode.NotFound).getOrElse(ResponseStub.adjust("", StatusCode.NotFound)))
      }
    Test(name)(runTest(backend, uri"http://localhost:3002").unsafeToFuture())
  }

  private def stubBackend(route: Route[IO]): WebSocketStreamBackend[IO, Fs2Streams[IO]] =
    WebSocketStreamBackendStub[IO, Fs2Streams[IO]](AwsLambdaCreateServerStubTest.catsMonadIO).whenAnyRequest.thenRespondF { request =>
      route(sttpToAwsRequest(request)).map(awsToSttpResponse)
    }

  private def transformMonad(route: Route[Task]): Route[IO] = r =>
    Unsafe.unsafe { implicit unsafe =>
      IO.fromFuture(IO(Runtime.default.unsafe.runToFuture(route(r))))
    }

}

object AwsLambdaCreateServerStubTest {
  implicit val catsMonadIO: CatsMonadError[IO] = new CatsMonadError[IO]

  def sttpToAwsRequest(request: GenericRequest[_, _]): AwsRequest = {
    AwsRequest(
      rawPath = request.uri.pathSegments.toString,
      rawQueryString = request.uri.params.toMultiSeq.foldLeft("") { case (q, (name, values)) =>
        s"${if (q == "") "" else s"$q&"}${if (values.isEmpty) name else values.map(v => s"$name=$v").mkString("&")}"
      },
      headers = request.headers.map(h => h.name -> h.value).toMap,
      requestContext = AwsRequestContext(
        domainName = Some("localhost:3002"),
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

  def awsToSttpResponse(response: AwsResponse): Response[StubBody] =
    ResponseStub.adjust(
      new String(response.body),
      new StatusCode(response.statusCode),
      response.headers
        .map { case (n, v) => v.split(",").map(Header(n, _)) }
        .flatten
        .toSeq
        .asInstanceOf[scala.collection.immutable.Seq[Header]]
    )
}
