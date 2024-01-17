package sttp.tapir.perf

import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder

object GatlingRunner {

  /** Blocking, runs the entire Gatling simulation.
    */
  def runSimulationBlocking(simulationClassName: String, params: PerfTestSuiteParams): Int = {
    val initialProps = new GatlingPropertiesBuilder()
      .simulationClass(simulationClassName)

    val props =
      if (params.buildGatlingReports)
        initialProps
      else initialProps.noReports()
    Gatling.fromMap(props.build)
  }
}
