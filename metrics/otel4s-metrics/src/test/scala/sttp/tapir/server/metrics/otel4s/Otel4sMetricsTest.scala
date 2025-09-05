package sttp.tapir.server.metrics.otel4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.traverse._
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.sdk.metrics.data.{HistogramPointData, LongPointData, MetricData, MetricDataType}
import io.opentelemetry.semconv.{ErrorAttributes, HttpAttributes, UrlAttributes}
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.otel4s.oteljava.testkit.OtelJavaTestkit
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter
import sttp.capabilities.Streams
import sttp.model._
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

import scala.jdk.CollectionConverters._
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
      .inMemory[IO](textMapPropagators = List(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()))
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
          metrics <- testkit.collectMetrics[MetricData]
        } yield {
          metrics should have size 3

          // asserts for metric "http.server.requests.total"
          val requestsTotal = metrics.find(_.getName == "http.server.requests.total").head
          requestsTotal.getType shouldBe MetricDataType.LONG_SUM
          requestsTotal.getUnit shouldBe "1"
          requestsTotal.getDescription shouldBe "Total HTTP requests"

          val requestsTotalData = requestsTotal.getData.getPoints.asScala.toList.asInstanceOf[List[LongPointData]].head
          requestsTotalData.getValue shouldBe expectedCount
          if (isFailure) {
            requestsTotalData.getAttributes shouldBe
              Attributes
                .builder()
                .put(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, expectedStatusCode)
                .put(HttpAttributes.HTTP_ROUTE, "/person")
                .put(UrlAttributes.URL_SCHEME, "http")
                .put(ErrorAttributes.ERROR_TYPE, "java.lang.RuntimeException")
                .build()
          } else {
            requestsTotalData.getAttributes shouldBe
              Attributes
                .builder()
                .put(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, expectedStatusCode)
                .put(HttpAttributes.HTTP_ROUTE, "/person")
                .put(UrlAttributes.URL_SCHEME, "http")
                .build()
          }

          // asserts for metric "http.server.active_requests"
          val activeRequests = metrics.find(_.getName == "http.server.active_requests").head
          activeRequests.getType shouldBe MetricDataType.LONG_SUM
          activeRequests.getUnit shouldBe "1"
          activeRequests.getDescription shouldBe "Active HTTP requests"

          val activeRequestsData = activeRequests.getData.getPoints.asScala.toList.asInstanceOf[List[LongPointData]].head
          activeRequestsData.getValue shouldBe 0
          activeRequestsData.getAttributes.size() shouldBe 3
          activeRequestsData.getAttributes shouldBe
            Attributes
              .builder()
              .put(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
              .put(HttpAttributes.HTTP_ROUTE, "/person")
              .put(UrlAttributes.URL_SCHEME, "http")
              .build()

          // asserts for metric "http.server.request.duration"
          val requestDuration = metrics.find(_.getName == "http.server.request.duration").head
          requestDuration.getType shouldBe MetricDataType.HISTOGRAM
          requestDuration.getUnit shouldBe "ms"
          requestDuration.getDescription shouldBe "Duration of HTTP requests"

          val requestDurationData = requestDuration.getData.getPoints.asScala.toList.asInstanceOf[List[HistogramPointData]]
          if (isFailure) {
            requestDurationData.map(_.getCount) shouldBe List(expectedCount)
            requestDurationData.map(_.getAttributes) shouldBe List(
              Attributes
                .builder()
                .put(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, expectedStatusCode)
                .put(HttpAttributes.HTTP_ROUTE, "/person")
                .put(UrlAttributes.URL_SCHEME, "http")
                .put(ErrorAttributes.ERROR_TYPE, "java.lang.RuntimeException")
                .build()
            )
          } else {
            requestDurationData.map(_.getCount) shouldBe List(expectedCount, expectedCount)
            requestDurationData.map(_.getAttributes) should contain theSameElementsAs List(
              Attributes
                .builder()
                .put(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, expectedStatusCode)
                .put(HttpAttributes.HTTP_ROUTE, "/person")
                .put(UrlAttributes.URL_SCHEME, "http")
                .put(AttributeKey.stringKey("phase"), "headers")
                .build(),
              Attributes
                .builder()
                .put(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, expectedStatusCode)
                .put(HttpAttributes.HTTP_ROUTE, "/person")
                .put(UrlAttributes.URL_SCHEME, "http")
                .put(AttributeKey.stringKey("phase"), "body")
                .build()
            )
          }
        }
      )

  private def testEndpointWithCustomMetrics(endpoint: ServerEndpoint[Any, IO], requests: ServerRequest*)(
      isFailure: Boolean
  ): IO[Assertion] = {
    val labels = MetricLabelsTyped[Attribute[_]](
      forRequest = List(
        { (_, _) => Attribute.apply("custom.request.key", "value") }
      ),
      forResponse = List(
        {
          case Right(r) => Some(Attribute.apply("custom.response.key", "value"))
          case Left(ex) => Some(Attribute.apply("custom.error.key", "value"))
        }
      )
    )
    val customGauge: Meter[IO] => Metric[IO, _] = meter =>
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
      .inMemory[IO](textMapPropagators = List(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()))
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
          metrics <- testkit.collectMetrics[MetricData]
        } yield {
          metrics should have size 1

          // asserts for metric "my.custom.gauge"
          val requestsTotal = metrics.find(_.getName == "my.custom.gauge").head
          requestsTotal.getType shouldBe MetricDataType.LONG_GAUGE
          requestsTotal.getUnit shouldBe "ms"
          requestsTotal.getDescription shouldBe "My custom Gauge"

          val requestsTotalData = requestsTotal.getData.getPoints.asScala.toList.asInstanceOf[List[LongPointData]].head

          if (isFailure) {
            requestsTotalData.getValue shouldBe 11
            requestsTotalData.getAttributes shouldBe
              Attributes
                .builder()
                .put("custom.request.key", "value")
                .put("custom.error.key", "value")
                .build()
          } else {
            requestsTotalData.getValue shouldBe 10
            requestsTotalData.getAttributes shouldBe
              Attributes
                .builder()
                .put("custom.request.key", "value")
                .put("custom.response.key", "value")
                .build()
          }
        }
      )
  }
}
