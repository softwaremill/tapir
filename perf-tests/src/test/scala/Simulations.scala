package perfTests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

object Common {
  val scn = scenario("get plaintext")
    .during(5*60) {
      exec(http("first plaintext")
             .get("/4"))
    }
  val userCount = 100
  val baseUrl = "http://127.0.0.1:8080"

  def genericInjection(path: String) = {
    val httpProtocol = http.baseUrl(baseUrl + path)
    scn.inject(atOnceUsers(userCount)).protocols(httpProtocol)
  }
}

class AkkaHttpVanillaSimulation extends Simulation {
  setUp(Common.genericInjection("/akka-http-vanilla"))
}

class AkkaHttpTapirSimulation extends Simulation {
  setUp(Common.genericInjection("/akka-http-tapir"))
}

class AkkaHttpMultiSimulation extends Simulation {
  setUp(Common.genericInjection("/path127"))
}
