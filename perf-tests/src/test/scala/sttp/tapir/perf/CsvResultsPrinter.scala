package sttp.tapir.perf

object CsvResultsPrinter {
  def print(results: List[GatlingSimulationResult]): String = {

    val groupedResults = results.groupBy(_.simulationName)
    val headers = "Simulation" :: groupedResults.values.head.flatMap(r => {
      val server = r.serverName
      List(
        s"$server-ops/sec",
        s"$server-latency-p99",
        s"$server-latency-p95",
        s"$server-latency-p75",
        s"$server-latency-p50"
      )
    })
    val rows: List[String] = groupedResults.toList.map { case (simName, serverResults) =>
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
