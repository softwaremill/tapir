// {cat=Observability; effects=Future; server=Netty; json=circe}: Reporting OpenTelemetry metrics

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-opentelemetry-metrics:1.11.8
//> using dep io.opentelemetry:opentelemetry-exporter-otlp:1.43.0
//> using dep org.slf4j:slf4j-api:2.0.13

package sttp.tapir.examples.observability

import io.circe.generic.auto.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.`export`.PeriodicMetricReader
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.metrics.opentelemetry.OpenTelemetryMetrics
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerOptions}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.io.StdIn

/** This example uses a gRPC <a href="https://opentelemetry.io/docs/concepts/components/#exporters">exporter</a> to send metrics to a <a
  * href="https://opentelemetry.io/docs/collector/">collector</a>, which by default is expected to be running on `localhost:4317`.
  *
  * You can run a collector locally using Docker with the following command:
  * {{{
  *   docker run -p 4317:4317 otel/opentelemetry-collector:latest
  * }}}
  *
  * Please refer to the <a href="https://opentelemetry.io/docs/concepts/sdk-configuration/otlp-exporter-configuration/">exporter
  * configuration</a> if you need to use a different host/port.
  *
  * The example code requires the following dependencies:
  * {{{
  *   val openTelemetryVersion = <OpenTelemetry version>
  *
  *   libraryDependencies ++= Seq(
  *     "io.opentelemetry" % "opentelemetry-sdk" % openTelemetryVersion,
  *     "io.opentelemetry" % "opentelemetry-sdk-metrics" % openTelemetryVersion,
  *     "io.opentelemetry" % "opentelemetry-exporter-otlp" % openTelemetryVersion
  *    )
  * }}}
  *
  * Once this example app and the collector are running, and after you send some requests to the `/person` endpoint, you should start seeing
  * the metrics in the collector logs (look for `InstrumentationScope tapir 1.0.0`), e.g.:
  * {{{
  *   InstrumentationScope tapir 1.0.0
  *   Metric #0
  *   Descriptor:
  *        -> Name: request_active
  *        -> Description: Active HTTP requests
  *        -> Unit: 1
  *        -> DataType: Sum
  *        -> IsMonotonic: false
  *        -> AggregationTemporality: Cumulative
  *
  *   ...
  * }}}
  */
@main def openTelemetryMetricsExample(): Unit =
  val logger: Logger = LoggerFactory.getLogger(this.getClass().getName)

  case class Person(name: String)

  // Simple endpoint returning 200 or 400 response with string body
  val personEndpoint: ServerEndpoint[Any, Future] =
    endpoint.post
      .in("person")
      .in(jsonBody[Person])
      .out(stringBody)
      .errorOut(stringBody)
      .serverLogic { p =>
        Thread.sleep(3000)
        Future.successful(Either.cond(p.name == "Jacob", "Welcome", "Unauthorized"))
      }

  // An exporter that sends metrics to a collector over gRPC
  val grpcExporter = OtlpGrpcMetricExporter.builder().build()

  // A metric reader that exports using the gRPC exporter
  val metricReader: PeriodicMetricReader = PeriodicMetricReader.builder(grpcExporter).build()

  // A meter registry whose meters are read by the above reader
  val meterProvider: SdkMeterProvider = SdkMeterProvider.builder().registerMetricReader(metricReader).build()

  // An instance of OpenTelemetry using the above meter registry
  val otel: OpenTelemetry = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build()

  val openTelemetryMetrics = OpenTelemetryMetrics.default[Future](otel)

  val serverOptions: NettyFutureServerOptions =
    NettyFutureServerOptions.customiseInterceptors
      // Adds an interceptor which collects metrics by executing callbacks
      .metricsInterceptor(openTelemetryMetrics.metricsInterceptor())
      .options

  val program = for {
    binding <- NettyFutureServer().port(8080).addEndpoint(personEndpoint, serverOptions).start()
    _ <- Future {
      logger.info(s"""Server started. Try it with: curl -X POST localhost:8080/person -d '{"name": "Jacob"}'""")
      logger.info("Press ENTER key to exit.")
      StdIn.readLine()
    }
    stop <- binding.stop()
  } yield stop

  Await.result(program, Duration.Inf)
