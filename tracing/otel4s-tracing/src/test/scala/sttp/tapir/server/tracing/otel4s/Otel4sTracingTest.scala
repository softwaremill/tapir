package sttp.tapir.server.tracing.otel4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.{HttpAttributes, ServerAttributes, UrlAttributes}
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.otel4s.trace.SpanKind
import org.typelevel.otel4s.oteljava.testkit.OtelJavaTestkit
import sttp.capabilities.Streams
import sttp.model._
import sttp.model.Uri._
import sttp.model.headers.Forwarded
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.TestUtil.serverRequestFromUri
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.TestUtil.StringToResponseBody
import sttp.tapir.server.interpreter._

import scala.util.{Success, Try}

class Otel4sTracingTest extends AsyncFlatSpec with Matchers {
  implicit val bodyListener: BodyListener[IO, String] = new BodyListener[IO, String] {
    override def onComplete(body: String)(cb: Try[Unit] => IO[Unit]): IO[String] = cb(Success(())).map(_ => body)
  }
  implicit val ioErr: MonadError[IO] = new CatsMonadError[IO]()

  def testEndpointWithSpan(endpoint: ServerEndpoint[Any, IO], request: ServerRequest)(verify: SpanData => Assertion): IO[Assertion] =
    OtelJavaTestkit
      // these are the default propagators when no propagators are specified via env
      // https://typelevel.org/otel4s/instrumentation/tracing-cross-service-propagation.html
      .inMemory[IO](textMapPropagators = List(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()))
      .use(testkit =>
        for {
          tracer <- testkit.tracerProvider.get("Test Tracer")
          interpreter = new ServerInterpreter[Any, IO, String, NoStreams](
            _ => List(endpoint),
            IOTestRequestBody,
            StringToResponseBody,
            List(Otel4sTracing(Otel4sTracingConfig(tracer))),
            _ => IO.pure(())
          )
          _ <- interpreter(request)
          spans <- testkit.finishedSpans
        } yield {
          spans should have size 1
          verify(spans.head)
        }
      )

  it should "report a simple trace" in {
    testEndpointWithSpan(
      endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[IO](_ => IO(Right("hello"))),
      serverRequestFromUri(uri"http://example.com/person?name=Adam")
    ) { span =>
      span.getKind shouldBe SpanKind.Server
      span.getName shouldBe "GET /person"
      span.getAttributes.get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE) shouldBe 200L
      span.getAttributes.get(UrlAttributes.URL_PATH) shouldBe "/person"
    }.unsafeToFuture()
  }

  it should "use the rendered path template as the span name" in {
    testEndpointWithSpan(
      endpoint
        .in("person" / path[String]("name") / path[String]("surname") / "info")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[IO](_ => IO(Right("hello"))),
      serverRequestFromUri(uri"http://example.com/person/Adam/Smith/info")
    ) { span =>
      span.getKind shouldBe SpanKind.Server
      span.getName shouldBe "GET /person/{name}/{surname}/info"
      span.getAttributes.get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE) shouldBe 200L
      span.getAttributes.get(UrlAttributes.URL_PATH) shouldBe "/person/Adam/Smith/info"
    }.unsafeToFuture()
  }

  it should "use the host from the forwarded header" in {
    testEndpointWithSpan(
      endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[IO](_ => IO(Right("hello"))),
      serverRequestFromUri(
        uri"http://example.com/person?name=Adam",
        _headers = List(Header(HeaderNames.Forwarded, Forwarded(None, None, Some("softwaremill.com"), None).toString))
      )
    ) { span =>
      span.getKind shouldBe SpanKind.Server
      span.getAttributes.get(ServerAttributes.SERVER_ADDRESS) shouldBe "softwaremill.com"
    }.unsafeToFuture()
  }

  it should "extract the context attributes from the headers" in {
    testEndpointWithSpan(
      endpoint
        .in("hello")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[IO](_ => IO(Right("hello"))),
      serverRequestFromUri(
        uri"http://example.com/hello",
        _headers = List(Header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
      )
    ) { span =>
      span.getKind shouldBe SpanKind.Server
      span.getTraceId shouldBe "4bf92f3577b34da6a3ce929d0e0e4736"
    }.unsafeToFuture()
  }
}

object IOTestRequestBody extends RequestBody[IO, NoStreams] {
  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): IO[RawValue[R]] = ???
  override val streams: Streams[NoStreams] = NoStreams
  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = ???
}
