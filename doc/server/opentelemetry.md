# OpenTelemetry Tracing for Tapir with Netty Sync Server

This documentation describes the integration between Tapir and OpenTelemetry for tracing synchronous HTTP requests, optimized for applications using Java's virtual threads (Project Loom).

## Dependencies

Add the following dependencies to your `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % "1.11.9",
  "com.softwaremill.sttp.tapir" %% "tapir-opentelemetry-tracing-sync" % "1.11.9",
  "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.36.0",
  "io.opentelemetry" % "opentelemetry-sdk" % "1.36.0"
)
```

## Overview

This integration provides:
- Synchronous request processing with Netty
- Virtual threads compatibility (Project Loom)
- OpenTelemetry context propagation
- OpenTelemetry baggage handling
- Custom span naming
- HTTP header attributes mapping
- High-performance request handling

## Additional Netty Server Features
- Graceful shutdown support
- Domain socket support
- WebSocket support through Ox
- Logging through SLF4J (enabled by default)
- Virtual threads optimization
- High-performance request handling

## Basic Usage

### OpenTelemetry Configuration

```scala
import sttp.tapir.*
import sttp.tapir.server.netty.NettySyncServer
import sttp.tapir.server.opentelemetry.*
import io.opentelemetry.api.trace.Tracer

// Obtain your OpenTelemetry tracer instance
val tracer: Tracer = // ... your OpenTelemetry configuration

// Create the OpenTelemetry tracing instance
val tracing = new OpenTelemetryTracingSync(tracer)

// Integrate with server options
val serverOptions = NettySyncServerOptions.customiseInterceptors
  .tracingInterceptor(tracing.interceptor())
  .options

// Create the server with additional Netty configuration
val server = NettySyncServer(serverOptions)
  .port(8080)
  .host("localhost")
```

### Custom OpenTelemetry Configuration

```scala
val config = OpenTelemetryConfig(
  includeHeaders = Set("x-request-id", "user-agent"), // Headers to include as attributes
  includeBaggage = true,                              // Enable baggage propagation
  errorPredicate = statusCode => statusCode >= 500,   // Define which HTTP status codes are errors
  spanNaming = SpanNaming.Path                        // Choose a span naming strategy
)

val customTracing = new OpenTelemetryTracingSync(tracer, config)
```

### Span Naming Strategies

Several strategies are available:

**Default**: Combines HTTP method and path
```scala
val spanNaming = SpanNaming.Default // Example: "GET /users"
```

**Path Only**: Uses only the path
```scala
val spanNaming = SpanNaming.Path // Example: "/users"
```

**Custom**: Define your own strategy
```scala
val spanNaming = SpanNaming.Custom { endpoint =>
  s"${endpoint.method.method} - ${endpoint.showPathTemplate()}"
}
```

## Complete Examples

### Basic Server with Tracing

```scala
import sttp.tapir.*
import sttp.tapir.server.netty.NettySyncServer
import sttp.tapir.server.opentelemetry.*
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import scala.util.Using

object TracedNettySyncServer:
  val healthEndpoint = endpoint.get
    .in("health")
    .out(stringBody)
    .handle { _ => 
      Right("OK") 
    }

  def setupTracing(): Tracer =
    val spanExporter = OtlpGrpcSpanExporter.builder()
      .setEndpoint("http://localhost:4317")
      .build()

    val tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
      .build()

    val openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .build()

    openTelemetry.getTracer("com.example.tapir-server")

  def main(args: Array[String]): Unit =
    val tracer = setupTracing()
    val tracing = new OpenTelemetryTracingSync(tracer)

    val serverOptions = NettySyncServerOptions
      .customiseInterceptors
      .tracingInterceptor(tracing.interceptor())
      .options

    val server = NettySyncServer(serverOptions)
      .port(8080)
      .addEndpoint(healthEndpoint)

    Using.resource(server.start()) { binding =>
      println("Server running on http://localhost:8080")
      Thread.sleep(Long.MaxValue)
    }
```

### WebSocket Server

```scala
import ox.*

val wsEndpoint = endpoint.get
  .in("ws")
  .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain])

def wsLogic(using Ox): Source[String] => Source[String] = input => 
  input.map(_.toUpperCase)

val wsServerEndpoint = wsEndpoint.handle(wsLogic)

// Add to server
val server = NettySyncServer(serverOptions)
  .addEndpoint(wsServerEndpoint)
```

### Domain Socket Server

```scala
import java.nio.file.Paths
import io.netty.channel.unix.DomainSocketAddress

val binding = NettySyncServer()
  .addEndpoint(endpoint)
  .startUsingDomainSocket(Paths.get("/tmp/server.sock"))
```

