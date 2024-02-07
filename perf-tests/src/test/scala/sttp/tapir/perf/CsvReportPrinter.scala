package sttp.tapir.perf

object CsvReportPrinter {
  def print(results: List[GatlingSimulationResult], initialSimOrdering: List[String]): String = {

    val groupedResults = results.groupBy(_.simulationName)
    val orderedResults = initialSimOrdering.flatMap(simName => groupedResults.get(simName).map(simName -> _))
    val headers = "Simulation" :: orderedResults.head._2.flatMap(r => {
      val server = r.serverName
      List(
        s"$server reqs/s",
        s"$server latency-p99",
        s"$server latency-p95",
        s"$server latency-p75",
        s"$server latency-p50"
      )
    })
    val rows: List[String] = orderedResults.map { case (simName, serverResults) =>
      (simName :: serverResults.flatMap(singleResult =>
        List(
          singleResult.meanReqsPerSec.toString,
          singleResult.latencyP99.toString,
          singleResult.latencyP95.toString,
          singleResult.latencyP75.toString,
          singleResult.latencyP75.toString
        )
      )).mkString(",")

    }
    (headers.mkString(",") :: rows).mkString("\n")
  }
}
