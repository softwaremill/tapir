package sttp.tapir.perf

import io.gatling.core.Predef._
import io.gatling.core.structure.PopulationBuilder
import io.gatling.http.Predef._

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object CommonSimulations {
  private val userCount = 100
  private val baseUrl = "http://127.0.0.1:8080"

  def testScenario(duration: FiniteDuration, routeNumber: Int): PopulationBuilder = {
    val httpProtocol = http.baseUrl(baseUrl)
    val execHttpGet = exec(http(s"HTTP GET /path$routeNumber/4").get(s"/path$routeNumber/4"))

    scenario(s"Repeatedly invoke GET of route number $routeNumber")
      .during(duration.toSeconds.toInt)(execHttpGet)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }
}

class OneRouteSimulation extends Simulation {
  setUp(CommonSimulations.testScenario(1.minute, 0))
}

class MultiRouteSimulation extends Simulation {
  setUp(CommonSimulations.testScenario(1.minute, 127))
}
