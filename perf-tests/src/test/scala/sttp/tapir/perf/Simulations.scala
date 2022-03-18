package sttp.tapir.perf

import io.gatling.core.Predef._
import io.gatling.core.structure.{PopulationBuilder, ScenarioBuilder}
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

object CommonSimulations {
  private val duration = 1.minute
  private val userCount = 100
  private val baseUrl = "http://127.0.0.1:8080"

  val scn: ScenarioBuilder = scenario("get plaintext")
    .during(duration.toSeconds.toInt) {
      exec(
        http("first plaintext")
          .get("/4")
      )
    }

  def genericInjection(n: Int): PopulationBuilder = {
    val httpProtocol = http.baseUrl(baseUrl + "/path" + n.toString)
    scn.inject(atOnceUsers(userCount)).protocols(httpProtocol)
  }
}

class OneRouteSimulation extends Simulation {
  setUp(CommonSimulations.genericInjection(0))
}

class MultiRouteSimulation extends Simulation {
  setUp(CommonSimulations.genericInjection(127))
}
