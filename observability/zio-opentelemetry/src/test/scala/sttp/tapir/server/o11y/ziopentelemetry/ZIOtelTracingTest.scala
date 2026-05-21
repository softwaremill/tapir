package sttp.tapir.server.o11y.ziopentelemetry

import scala.util.{Success, Try}

import sttp.capabilities.Streams
import sttp.model.Uri._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.TestUtil.serverRequestFromUri
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter._
import sttp.tapir.server.o11y.ziopentelemetry.ZIOpenTelemetryTracing
import sttp.tapir.server.TestUtil.StringToResponseBody

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.SdkTracerProvider

import zio._
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test._
import zio.test.Assertion._

import sttp.tapir.ztapir.RIOMonadError
import zio.telemetry.opentelemetry.OpenTelemetry

object ZIOtelTracingTest extends ZIOSpecDefault {

  implicit val bodyListener: BodyListener[Task, String] = new BodyListener[Task, String] {
    override def onComplete(body: String)(cb: Try[Unit] => Task[Unit]): Task[String] = cb(Success(())).map(_ => body)
  }

  implicit val ioErr: MonadError[Task] = new RIOMonadError

  val inMemoryTracer: UIO[(InMemorySpanExporter, Tracer)] = for {
    spanExporter <- ZIO.succeed(InMemorySpanExporter.create())
    spanProcessor <- ZIO.succeed(SimpleSpanProcessor.create(spanExporter))
    tracerProvider <- ZIO.succeed(SdkTracerProvider.builder().addSpanProcessor(spanProcessor).build())
    tracer = tracerProvider.get("TracingTest")
  } yield (spanExporter, tracer)

  val inMemoryTracerLayer: ULayer[InMemorySpanExporter with Tracer] =
    ZLayer.fromZIOEnvironment(inMemoryTracer.map { case (inMemorySpanExporter, tracer) =>
      ZEnvironment(inMemorySpanExporter).add(tracer)
    })

  def tracingMockLayer(
      logAnnotated: Boolean = false
  ): URLayer[ContextStorage, Tracing with InMemorySpanExporter with Tracer] =
    inMemoryTracerLayer >>> (Tracing.live(logAnnotated) ++ inMemoryTracerLayer)

  def spec: Spec[Any, Throwable] =
    suite("zio opentelemetry tapir interceptor")(test("report a simple trace") {
      for {
        _ <- ZIO.logDebug("Setting up in-memory tracer and tracing layer")
        tracing <- ZIO.service[Tracing]
        endpointa = endpoint
          .in("person")
          .in(query[String]("name"))
          .out(stringBody)
          .errorOut(stringBody)
          .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

        request = serverRequestFromUri(uri"http://example.com/person?name=Adam")
        interpreter = new ServerInterpreter[Any, Task, String, NoStreams](
          _ => List(endpointa),
          ZIOTestRequestBody,
          StringToResponseBody,
          List(ZIOpenTelemetryTracing(tracing)),
          _ => ZIO.succeed(()) @@ tracing.aspects.span("interpreter")
        )
        _ <- interpreter(request)

        exported <- ZIO.service[InMemorySpanExporter]

        fishedSpans = exported.getFinishedSpanItems()

        span = fishedSpans.get(0) // getFirst not supported in Java 11

        _ <- ZIO.debug(s"Span: $span")

      } yield {
        assert(fishedSpans.size())(equalTo(1)) &&
        assert(span.getName())(equalTo("GET /person")) &&
        assert(span.getAttributes.size())(equalTo(6))
      }

    }).provide(
      OpenTelemetry.contextZIO,
      tracingMockLayer(false)
    )
}

object ZIOTestRequestBody extends RequestBody[Task, NoStreams] {
  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): Task[RawValue[R]] = ???
  override val streams: Streams[NoStreams] = NoStreams
  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = ???
}