## Configuration Options

### OpenTelemetryConfig

| Option           | Type                  | Default                 | Description                                   |
| ---------------- | --------------------- | ----------------------- | --------------------------------------------- |
| `includeHeaders` | `Set[String]`         | `Set.empty`             | HTTP headers to include as attributes         |
| `includeBaggage` | `Boolean`             | `true`                  | Enable/disable baggage propagation            |
| `errorPredicate` | `Int => Boolean`      | `_ >= 500`              | Determines which HTTP status codes are errors |
| `spanNaming`     | `SpanNaming`          | `Default`               | Strategy for naming spans                     |
| `virtualThreads` | `VirtualThreadConfig` | `VirtualThreadConfig()` | Configuration for virtual threads             |

### VirtualThreadConfig

| Option                    | Type      | Default       | Description                          |
| ------------------------- | --------- | ------------- | ------------------------------------ |
| `useVirtualThreads`       | `Boolean` | `true`        | Enable/disable virtual threads usage |
| `virtualThreadNamePrefix` | `String`  | `"tapir-ot-"` | Prefix for virtual thread names      |

### Netty Server Configuration

```scala
import scala.concurrent.duration.*

// Basic configuration
val server = NettySyncServer()
  .port(8080)
  .host("localhost")
  .withGracefulShutdownTimeout(5.seconds)

// Advanced Netty configuration
val nettyConfig = NettyConfig.default
  .socketBacklog(256)
  .withGracefulShutdownTimeout(5.seconds)
  // Or disable graceful shutdown
  //.noGracefulShutdown

val serverWithConfig = NettySyncServer(nettyConfig)
```

## Testing

For testing your application with tracing:

```scala
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

val spanExporter = InMemorySpanExporter.create()
val tracerProvider = SdkTracerProvider.builder()
  .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
  .build()
val tracer = tracerProvider.get("test-tracer")

// After running your test
val spans = spanExporter.getFinishedSpanItems()
// Perform assertions on spans
```

## Virtual Threads Compatibility

This module is optimized for use with Project Loom's virtual threads:
- Uses `ScopedValue` instead of `ThreadLocal` for context storage
- Ensures tracing context is maintained across thread boundaries
- Efficiently handles a large number of concurrent requests
- Proper context propagation across virtual threads

## Performance and Optimization

- Minimal overhead for synchronous operations
- Efficient context propagation
- Non-blocking operations in the request processing path
- Thread-safe for highly concurrent environments
- Optimized for virtual threads via Netty Sync
- Configurable socket backlog and other Netty parameters
- Graceful shutdown support for clean request handling

## Debugging

Spans include standard HTTP attributes:
- `http.method`
- `http.url`
- `http.status_code`
- Custom headers (if configured)
- Error information

To view spans:
1. Use an OpenTelemetry-compatible tracing backend (Jaeger, Zipkin)
2. Configure the OpenTelemetry SDK to export spans
3. Use the backend's UI to visualize and inspect spans

### Logging
By default, logging of handled requests and exceptions is enabled using SLF4J. You can customize it:

```scala
val serverOptions = NettySyncServerOptions.customiseInterceptors
  .serverLog(None) // Disable logging
  .options
```

## Best Practices

1. **Span Naming**
   - Use descriptive and consistent names
   - Include HTTP method and path
   - Avoid overly generic names
   - Consider using custom naming for specific use cases

2. **Attributes**
   - Limit traced headers to relevant ones
   - Add meaningful business attributes
   - Follow OpenTelemetry semantic conventions
   - Consider performance impact of attribute collection

3. **Error Handling**
   - Configure error predicate appropriately
   - Add relevant details to error spans
   - Use span events for exceptions
   - Consider error handling in WebSocket scenarios

4. **Performance**
   - Monitor tracing impact
   - Use sampling if needed
   - Optimize configuration for your use case
   - Consider using domain sockets for local communication
   - Configure appropriate shutdown timeouts
   - Tune Netty parameters for your load

## Integration with Other Tapir Components

The OpenTelemetry Sync module works seamlessly with:
- Security interceptors
- Documentation generators
- Other monitoring solutions
- Server endpoints and routing
- WebSocket endpoints
- Domain socket endpoints

## Limitations

- Requires Java 19+ for virtual threads
- Ensure other libraries are virtual thread compatible
- Context propagation may need manual handling in complex threading scenarios
- Sampling might be required for high-throughput applications
- WebSocket support requires understanding of Ox concurrency model
- Domain socket support limited to Unix-like systems
