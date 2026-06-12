package sttp.tapir.server.ziopentelemetry.noop

import zio._
import zio.telemetry.opentelemetry.metrics._
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context

object NoopMeter {

  def apply(instrumentationScopeName: String): Meter = new Meter {

    override def toString(): String = instrumentationScopeName

    override def counter(
        name: String,
        unit: Option[String] = None,
        description: Option[String] = None
    )(implicit trace: Trace): UIO[Counter[Long]] = ZIO.succeed(new Counter[Long] {
      def add(value: Long, attributes: Attributes)(implicit trace: Trace): UIO[Unit] = ZIO.unit
      def inc(attributes: Attributes)(implicit trace: Trace): UIO[Unit] = ZIO.unit
      def record0(value: Long, attributes: Attributes, context: Context): Unit = ()
    })

    def histogram(name: String, unit: Option[String], description: Option[String], boundaries: Option[Chunk[Double]])(implicit
        trace: Trace
    ): UIO[Histogram[Double]] = ZIO.succeed(new Histogram[Double] {
      def record(value: Double, attributes: Attributes)(implicit trace: Trace): UIO[Unit] = ZIO.unit
      def record0(value: Double, attributes: Attributes, context: Context): Unit = ()
    })

    def observableCounter(name: String, unit: Option[String], description: Option[String])(
        callback: ObservableMeasurement[Long] => Task[Unit]
    )(implicit trace: Trace): RIO[Scope, Unit] = ZIO.unit

    def observableGauge(name: String, unit: Option[String], description: Option[String])(
        callback: ObservableMeasurement[Double] => Task[Unit]
    )(implicit trace: Trace): RIO[Scope, Unit] = ZIO.unit

    def observableUpDownCounter(name: String, unit: Option[String], description: Option[String])(
        callback: ObservableMeasurement[Long] => Task[Unit]
    )(implicit trace: Trace): RIO[Scope, Unit] = ZIO.unit

    def upDownCounter(name: String, unit: Option[String], description: Option[String])(implicit trace: Trace): UIO[UpDownCounter[Long]] =
      ZIO.succeed(new UpDownCounter[Long] {
        def add(value: Long, attributes: Attributes)(implicit trace: Trace): UIO[Unit] = ZIO.unit
        def inc(attributes: Attributes)(implicit trace: Trace): UIO[Unit] = ZIO.unit
        def dec(attributes: Attributes)(implicit trace: Trace): UIO[Unit] = ZIO.unit
        def record0(value: Long, attributes: Attributes, context: Context): Unit = ()
      })
  }
}
