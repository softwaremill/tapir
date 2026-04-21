package sttp.tapir.server.tracing.otel4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.sdk.trace.data.SpanData
import org.scalatest.compatible.Assertion
import org.typelevel.otel4s.oteljava.testkit.OtelJavaTestkit
import org.typelevel.otel4s.oteljava.testkit.trace.{SpanExpectation, TraceExpectation, TraceExpectations, TraceForestExpectation}
import org.typelevel.otel4s.semconv.attributes.{HttpAttributes, ServerAttributes, UrlAttributes}
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
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

  def testEndpointWithSpan(endpoint: ServerEndpoint[Any, IO], request: ServerRequest)(expectation: SpanExpectation): IO[Assertion] =
    OtelJavaTestkit
      // these are the default propagators when no propagators are specified via env
      // https://typelevel.org/otel4s/instrumentation/tracing-cross-service-propagation.html
      .inMemory[IO](_.addTextMapPropagators(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()))
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
          assertTrace(
            spans,
            TraceForestExpectation.unordered(
              TraceExpectation.leaf(expectation)
            )
          )
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
    )(
      SpanExpectation
        .server("GET /person")
        .noParentSpanContext
        .attributesSubset(
          HttpAttributes.HttpResponseStatusCode(200L),
          UrlAttributes.UrlPath("/person")
        )
    ).unsafeToFuture()
  }

  it should "use the rendered path template as the span name" in {
    testEndpointWithSpan(
      endpoint
        .in("person" / path[String]("name") / path[String]("surname") / "info")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[IO](_ => IO(Right("hello"))),
      serverRequestFromUri(uri"http://example.com/person/Adam/Smith/info")
    )(
      SpanExpectation
        .server("GET /person/{name}/{surname}/info")
        .noParentSpanContext
        .attributesSubset(
          HttpAttributes.HttpResponseStatusCode(200L),
          UrlAttributes.UrlPath("/person/Adam/Smith/info")
        )
    ).unsafeToFuture()
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
    )(
      SpanExpectation
        .server("GET /person")
        .noParentSpanContext
        .attributesSubset(
          ServerAttributes.ServerAddress("softwaremill.com")
        )
    ).unsafeToFuture()
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
    )(
      SpanExpectation
        .server("GET /hello")
        .where("expected trace id from traceparent header")(
          _.getTraceId == "4bf92f3577b34da6a3ce929d0e0e4736"
        )
    ).unsafeToFuture()
  }

  private def assertTrace(spans: List[SpanData], expectation: TraceForestExpectation): Assertion =
    TraceExpectations.check(spans, expectation) match {
      case Right(_) => succeed
      case Left(mismatches) =>
        fail(TraceExpectations.format(mismatches))
    }
}

object IOTestRequestBody extends RequestBody[IO, NoStreams] {
  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): IO[RawValue[R]] = ???
  override val streams: Streams[NoStreams] = NoStreams
  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = ???
}
