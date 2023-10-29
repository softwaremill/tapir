package sttp.tapir.server.metrics.zio

import sttp.tapir.TestUtil._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.TestUtil._
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.metrics.zio.ZioMetrics.DefaultNamespace
import zio._
import zio.metrics.Metric.{Counter, Gauge}
import zio.metrics._
import zio.test._

object ZioMetricsTest extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] = {
    suite("ZioMetrics")(
      test("can collect requests active") {

        val serverEp = PersonsApi { name =>
          Thread.sleep(100)
          PersonsApi.defaultLogic(name)
        }.serverEp
        val metrics: ZioMetrics[Id] = ZioMetrics(DefaultNamespace, List.empty).addRequestsActive()
        val interpreter =
          new ServerInterpreter[Any, Id, Unit, NoStreams](
            _ => List(serverEp),
            TestRequestBody,
            UnitToResponseBody,
            List(metrics.metricsInterceptor(), new DecodeFailureInterceptor(DefaultDecodeFailureHandler[Id])),
            _ => ()
          )

        // when
        val active: Gauge[Long] = ZioMetrics
          .getActiveRequestGauge("tapir")
          .tagged(
            Set(MetricLabel("path", "/person"), MetricLabel("method", "GET"))
          )

        for {
          _ <- ZIO
            .succeed({
              interpreter.apply(PersonsApi.request("Jacob"))
            })
            .fork
          _ <- ZIO.succeed(Thread.sleep(100))
          state <- active.value
          _ <- ZIO.succeed(Thread.sleep(150))
          state2 <- active.value
        } yield assertTrue(state == MetricState.Gauge(1)) && assertTrue(state2 == MetricState.Gauge(0))

      } @@ TestAspect.retry(Schedule.recurs(5)),
      test("can collect requests total") {

        val serverEp = PersonsApi { name =>
          PersonsApi.defaultLogic(name)
        }.serverEp
        val metrics: ZioMetrics[Id] = ZioMetrics(DefaultNamespace, List.empty).addRequestsTotal()
        val interpreter =
          new ServerInterpreter[Any, Id, Unit, NoStreams](
            _ => List(serverEp),
            TestRequestBody,
            UnitToResponseBody,
            List(metrics.metricsInterceptor(), new DecodeFailureInterceptor(DefaultDecodeFailureHandler[Id])),
            _ => ()
          )

        // when
        val counter: Counter[Long] = ZioMetrics
          .getRequestsTotalCounter("tapir")
          .tagged(
            Set(MetricLabel("path", "/person"), MetricLabel("method", "GET"), MetricLabel("status", "2xx"))
          )

        val missedCounter: Counter[Long] = ZioMetrics
          .getRequestsTotalCounter("tapir")
          .tagged(
            Set(MetricLabel("path", "/person"), MetricLabel("method", "GET"), MetricLabel("status", "4xx"))
          )
        for {
          _ <- ZIO.succeed({
            interpreter.apply(PersonsApi.request("Jacob"))
            interpreter.apply(PersonsApi.request("Jacob"))
            interpreter.apply(PersonsApi.request("Jacob"))
            interpreter.apply(PersonsApi.request(""))
            interpreter.apply(PersonsApi.request("Ace"))
          })
          state <- counter.value
          stateMissed <- missedCounter.value
        } yield assertTrue(state == MetricState.Counter(3d)) &&
          assertTrue(stateMissed == MetricState.Counter(2d))
      },
      test("can collect requests duration") {
        val serverEp = PersonsApi { name =>
          PersonsApi.defaultLogic(name)
        }.serverEp
        val metrics: ZioMetrics[Id] = ZioMetrics(DefaultNamespace, List.empty).addRequestsDuration()
        val interpreter =
          new ServerInterpreter[Any, Id, Unit, NoStreams](
            _ => List(serverEp),
            TestRequestBody,
            UnitToResponseBody,
            List(metrics.metricsInterceptor(), new DecodeFailureInterceptor(DefaultDecodeFailureHandler[Id])),
            _ => ()
          )

        // when
        val histogram: Metric[MetricKeyType.Histogram, Double, MetricState.Histogram] = ZioMetrics
          .getRequestDurationHistogram("tapir")
          .tagged(
            Set(MetricLabel("path", "/person"), MetricLabel("method", "GET"), MetricLabel("status", "2xx"), MetricLabel("phase", "headers"))
          )

        for {
          _ <- ZIO.succeed({
            interpreter.apply(PersonsApi.request("Jacob"))
          })
          state <- histogram.value
        } yield assertTrue(state.buckets.exists(_._2 > 0L)) &&
          assertTrue(state.count == 1L) &&
          assertTrue(state.min > 0d) &&
          assertTrue(state.max > 0d)
      }
    )
  }
}
