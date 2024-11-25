package sttp.tapir.server.opentelemetry

/**
 * Configuration options for OpenTelemetry tracing
 *
 * @param includeHeaders Headers to include as span attributes
 * @param includeBaggage Whether to include OpenTelemetry baggage in spans
 * @param errorPredicate Custom predicate to determine if a response should be marked as error
 * @param spanNaming Strategy for naming spans
 */
case class OpenTelemetryConfig(
    includeHeaders: Set[String] = Set.empty,
    includeBaggage: Boolean = true,
    errorPredicate: Int => Boolean = _ >= 500,
    spanNaming: SpanNaming = SpanNaming.Default
)
