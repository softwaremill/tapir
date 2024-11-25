# OpenTelemetry Tracing for Synchronous Applications in Tapir

This module provides server-side integration between Tapir and OpenTelemetry for tracing synchronous HTTP requests, optimized for applications using Java's Project Loom virtual threads.

## Dependencies

To use this module, add the following dependency to your `build.sbt` file:

```
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-opentelemetry-tracing-sync" % "@VERSION@"
```

Replace `@VERSION@` with the latest version of Tapir.

## Overview

This module integrates OpenTelemetry tracing with Tapir, providing:

- Synchronous request processing
- Virtual threads compatibility (Project Loom)
- Context propagation
- Baggage handling
- Custom span naming
- Header attributes mapping

## Usage

### Basic Configuration

```
import sttp.tapir.server.opentelemetry.*
import io.opentelemetry.api.trace.Tracer

// Obtain your OpenTelemetry tracer instance
val tracer: Tracer = // ... your OpenTelemetry tracer configuration

// Create the OpenTelemetry tracing instance
val tracing = new OpenTelemetryTracingSync(tracer)

// Integrate with your server options
val serverOptions = NettyFutureServerOptions.customiseInterceptors
  .tracingInterceptor(tracing.interceptor())
  .options

// Create the server interpreter
val server = NettyFutureServerInterpreter(serverOptions)
```

### Custom Configuration

```
val config = OpenTelemetryConfig(
  includeHeaders = Set("x-request-id", "user-agent"), // Headers to include as span attributes
  includeBaggage = true,                              // Enable baggage propagation
  errorPredicate = statusCode => statusCode >= 500,   // Define which HTTP status codes are considered errors
  spanNaming = SpanNaming.Path                        // Choose a span naming strategy
)

val customTracing = new OpenTelemetryTracingSync(tracer, config)
```

### Span Naming Strategies

You can choose different strategies for naming your spans:

**Default**: Combines the HTTP method and path (e.g., `"GET /users"`).

```
val spanNaming = SpanNaming.Default
```

**Path Only**: Uses only the request path (e.g., `"/users"`).

```
val spanNaming = SpanNaming.Path
```

**Custom Naming**: Define your own naming strategy using a function.

```
val spanNaming = SpanNaming.Custom { endpoint =>
  s"${endpoint.method.method} - ${endpoint.showShort}"
}
```

## Configuration Options

### OpenTelemetryConfig

| Option           | Type                  | Default                 | Description                                   |
| ---------------- | --------------------- | ----------------------- | --------------------------------------------- |
| `includeHeaders` | `Set[String]`         | `Set.empty`             | HTTP headers to include as span attributes    |
| `includeBaggage` | `Boolean`             | `true`                  | Enable or disable baggage propagation         |
| `errorPredicate` | `Int => Boolean`      | `_ >= 500`              | Determines which HTTP status codes are errors |
| `spanNaming`     | `SpanNaming`          | `Default`               | Strategy for naming spans                     |
| `virtualThreads` | `VirtualThreadConfig` | `VirtualThreadConfig()` | Configuration for virtual threads             |

### VirtualThreadConfig

| Option                    | Type      | Default       | Description                             |
| ------------------------- | --------- | ------------- | --------------------------------------- |
| `useVirtualThreads`       | `Boolean` | `true`        | Enable or disable virtual threads usage |
| `virtualThreadNamePrefix` | `String`  | `"tapir-ot-"` | Prefix for virtual thread names         |

## Virtual Threads Compatibility

This module is optimized for use with Project Loom's virtual threads:

- **Scoped Values**: Utilizes `ScopedValue` instead of `ThreadLocal` for context storage.
- **Proper Context Propagation**: Ensures tracing context is maintained across thread boundaries.
- **High Concurrency**: Efficiently handles a large number of concurrent requests.

## Examples

### Basic Server Setup

```
import sttp.tapir.*
import sttp.tapir.server.opentelemetry.*
import sttp.tapir.server.netty.*
import io.opentelemetry.api.trace.Tracer
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

def setupServer(tracer: Tracer) = {
  val tracing = new OpenTelemetryTracingSync(tracer)
  
  val serverOptions = NettyFutureServerOptions.customiseInterceptors
    .tracingInterceptor(tracing.interceptor())
    .options
  
  val server = NettyFutureServerInterpreter(serverOptions)
  
  val helloEndpoint = endpoint.get
    .in("hello")
    .out(stringBody)
    .serverLogicSuccess(_ => Future.successful("Hello, World!"))
  
  server.toRoute(helloEndpoint)
}
```

### Custom Span Attributes

```
val config = OpenTelemetryConfig(
  includeHeaders = Set("x-request-id"),
  spanNaming = SpanNaming.Custom { endpoint =>
    s"${endpoint.method.method} - ${endpoint.showShort}"
  }
)

val tracing = new OpenTelemetryTracingSync(tracer, config)
```

## Integration with Other Tapir Components

The OpenTelemetry Sync Tracing module can be used alongside other Tapir components:

- **Server Interpreters**: Compatible with various server backends like Netty, Http4s, and Akka HTTP.
- **Monitoring Solutions**: Can be integrated with additional monitoring tools.
- **Security Interceptors**: Works with Tapir's security features.
- **Documentation Generators**: Complements OpenAPI and AsyncAPI documentation.

## Error Handling

By default, the module handles errors in the following way:

- **Error Span Marking**: Marks spans as errors for HTTP status codes matching the `errorPredicate` (default is `>= 500`).
- **Exception Recording**: Records exceptions as events within the span.
- **Error Attributes**: Adds error-related attributes following OpenTelemetry's semantic conventions.

## Testing

When testing your application, you might want to verify that tracing is working as expected. Here are some tips:

- **Use In-Memory Exporters**: Configure OpenTelemetry to use an in-memory exporter to collect spans during tests.
- **Assert on Spans**: After executing test requests, assert that the correct spans were created with the expected attributes.
- **Mocking**: If necessary, mock the `Tracer` or other OpenTelemetry components to control the behavior in tests.

Example of setting up an in-memory exporter:

```
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.api.trace.Tracer

val spanExporter = InMemorySpanExporter.create()
val tracerProvider = SdkTracerProvider.builder()
  .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
  .build()
val tracer: Tracer = tracerProvider.get("test-tracer")

// Use the tracer in your application setup

// After running your test
val spans = spanExporter.getFinishedSpanItems()
// Perform assertions on spans
```

## Limitations

- **Virtual Threads Requirement**: To take full advantage of virtual threads, your application needs to run on Java 19 or later with Project Loom enabled.
- **Compatibility**: Ensure that other libraries used in your application are compatible with virtual threads to avoid unexpected behavior.
- **Context Propagation**: While the module handles context propagation across virtual threads, manual intervention might be required in complex threading scenarios.

## Performance Considerations

- **Low Overhead**: Designed to have minimal impact on synchronous operations.
- **Efficient Context Propagation**: Optimized for passing context without performance penalties.
- **Non-Blocking**: Avoids blocking operations in the request processing path.
- **Thread-Safety**: Safe to use in highly concurrent environments.

## Debugging

Spans include standard HTTP attributes for easier debugging:

- `http.method`
- `http.url`
- `http.status_code`
- **Custom Headers**: If configured, additional headers are included.
- **Error Information**: Details about errors and exceptions.

To view and analyze spans:

1. Use an OpenTelemetry-compatible tracing backend (e.g., Jaeger, Zipkin).
2. Configure the OpenTelemetry SDK to export spans to your tracing backend.
3. Use the backend's UI to visualize and inspect the spans and their attributes.
