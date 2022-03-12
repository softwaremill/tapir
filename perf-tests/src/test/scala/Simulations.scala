package perfTests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

object CommonSimulations {
  val scn = scenario("get plaintext")
    .during(5 * 60) {
      exec(
        http("first plaintext")
          .get("/4")
      )
    }
  val userCount = 100
  val baseUrl = "http://127.0.0.1:8080"

  def genericInjection(n: Int) = {
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
