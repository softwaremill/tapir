package sttp.tapir.perf

import scalatags.Text.all._
import scalatags.Text

object HtmlReportPrinter {
  val tableStyle = "border-collapse: collapse; font-family: Roboto, Helvetica, Arial, sans-serif;"
  val cellStyle = "border: 1px solid black; padding: 5px;"
  val headStyle =
    "border: 1px solid black; padding: 5px; color: rgb(245, 245, 245); background-color: rgb(85, 73, 75)"
  val simCellStyle =
    "border: 1px solid black; padding: 5px; color: black; background-color: rgb(243, 112, 94); font-weight: bold"

  def print(results: List[GatlingSimulationResult]): String = {

    val headers = "Server" :: results.groupBy(_.serverName).head._2.map(_.simulationName)
    createHtmlTable(headers, results.groupBy(_.serverName).values.toList.sortBy(_.head.serverName))
  }

  private def createHtmlTable(headers: Seq[String], rows: List[List[GatlingSimulationResult]]): String = {

    table(style := tableStyle)(
      thead(
        tr(headers.map(header => th(header, style := headStyle)))
      ),
      tbody(
        for (row <- rows) yield {
          tr(td(row.head.serverName, style := simCellStyle) :: row.map(toColumn), style := cellStyle)
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
