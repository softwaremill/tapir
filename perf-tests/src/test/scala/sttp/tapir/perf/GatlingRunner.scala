package sttp.tapir.perf

import io.gatling.app.Gatling
import io.gatling.shared.cli.GatlingCliOptions

object GatlingRunner {

  /** Blocking, runs the entire Gatling simulation.
    */
  def runSimulationBlocking(simulationClassName: String, params: PerfTestSuiteParams): Unit = {
    val reportsArr: Array[String] =
      if (params.buildGatlingReports) Array.empty
      else
        Array(
          s"--${GatlingCliOptions.NoReports.full}"
        )
    val args = Array(
      s"--${GatlingCliOptions.Simulation.full}",
      s"$simulationClassName",
      s"--${GatlingCliOptions.ResultsFolder.full}",
      s"target/gatling/results",
    ) ++ reportsArr
    println(">>>>>>>>>>>>>>>>>>")
    println(args.toList)
    println(">>>>>>>>>>>>>>>>>>")
    Gatling.main(args)
  }
}
