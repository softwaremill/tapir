# OpenTelemetry Sync Tracing for Tapir

Module providing OpenTelemetry integration for Tapir, optimized for synchronous style and virtual threads (Project Loom).

## Installation

```sbt
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-opentelemetry-tracing-sync" % "version"
```

## Overview

This module implements OpenTelemetry tracing integration with specific support for:

- Synchronous request processing
- Virtual threads compatibility (Project Loom)
- Context propagation
- Baggage handling
- Custom span naming
- Header attributes mapping

## Usage

### Basic Configuration

```
scalaCopier le codeimport sttp.tapir.server.opentelemetry._
import io.opentelemetry.api.trace.Tracer

// Get your OpenTelemetry tracer instance
val tracer: Tracer = // ... your OTel configuration

// Basic setup
val tracing = new OpenTelemetryTracingSync(tracer)

// Server integration
val serverInterpreter = ServerInterpreter(tracing)
```

### Custom Configuration

```
scalaCopier le codeval config = OpenTelemetryConfig(
  includeHeaders = Set("x-request-id", "user-agent"),
  includeBaggage = true,
  errorPredicate = _ >= 500,
  spanNaming = SpanNaming.Path
)

val customTracing = new OpenTelemetryTracingSync(tracer, config)
```

### Span Naming Strategies

```
scalaCopier le code// Default: "METHOD /path"
spanNaming = SpanNaming.Default

// Path only: "/path"
spanNaming = SpanNaming.Path

// Custom naming
spanNaming = SpanNaming.Custom(endpoint => s"API-${endpoint.showShort}")
```

## Configuration Options

### OpenTelemetryConfig

| Option           | Type                  | Default                 | Description                                      |
| ---------------- | --------------------- | ----------------------- | ------------------------------------------------ |
| `includeHeaders` | `Set[String]`         | `Set.empty`             | HTTP headers to include as span attributes       |
| `includeBaggage` | `Boolean`             | `true`                  | Enable/disable OpenTelemetry baggage propagation |
| `errorPredicate` | `Int => Boolean`      | `_ >= 500`              | Predicate to determine error status codes        |
| `spanNaming`     | `SpanNaming`          | `Default`               | Strategy for naming spans                        |
| `virtualThreads` | `VirtualThreadConfig` | `VirtualThreadConfig()` | Virtual threads specific options                 |

### VirtualThreadConfig

| Option                    | Type      | Default       | Description                     |
| ------------------------- | --------- | ------------- | ------------------------------- |
| `useVirtualThreads`       | `Boolean` | `true`        | Enable virtual threads usage    |
| `virtualThreadNamePrefix` | `String`  | `"tapir-ot-"` | Prefix for virtual thread names |

## Virtual Threads Compatibility

This module is designed to work efficiently with Project Loom's virtual threads:

- Uses `ScopedValue` instead of `ThreadLocal`
- Proper context propagation across thread boundaries
- Optimized for high-concurrency scenarios

## Examples

### Basic Server Setup

```
scalaCopier le codeimport sttp.tapir.server.opentelemetry._
import io.opentelemetry.api.trace.Tracer

def setupServer(tracer: Tracer) = {
  val tracing = new OpenTelemetryTracingSync(tracer)
  
  val endpoint = endpoint.get
    .in("hello")
    .out(stringBody)
    .serverLogic(_ => Right("Hello, World!"))

  ServerInterpreter(tracing)
    .toRoute(endpoint)
}
```

### Custom Span Attributes

```
scalaCopier le codeval config = OpenTelemetryConfig(
  includeHeaders = Set("x-request-id"),
  spanNaming = SpanNaming.Custom { endpoint =>
    s"${endpoint.method.method}-${endpoint.showShort}"
  }
)

val tracing = new OpenTelemetryTracingSync(tracer, config)
```

## Integration with Other Tapir Components

The module can be used alongside other Tapir components:

- Server interpreters (e.g., Netty, Http4s)
- Other monitoring solutions
- Security interceptors
- Documentation generators

## Error Handling

By default, the module:

- Marks spans as errors for 5xx status codes
- Records exceptions as span events
- Adds error attributes according to OpenTelemetry semantic conventions

## Performance Considerations

- Minimal overhead for synchronous operations
- Efficient context propagation
- No blocking operations in the critical path
- Thread-safety guarantees for concurrent requests

## Debugging

Spans include standard HTTP attributes:

- `http.method`
- `http.url`
- `http.status_code`
- Custom headers (if configured)
- Error information