package sttp.tapir.perf

import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder
import scala.reflect.runtime.universe

object GatlingRunner {

  /** Blocking, runs the entire Gatling simulation.
    */
  def runSimulationBlocking(simulationClassName: String): Int = {
    System.setProperty("tapir.perf.user-count", "1") // TODO from config
    val props = new GatlingPropertiesBuilder()
      .simulationClass(simulationClassName)
      .build

    Gatling.fromMap(props)
  }
}
