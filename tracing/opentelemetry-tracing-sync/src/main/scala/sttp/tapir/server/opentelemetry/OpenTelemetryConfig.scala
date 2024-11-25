package sttp.tapir.server.opentelemetry
case class OpenTelemetryConfig(
    // Headers to include as span attributes
    includeHeaders: Set[String] = Set.empty,
    
    // Enable/disable baggage propagation
    includeBaggage: Boolean = true,
    
    // Predicate to determine if an HTTP code should be considered as an error
    errorPredicate: Int => Boolean = _ >= 500,
    
    // Span naming strategy
    spanNaming: SpanNaming = SpanNaming.Default,
    
    // Additional Loom-specific options
    virtualThreads: VirtualThreadConfig = VirtualThreadConfig()
)
case class VirtualThreadConfig(
    // Specific configuration for using virtual threads
    useVirtualThreads: Boolean = true,
    virtualThreadNamePrefix: String = "tapir-ot-"
)
