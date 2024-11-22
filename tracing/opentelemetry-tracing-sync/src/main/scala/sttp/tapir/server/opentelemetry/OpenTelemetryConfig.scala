package sttp.tapir.server.opentelemetry

case class OpenTelemetryConfig(
    // Headers à inclure comme attributs de span
    includeHeaders: Set[String] = Set.empty,
    
    // Activer/désactiver la propagation du baggage
    includeBaggage: Boolean = true,
    
    // Prédicat pour déterminer si un code HTTP doit être considéré comme une erreur
    errorPredicate: Int => Boolean = _ >= 500,
    
    // Stratégie de nommage des spans
    spanNaming: SpanNaming = SpanNaming.Default,
    
    // Options supplémentaires spécifiques à Loom
    virtualThreads: VirtualThreadConfig = VirtualThreadConfig()
)

case class VirtualThreadConfig(
    // Configuration spécifique pour l'utilisation des threads virtuels
    useVirtualThreads: Boolean = true,
    virtualThreadNamePrefix: String = "tapir-ot-"
)