package sttp.tapir.server.ziopentelemetry.noop

import zio.telemetry.opentelemetry.tracing.Tracing
import zio._
import io.opentelemetry.api.common.Attributes
import zio.telemetry.opentelemetry.context.IncomingContextCarrier
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import zio.telemetry.opentelemetry.tracing.StatusMapper
import zio.telemetry.opentelemetry.tracing.propagation.TraceContextPropagator
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import zio.telemetry.opentelemetry.context.OutgoingContextCarrier
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import zio.telemetry.opentelemetry.common.Attribute
import io.opentelemetry.api.common.AttributeKey

object NoopTracing {
  def apply(instrumentationScopeName: String): Tracing = new Tracing {

    override def toString(): String = instrumentationScopeName

    def addEvent(name: String)(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def addEventWithAttributes(name: String, attributes: Attributes)(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def extractSpan[C, R, E, E1 <: E, A, A1 <: A](
        propagator: TraceContextPropagator,
        carrier: IncomingContextCarrier[C],
        spanName: String,
        spanKind: SpanKind,
        attributes: Attributes,
        statusMapper: StatusMapper[E, A],
        links: Seq[SpanContext]
    )(zio: => ZIO[R, E1, A1])(implicit trace: Trace): ZIO[R, E1, A1] =
      zio

    def extractSpanUnsafe[C](
        propagator: TraceContextPropagator,
        carrier: IncomingContextCarrier[C],
        spanName: String,
        spanKind: SpanKind,
        attributes: Attributes,
        links: Seq[SpanContext]
    )(implicit trace: Trace): UIO[(Span, UIO[Any])] =
      ZIO.succeed((Span.getInvalid(), ZIO.unit))

    def getCurrentContextUnsafe(implicit trace: Trace): UIO[Context] =
      ZIO.succeed(Context.current())

    def getCurrentSpanUnsafe(implicit trace: Trace): UIO[Span] =
      ZIO.succeed(Span.current())

    def inSpan[R, E, E1 <: E, A, A1 <: A](
        span: Span,
        spanName: String,
        spanKind: SpanKind,
        attributes: Attributes,
        statusMapper: StatusMapper[E, A],
        links: Seq[SpanContext]
    )(zio: => ZIO[R, E1, A1])(implicit trace: Trace): ZIO[R, E1, A1] =
      zio

    def getCurrentSpanContextUnsafe(implicit trace: Trace): UIO[SpanContext] = ZIO.succeed(Span.current().getSpanContext())

    def root[R, E, E1 <: E, A, A1 <: A](
        spanName: String,
        spanKind: SpanKind,
        attributes: Attributes,
        statusMapper: StatusMapper[E, A],
        links: Seq[SpanContext]
    )(zio: => ZIO[R, E1, A1])(implicit trace: Trace): ZIO[R, E1, A1] =
      zio

    def span[R, E, E1 <: E, A, A1 <: A](
        spanName: String,
        spanKind: SpanKind,
        attributes: Attributes,
        statusMapper: StatusMapper[E, A],
        links: Seq[SpanContext]
    )(zio: => ZIO[R, E1, A1])(implicit trace: Trace): ZIO[R, E1, A1] =
      zio

    def scopedEffect[A](effect: => A)(implicit trace: Trace): Task[A] = ZIO.attempt(effect)

    def injectSpan[C](propagator: TraceContextPropagator, carrier: OutgoingContextCarrier[C])(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def scopedEffectFromFuture[A](make: ExecutionContext => Future[A])(implicit trace: Trace): Task[A] = ZIO.fromFuture(make)

    def scopedEffectTotal[A](effect: => A)(implicit trace: Trace): UIO[A] = ZIO.succeed(effect)

    def spanScoped(
        spanName: String,
        spanKind: SpanKind,
        attributes: Attributes,
        statusMapper: StatusMapper[Any, Unit],
        links: Seq[SpanContext]
    )(implicit trace: Trace): ZIO[Scope, Nothing, Unit] = ZIO.unit

    def spanUnsafe(spanName: String, spanKind: SpanKind, attributes: Attributes, links: Seq[SpanContext])(implicit
        trace: Trace
    ): UIO[(Span, UIO[Any])] =
      ZIO.succeed((Span.getInvalid(), ZIO.unit))

    def setAttribute[T](attribute: Attribute[T])(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def setAttribute(name: String, value: String)(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def setAttribute(name: String, value: Boolean)(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def setAttribute(name: String, value: Long)(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def setAttribute(name: String, value: Double)(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def setAttribute(name: String, values: Seq[String])(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def setAttribute[T](key: AttributeKey[T], value: T)(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def setAttribute(name: String, values: Seq[Boolean])(implicit i1: DummyImplicit, trace: Trace): UIO[Unit] = ZIO.unit

    def setAttribute(name: String, values: Seq[Long])(implicit i1: DummyImplicit, i2: DummyImplicit, trace: Trace): UIO[Unit] = ZIO.unit

    def setAttribute(name: String, values: Seq[Double])(implicit
        i1: DummyImplicit,
        i2: DummyImplicit,
        i3: DummyImplicit,
        trace: Trace
    ): UIO[Unit] = ZIO.unit

  }

}
