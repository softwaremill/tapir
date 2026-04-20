package sttp.tapir.server.metrics.otel4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.traverse._
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.otel4s.oteljava.testkit.OtelJavaTestkit
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.{Gauge, Meter}
import org.typelevel.otel4s.oteljava.testkit.metrics.{MetricExpectation, MetricExpectations, PointExpectation}
import sttp.capabilities.Streams
import sttp.model.Uri._
import sttp.monad.MonadError
import sttp.tapir.{AttributeKey => _, _}
import sttp.tapir.TestUtil.serverRequestFromUri
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.TestUtil.StringToResponseBody
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interpreter._
import sttp.tapir.server.metrics.{EndpointMetric, Metric, MetricLabelsTyped}
import sttp.tapir.server.metrics.otel4s.Otel4sMetrics.{requestAttrs, responseAttrs}

import scala.util.{Success, Try}

class Otel4sMetricsTest extends AsyncFlatSpec with Matchers {
  implicit val bodyListener: BodyListener[IO, String] = new BodyListener[IO, String] {
    override def onComplete(body: String)(cb: Try[Unit] => IO[Unit]): IO[String] = cb(Success(())).map(_ => body)
  }
  implicit val ioErr: MonadError[IO] = new CatsMonadError[IO]()

  private val ioTestRequestBody = new RequestBody[IO, NoStreams] {
    override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): IO[RawValue[R]] =
      IO.pure(RawValue(null.asInstanceOf[R], Seq.empty))
    override val streams: Streams[NoStreams] = NoStreams
    override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = ???
  }

  it should "collect metrics from all successful requests" in {
    testEndpointWithMetrics(
      endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[IO](_ => IO(Right("hello"))),
      serverRequestFromUri(uri"http://example.com/person?name=Adam"), // collect metrics, counter increments
      serverRequestFromUri(uri"http://example.com/person?name=Eva"), // collect metrics, counter increments
      serverRequestFromUri(uri"http://example.com/human?name=Adam") // doesn't collect metrics
    )(
      expectedCount = 2,
      expectedStatusCode = 200,
      isFailure = false
    ).unsafeToFuture()
  }

  it should "collect metrics from successfully error requests" in {
    testEndpointWithMetrics(
      endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[IO](_ => IO(Left("Bad request"))),
      serverRequestFromUri(uri"http://example.com/person?name=Adam"), // collect metrics, counter increments
      serverRequestFromUri(uri"http://example.com/human?name=Adam") // doesn't collect metrics
    )(
      expectedCount = 1,
      expectedStatusCode = 400,
      isFailure = false
    ).unsafeToFuture()
  }

  it should "collect metrics from failed requests" in {
    testEndpointWithMetrics(
      endpoint
        .in("person")
        .in(query[String]("name"))
        .in(stringBody)
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[IO](_ => IO.raiseError(new RuntimeException("boom!"))),
      serverRequestFromUri(uri"http://example.com/person?name=Adam"),
      serverRequestFromUri(uri"http://example.com/person?name=Eva")
    )(
      expectedCount = 2,
      expectedStatusCode = 500,
      isFailure = true
    ).unsafeToFuture()
  }

  it should "collect custom metrics from all successful requests" in {
    testEndpointWithCustomMetrics(
      endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[IO](_ => IO(Right("hello"))),
      serverRequestFromUri(uri"http://example.com/person?name=Adam"), // collect metrics, counter increments
      serverRequestFromUri(uri"http://example.com/person?name=Eva"), // collect metrics, counter increments
      serverRequestFromUri(uri"http://example.com/human?name=Adam") // doesn't collect metrics
    )(
      isFailure = false
    ).unsafeToFuture()
  }

  it should "collect custom metrics from successfully error requests" in {
    testEndpointWithCustomMetrics(
      endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[IO](_ => IO(Left("Bad request"))),
      serverRequestFromUri(uri"http://example.com/person?name=Adam"), // collect metrics, counter increments
      serverRequestFromUri(uri"http://example.com/human?name=Adam") // doesn't collect metrics
    )(
      isFailure = false
    ).unsafeToFuture()
  }

  it should "collect custom metrics from failed requests" in {
    testEndpointWithCustomMetrics(
      endpoint
        .in("person")
        .in(query[String]("name"))
        .in(stringBody)
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[IO](_ => IO.raiseError(new RuntimeException("boom!"))),
      serverRequestFromUri(uri"http://example.com/person?name=Adam"),
      serverRequestFromUri(uri"http://example.com/person?name=Eva")
    )(
      isFailure = true
    ).unsafeToFuture()
  }

  private def testEndpointWithMetrics(endpoint: ServerEndpoint[Any, IO], requests: ServerRequest*)(
      expectedCount: Int,
      expectedStatusCode: Long,
      isFailure: Boolean
  ): IO[Assertion] =
    OtelJavaTestkit
      .inMemory[IO]()
      .use(testkit =>
        for {
          meter <- testkit.meterProvider.get("Test Meter")
          interpreter = new ServerInterpreter[Any, IO, String, NoStreams](
            serverEndpoints = _ => List(endpoint),
            requestBody = ioTestRequestBody,
            toResponseBody = StringToResponseBody,
            interceptors = List(
              new ExceptionInterceptor(DefaultExceptionHandler[IO]),
              Otel4sMetrics.default(meter).metricsInterceptor()
            ),
            deleteFile = _ => IO.pure(())
          )
          _ <- requests.traverse(interpreter.apply)
          metrics <- testkit.collectMetrics
        } yield {
          metrics should have size 3
          assertMetrics(
            metrics,
            List(
              requestsTotalExpectation(expectedCount, expectedStatusCode, isFailure),
              activeRequestsExpectation,
              requestDurationExpectation(expectedCount, expectedStatusCode, isFailure)
            )
          )
        }
      )

  private def testEndpointWithCustomMetrics(endpoint: ServerEndpoint[Any, IO], requests: ServerRequest*)(
      isFailure: Boolean
  ): IO[Assertion] = {
    val labels = MetricLabelsTyped[Attribute[_]](
      forRequest = List(
        { _ => Attribute.apply("custom.request.key", "value") }
      ),
      forEndpoint = Nil,
      forResponse = List(
        {
          case Right(_) => Some(Attribute.apply("custom.response.key", "value"))
          case Left(_)  => Some(Attribute.apply("custom.error.key", "value"))
        }
      )
    )
    val customGauge: Meter[IO] => Metric[IO, IO[Gauge[IO, Long]]] = meter =>
      Metric(
        metric = meter
          .gauge[Long]("my.custom.gauge")
          .withUnit("ms")
          .withDescription("My custom Gauge")
          .create,
        onRequest = (req, gaugeM, m) =>
          m.map(gaugeM) { gauge =>
            EndpointMetric()
              .onResponseBody((ep, res) => gauge.record(10, requestAttrs(labels, ep, req) ++ responseAttrs(labels, Right(res), None)))
              .onException((ep, ex) => gauge.record(11, requestAttrs(labels, ep, req) ++ responseAttrs(labels, Left(ex), None)))
          }
      )

    OtelJavaTestkit
      .inMemory[IO]()
      .use(testkit =>
        for {
          meter <- testkit.meterProvider.get("Test Meter Custom")
          interpreter = new ServerInterpreter[Any, IO, String, NoStreams](
            serverEndpoints = _ => List(endpoint),
            requestBody = ioTestRequestBody,
            toResponseBody = StringToResponseBody,
            interceptors = List(
              new ExceptionInterceptor(DefaultExceptionHandler[IO]),
              Otel4sMetrics(List(customGauge(meter))).metricsInterceptor()
            ),
            deleteFile = _ => IO.pure(())
          )
          _ <- requests.traverse(interpreter.apply)
          metrics <- testkit.collectMetrics
        } yield {
          metrics should have size 1
          assertMetrics(
            metrics,
            List(
              customGaugeExpectation(isFailure)
            )
          )
        }
      )
  }

  private def requestsTotalExpectation(expectedCount: Int, expectedStatusCode: Long, isFailure: Boolean): MetricExpectation.Numeric[Long] =
    MetricExpectation
      .sum[Long]("http.server.requests.total")
      .unit("1")
      .description("Total HTTP requests")
      .value(expectedCount.toLong)
      .pointsWhere("single requests.total point expected")(_.size == 1)
      .containsPoints(
        PointExpectation
          .numeric(expectedCount.toLong)
          .attributesSubset((baseResponseAttributes(expectedStatusCode) ++ failureAttributes(isFailure)): _*)
      )

  private val activeRequestsExpectation: MetricExpectation.Numeric[Long] =
    MetricExpectation
      .sum[Long]("http.server.active_requests")
      .unit("1")
      .description("Active HTTP requests")
      .value(0L)
      .pointsWhere("single active_requests point expected")(_.size == 1)
      .containsPoints(
        PointExpectation
          .numeric(0L)
          .attributesExact(
            Attribute("http.request.method", "GET"),
            Attribute("url.scheme", "http")
          )
      )

  private def requestDurationExpectation(expectedCount: Int, expectedStatusCode: Long, isFailure: Boolean): MetricExpectation.Histogram = {
    val base = MetricExpectation
      .histogram("http.server.request.duration")
      .unit("ms")
      .description("Duration of HTTP requests")

    if (isFailure) {
        base
          .pointCount(1)
          .containsPoints(
            PointExpectation
              .histogram
              .count(expectedCount.toLong)
              .attributesSubset((baseResponseAttributes(expectedStatusCode) ++ failureAttributes(isFailure)): _*)
          )
    } else {
      base
        .pointCount(2)
        .containsPoints(
          PointExpectation
            .histogram
            .count(expectedCount.toLong)
            .attributesSubset((baseResponseAttributes(expectedStatusCode) ++ phaseAttribute("headers")): _*)
        )
        .containsPoints(
          PointExpectation
            .histogram
            .count(expectedCount.toLong)
            .attributesSubset((baseResponseAttributes(expectedStatusCode) ++ phaseAttribute("body")): _*)
        )
    }
  }

  private def customGaugeExpectation(isFailure: Boolean): MetricExpectation.Numeric[Long] = {
    val value = if (isFailure) 11L else 10L
    val attrs =
      if (isFailure) {
        List(
          Attribute("custom.request.key", "value"),
          Attribute("custom.error.key", "value")
        )
      } else {
        List(
          Attribute("custom.request.key", "value"),
          Attribute("custom.response.key", "value")
        )
      }

    MetricExpectation
      .gauge[Long]("my.custom.gauge")
      .unit("ms")
      .description("My custom Gauge")
      .value(value)
      .pointsWhere("single custom gauge point expected")(_.size == 1)
      .containsPoints(
        PointExpectation
          .numeric(value)
          .attributesExact(attrs: _*)
      )
  }

  private def baseResponseAttributes(statusCode: Long): List[Attribute[_]] =
    List(
      Attribute("http.request.method", "GET"),
      Attribute("http.response.status_code", statusCode),
      Attribute("http.route", "/person"),
      Attribute("url.scheme", "http")
    )

  private def failureAttributes(isFailure: Boolean): List[Attribute[_]] =
    if (isFailure) List(Attribute("error.type", "java.lang.RuntimeException")) else Nil

  private def phaseAttribute(phase: String): List[Attribute[_]] =
    List(Attribute("phase", phase))

  private def assertMetrics(metrics: List[io.opentelemetry.sdk.metrics.data.MetricData], expectations: List[MetricExpectation]): Assertion =
    MetricExpectations.checkAll(metrics, expectations) match {
      case Right(_) => succeed
      case Left(mismatches) =>
        fail(MetricExpectations.format(mismatches))
    }
}
