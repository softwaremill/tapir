package sttp.tapir.perf

import scalatags.Text.all._
import scalatags.Text

object HtmlResultsPrinter {
  val tableStyle = "border-collapse: collapse;"
  val cellStyle = "border: 1px solid black; padding: 5px;"

  def print(results: List[GatlingSimulationResult]): String = {

    val headers = "Simulation" :: results.groupBy(_.simulationName).head._2.map(_.serverName)
    createHtmlTable(headers, results.groupBy(_.simulationName).values.toList)
  }

  private def createHtmlTable(headers: Seq[String], rows: List[List[GatlingSimulationResult]]): String = {

    table(style := tableStyle)(
      thead(
        tr(headers.map(header => th(header, style := cellStyle)))
      ),
      tbody(
        for (row <- rows) yield {
          tr(td(row.head.simulationName) :: row.map(toColumn), style := cellStyle)
        }
      )
    ).render
  }

  private def toColumn(result: GatlingSimulationResult): Text.TypedTag[String] =
    td(
      Seq(
        p(s"reqs/sec = ${result.meanReqsPerSec}"),
        p(s"p99 latency = ${result.latencyP99}"),
        p(s"p95 latency = ${result.latencyP95}"),
        p(s"p75 latency = ${result.latencyP75}"),
        p(s"p50 latency = ${result.latencyP50}")
      ),
      style := cellStyle
    )
}
