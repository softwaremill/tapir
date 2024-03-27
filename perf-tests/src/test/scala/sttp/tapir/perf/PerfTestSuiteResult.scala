package sttp.tapir.perf

import scala.concurrent.duration.FiniteDuration

case class GatlingSimulationResult(
    simulationName: String,
    serverName: String,
    duration: FiniteDuration,
    meanReqsPerSec: Long,
    latencyP99: Double,
    latencyP95: Double,
    latencyP75: Double,
    latencyP50: Double
)
